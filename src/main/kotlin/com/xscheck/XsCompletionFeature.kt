package com.xscheck

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Makes LSP completion work with multiple carets.
 *
 * LSP4IJ marks its items with [CodeCompletionHandlerBase.DIRECT_INSERTION], which makes the
 * platform call `handleInsert` exactly once, for the primary caret only — and LSP4IJ's insert
 * logic itself works from offsets captured for that caret. So with several carets, only one
 * of them ever got the completion.
 */
class XsCompletionFeature : LSPCompletionFeature() {
    override fun createLookupElement(item: CompletionItem, context: LSPCompletionContext): LookupElement? {
        if (item.label.isNullOrBlank()) return null
        return XsCompletionProposal(item, context, this)
    }
}

private class XsCompletionProposal(
    item: CompletionItem,
    context: LSPCompletionFeature.LSPCompletionContext,
    feature: LSPCompletionFeature,
) : LSPCompletionProposal(item, context, feature) {

    /** Decided once, when completion is invoked, so every caret takes the same path. */
    private val multiCaret = context.parameters.editor.caretModel.caretCount > 1

    init {
        if (multiCaret) {
            // Hand insertion back to the platform: it replaces the typed prefix with the lookup
            // string at *every* caret, then calls handleInsert once per caret with that caret's
            // offsets. (LSP4IJ's constructor set this flag; null removes it.)
            putUserData(CodeCompletionHandlerBase.DIRECT_INSERTION, null)
        }
    }

    override fun handleInsert(context: InsertionContext) {
        if (!multiCaret) {
            // Single caret: LSP4IJ's full behaviour (snippet template, command, parameter hints).
            super.handleInsert(context)
            return
        }

        // The platform already inserted the identifier at this caret. Snippets/templates can't
        // span carets, so for functions and rules just emulate the server's `name($0)` snippet.
        if (item.kind != CompletionItemKind.Function) return

        val document = context.document
        val tail = context.tailOffset
        val chars = document.charsSequence
        if (tail >= chars.length || chars[tail] != '(') {
            document.insertString(tail, "()")
        }
        context.editor.caretModel.moveToOffset(tail + 1)
    }
}
