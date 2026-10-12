package com.xscheck

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A dedicated, isolated `xs-check-lsp` process that answers completion / hover for
 * **injected** XS fragments.
 *
 * Real `.xs` files are served by the main LSP4IJ-managed server; this one is deliberately
 * separate, so a race or crash here can never affect real-file analysis. An injected
 * fragment is also analysed in isolation, so results cover the built-in prelude and symbols
 * defined within the fragment — not project-defined names.
 */
@Service(Service.Level.PROJECT)
class XsInjectionServer(private val project: Project) : Disposable {

    private val uri = "file:///xs-injection/${System.identityHashCode(this)}.xs"
    private var process: Process? = null
    private var server: LanguageServer? = null
    private var version = 0
    /** Last text pushed to the running server; lets [sync] skip a re-lint when nothing changed. */
    private var lastSyncedText: String? = null

    @Synchronized
    private fun ensureStarted(): LanguageServer? {
        server?.let { if (process?.isAlive == true) return it }
        // Clean up a dead instance before restarting.
        process?.destroy()
        server = null
        lastSyncedText = null
        try {
            val proc = ProcessBuilder(XsServerFactory.binary.absolutePath)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val launcher = LSPLauncher.createClientLauncher(
                InjectionClient(), proc.inputStream, ForgivingOutputStream(proc.outputStream)
            )
            val srv = launcher.remoteProxy
            launcher.startListening()

            val init = InitializeParams().apply {
                capabilities = ClientCapabilities().apply {
                    workspace = WorkspaceClientCapabilities().apply { configuration = true }
                }
            }
            // Cancelable: this can run inside a completion/hover read action, so it must yield
            // promptly to a pending write action (typing) instead of blocking the EDT for 10s.
            awaitCancelable(srv.initialize(init), INIT_TIMEOUT_MS)
                ?: error("XS injection server did not initialize within ${INIT_TIMEOUT_MS}ms")
            srv.initialized(InitializedParams())

            // Open the document once with empty text so the server's caches are populated
            // before the first completion request (its handlers assume a cached document).
            version = 1
            srv.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xs", version, ""))
            )
            lastSyncedText = ""

            process = proc
            server = srv
            return srv
        } catch (t: Throwable) {
            thisLogger().warn("XS injection server failed to start", t)
            process?.destroy()
            process = null
            server = null
            lastSyncedText = null
            return null
        }
    }

    /**
     * Pushes [text] to the running server, but only when it differs from what we last sent. Every
     * didChange triggers a full re-lint there — which deep-clones the ~1400-symbol built-in prelude
     * env and re-type-checks the fragment — so re-sending unchanged text (e.g. on hover, which never
     * edits the fragment) would force a redundant re-analysis for an identical result. Skipping it
     * lets hover and repeated lookups read straight from the server's already-built cache.
     */
    @Synchronized
    private fun sync(srv: LanguageServer, text: String) {
        if (text == lastSyncedText) return
        val id = VersionedTextDocumentIdentifier(uri, ++version)
        srv.textDocumentService.didChange(
            DidChangeTextDocumentParams(id, listOf(TextDocumentContentChangeEvent(text)))
        )
        lastSyncedText = text
    }

    fun completion(text: String, position: Position): List<CompletionItem> {
        val srv = ensureStarted() ?: return emptyList()
        return try {
            sync(srv, text)
            val result = awaitCancelable(
                srv.textDocumentService.completion(CompletionParams(TextDocumentIdentifier(uri), position)),
                REQUEST_TIMEOUT_MS,
            )
            when {
                result == null -> emptyList()
                result.isLeft -> result.left
                else -> result.right.items
            }
        } catch (pce: ProcessCanceledException) {
            throw pce
        } catch (t: Throwable) {
            thisLogger().debug("XS injection completion failed", t)
            emptyList()
        }
    }

    fun hover(text: String, position: Position): Hover? {
        val srv = ensureStarted() ?: return null
        return try {
            sync(srv, text)
            awaitCancelable(
                srv.textDocumentService.hover(HoverParams(TextDocumentIdentifier(uri), position)),
                REQUEST_TIMEOUT_MS,
            )
        } catch (pce: ProcessCanceledException) {
            throw pce
        } catch (t: Throwable) {
            thisLogger().debug("XS injection hover failed", t)
            null
        }
    }

    /**
     * Waits for [future] up to [timeoutMs], but polls in small slices and calls
     * [ProgressManager.checkCanceled] between them. Completion/hover run inside a read action; a
     * plain blocking `get()` there is non-cancellable and stalls a pending write action (typing),
     * freezing the EDT. Polling lets a pending write cancel us promptly (via [ProcessCanceledException]).
     *
     * Returns `null` on timeout; propagates [ProcessCanceledException] on cancellation.
     */
    private fun <T> awaitCancelable(future: CompletableFuture<T>, timeoutMs: Long): T? {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            ProgressManager.checkCanceled()
            val remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) {
                future.cancel(true)
                return null
            }
            try {
                return future.get(minOf(remainingMs, POLL_INTERVAL_MS), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // Re-check cancellation and keep polling until the deadline.
            }
        }
    }

    override fun dispose() {
        try {
            server?.shutdown()?.get(1, TimeUnit.SECONDS)
            server?.exit()
        } catch (_: Throwable) {
        } finally {
            process?.destroy()
        }
    }

    /**
     * Wraps the isolated server's stdin so a write that fails because the process has already died is
     * dropped instead of thrown. Server→client requests (e.g. `workspace/configuration`) are answered
     * on LSP4J's reader thread, which then writes the response back through here; if the process exited
     * in the meantime — a crash, or `destroy()` from [dispose] / the [ensureStarted] restart path — the
     * pipe is closed and the write raises `IOException: The pipe is being closed`. LSP4J has no handler
     * for a failed response write, so it surfaces as a spurious "Internal error". A response to a dead
     * helper is worthless anyway (completion/hover already degrade to empty and [ensureStarted] respawns),
     * so swallowing the failure — logged at debug — is safe and removes the noise.
     */
    private class ForgivingOutputStream(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = guarded { delegate.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) = guarded { delegate.write(b, off, len) }
        override fun flush() = guarded { delegate.flush() }
        override fun close() = guarded { delegate.close() }

        private fun guarded(block: () -> Unit) {
            try {
                block()
            } catch (e: IOException) {
                thisLogger().debug("Dropped a write to a closed XS injection server pipe", e)
            }
        }
    }

    /** Minimal client: answers `workspace/configuration` from the project settings; ignores the rest. */
    private inner class InjectionClient : LanguageClient {
        override fun telemetryEvent(`object`: Any?) {}
        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {}
        override fun showMessage(messageParams: MessageParams?) {}
        override fun logMessage(message: MessageParams?) {}
        override fun showMessageRequest(params: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> =
            CompletableFuture.completedFuture(null)

        override fun configuration(params: ConfigurationParams): CompletableFuture<MutableList<Any>> {
            val state = XsSettings.getInstance(project).state
            val xsc = JsonObject().apply {
                add("ignores", JsonArray().also { arr -> state.ignores.forEach(arr::add) })
                add(
                    "extraPreludePath",
                    if (state.extraPreludePath.isEmpty()) JsonNull.INSTANCE else JsonPrimitive(state.extraPreludePath)
                )
                add("includeDirectories", JsonArray().also { arr -> state.includeDirectories.forEach(arr::add) })
                addProperty("flavour", state.flavour)
            }
            return CompletableFuture.completedFuture(params.items.map { xsc as Any }.toMutableList())
        }
    }

    companion object {
        /** Overall budget for a single completion/hover request before giving up. */
        private const val REQUEST_TIMEOUT_MS = 3_000L
        /** Overall budget for the one-time server initialization handshake. */
        private const val INIT_TIMEOUT_MS = 10_000L
        /** Slice between cancellation checks while waiting on a server response. */
        private const val POLL_INTERVAL_MS = 25L

        fun getInstance(project: Project): XsInjectionServer =
            project.getService(XsInjectionServer::class.java)
    }
}

/** Converts a 0-based character [offset] in [text] to an LSP [Position] (UTF-16 columns). */
internal fun offsetToPosition(text: String, offset: Int): Position {
    val end = offset.coerceIn(0, text.length)
    var line = 0
    var lineStart = 0
    var i = 0
    while (i < end) {
        if (text[i] == '\n') {
            line++
            lineStart = i + 1
        }
        i++
    }
    return Position(line, end - lineStart)
}
