package com.xscheck

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.io.File
import java.security.MessageDigest

class XsServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        XsConnectionProvider(binary.absolutePath)

    override fun createLanguageClient(project: Project): LanguageClientImpl =
        XsLanguageClient(project)

    companion object {
        val binary: File by lazy { extractBinary() }

        private fun extractBinary(): File {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            val isArm = arch.contains("aarch64") || arch.contains("arm")

            val (platform, ext) = when {
                os.contains("win")  -> "win32-x64" to ".exe"
                os.contains("mac")  -> (if (isArm) "darwin-arm64" else "darwin-x64") to ""
                else                -> (if (isArm) "linux-arm64" else "linux-x64") to ""
            }

            val resourcePath = "/binaries/$platform/xs-check-lsp$ext"
            val bytes = XsServerFactory::class.java.getResourceAsStream(resourcePath)?.use { it.readBytes() }
                ?: error("xs-check-lsp binary not found for platform: $platform (resource: $resourcePath)")

            // Cache keyed by the binary's content hash, so a rebuilt server (even with an
            // unchanged plugin version) is always re-extracted instead of reusing a stale copy.
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .take(8).joinToString("") { "%02x".format(it) }
            val cacheDir = File(System.getProperty("user.home"), ".xscheck").also { it.mkdirs() }

            val binary = File(cacheDir, "xs-check-lsp-$hash$ext")
            if (!binary.exists() || binary.length() != bytes.size.toLong()) {
                binary.writeBytes(bytes)
                if (ext.isEmpty()) binary.setExecutable(true)
            }

            return binary
        }
    }
}

class XsConnectionProvider(binaryPath: String)
    : ProcessStreamConnectionProvider(listOf(binaryPath))
