package com.xscheck

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl

/**
 * Answers the server's `workspace/configuration` request (section `xsc`) from the
 * per-project [XsSettings].
 *
 * This is the protocol-correct mechanism the upstream `xs-check-lsp` server already
 * expects -- identical to how the VSCode extension supplies its configuration -- so the
 * Rust core can be used completely unmodified. LSP4IJ calls [createSettings] and extracts
 * the requested section from the returned object on each `workspace/configuration` request.
 */
class XsLanguageClient(private val project: Project) : LanguageClientImpl(project) {

    override fun createSettings(): Any {
        val state = XsSettings.getInstance(project).state

        // Shape must match the server's `JsonConfig` (camelCase): see
        // xs-check/xsc-lsp/src/config/config.rs
        val xsc = JsonObject().apply {
            add("ignores", state.ignores.toJsonArray())
            add(
                "extraPreludePath",
                if (state.extraPreludePath.isEmpty()) JsonNull.INSTANCE
                else JsonPrimitive(state.extraPreludePath)
            )
            add("includeDirectories", state.includeDirectories.toJsonArray())
            addProperty("flavour", state.flavour)
        }

        return JsonObject().apply { add("xsc", xsc) }
    }

    private fun List<String>.toJsonArray(): JsonArray =
        JsonArray().also { array -> forEach(array::add) }
}
