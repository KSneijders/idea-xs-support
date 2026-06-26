package com.xscheck

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.features.semanticTokens.DefaultSemanticTokensColorsProvider
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider

/**
 * Colors the LSP `parameter` semantic token with the XS parameter color, and delegates
 * everything else (including plain `variable`) to LSP4IJ's default mapping -- so regular
 * variables keep the theme default.
 *
 * Registered (scoped to the XS server) via the `semanticTokensColorsProvider` extension
 * point in plugin.xml.
 */
class XsSemanticTokensColorsProvider : SemanticTokensColorsProvider {
    private val default = DefaultSemanticTokensColorsProvider()

    override fun getTextAttributesKey(
        tokenType: String,
        tokenModifiers: List<String>,
        file: PsiFile,
    ): TextAttributesKey? {
        if (tokenType == "parameter") return XsHighlightingColors.PARAMETER
        return default.getTextAttributesKey(tokenType, tokenModifiers, file)
    }
}
