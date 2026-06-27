package com.xscheck

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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

    @Synchronized
    private fun ensureStarted(): LanguageServer? {
        server?.let { if (process?.isAlive == true) return it }
        // Clean up a dead instance before restarting.
        process?.destroy()
        server = null
        try {
            val proc = ProcessBuilder(XsServerFactory.binary.absolutePath)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val launcher = LSPLauncher.createClientLauncher(InjectionClient(), proc.inputStream, proc.outputStream)
            val srv = launcher.remoteProxy
            launcher.startListening()

            val init = InitializeParams().apply {
                capabilities = ClientCapabilities().apply {
                    workspace = WorkspaceClientCapabilities().apply { configuration = true }
                }
            }
            srv.initialize(init).get(10, TimeUnit.SECONDS)
            srv.initialized(InitializedParams())

            // Open the document once with empty text so the server's caches are populated
            // before the first completion request (its handlers assume a cached document).
            version = 1
            srv.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "xs", version, ""))
            )

            process = proc
            server = srv
            return srv
        } catch (t: Throwable) {
            thisLogger().warn("XS injection server failed to start", t)
            process?.destroy()
            process = null
            server = null
            return null
        }
    }

    private fun sync(srv: LanguageServer, text: String) {
        val id = VersionedTextDocumentIdentifier(uri, ++version)
        srv.textDocumentService.didChange(
            DidChangeTextDocumentParams(id, listOf(TextDocumentContentChangeEvent(text)))
        )
    }

    fun completion(text: String, position: Position): List<CompletionItem> {
        val srv = ensureStarted() ?: return emptyList()
        return try {
            sync(srv, text)
            val result = srv.textDocumentService
                .completion(CompletionParams(TextDocumentIdentifier(uri), position))
                .get(3, TimeUnit.SECONDS)
            when {
                result == null -> emptyList()
                result.isLeft -> result.left
                else -> result.right.items
            }
        } catch (t: Throwable) {
            thisLogger().debug("XS injection completion failed", t)
            emptyList()
        }
    }

    fun hover(text: String, position: Position): Hover? {
        val srv = ensureStarted() ?: return null
        return try {
            sync(srv, text)
            srv.textDocumentService
                .hover(HoverParams(TextDocumentIdentifier(uri), position))
                .get(3, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            thisLogger().debug("XS injection hover failed", t)
            null
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
