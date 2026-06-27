package com.xscheck

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Hover / quick-documentation for XS **injected** into other files, by proxying to an
 * isolated xs-check server. Bails on real `.xs` files (the LSP4IJ-managed server already
 * provides documentation there).
 */
class XsInjectionDocumentationProvider : DocumentationProvider {

    // The flat XS PSI has no references, so quick-doc would otherwise find no element to
    // document. Hand it the word token under the caret so generateDoc gets called.
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        if (contextElement == null) return null
        if (!InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return null
        return if (contextElement.node?.elementType in DOCUMENTABLE) contextElement else null
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = originalElement ?: element ?: return null
        val file = target.containingFile ?: return null
        val project = file.project
        if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) return null

        val hover = XsInjectionServer.getInstance(project)
            .hover(file.text, offsetToPosition(file.text, target.textRange.startOffset)) ?: return null

        val markdown = hover.contents?.let { contents ->
            when {
                contents.isRight -> contents.right.value
                else -> contents.left.joinToString("\n") { if (it.isLeft) it.left else it.right.value }
            }
        }?.takeIf { it.isNotBlank() } ?: return null

        return markdownToHtml(markdown)
    }
}

/** Word tokens worth offering documentation for. */
private val DOCUMENTABLE = setOf(
    XsTokenTypes.IDENTIFIER,
    XsTokenTypes.FUNCTION,
    XsTokenTypes.CONSTANT,
    XsTokenTypes.TYPE,
    XsTokenTypes.KEYWORD,
)

/** Minimal Markdown -> HTML for the quick-doc popup: fenced code blocks, inline code, line breaks. */
private fun markdownToHtml(markdown: String): String {
    val html = StringBuilder("<html><body>")
    markdown.split("```").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            // Inside a fence: drop the language tag on the first line, render the rest verbatim.
            val code = part.substringAfter('\n', part)
            html.append("<pre>").append(escape(code.trimEnd())).append("</pre>")
        } else {
            html.append(renderInline(part))
        }
    }
    return html.append("</body></html>").toString()
}

private fun renderInline(text: String): String {
    val out = StringBuilder()
    val segments = text.split("`")
    segments.forEachIndexed { index, segment ->
        if (index % 2 == 1) out.append("<code>").append(escape(segment)).append("</code>")
        else out.append(escape(segment).replace("\n", "<br>"))
    }
    return out.toString()
}

private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)
