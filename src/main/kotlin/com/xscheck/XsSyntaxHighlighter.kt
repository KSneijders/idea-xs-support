package com.xscheck

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class XsSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = XsLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        XsTokenTypes.KEYWORD  -> pack(XsHighlightingColors.KEYWORD)
        XsTokenTypes.TYPE     -> pack(XsHighlightingColors.TYPE)
        XsTokenTypes.FUNCTION -> pack(XsHighlightingColors.FUNCTION)
        XsTokenTypes.CONSTANT -> pack(XsHighlightingColors.CONSTANT)
        XsTokenTypes.STRING   -> pack(XsHighlightingColors.STRING)
        XsTokenTypes.NUMBER   -> pack(XsHighlightingColors.NUMBER)
        XsTokenTypes.COMMENT  -> pack(XsHighlightingColors.COMMENT)
        XsTokenTypes.DOC_TAG  -> pack(XsHighlightingColors.DOC_TAG)
        XsTokenTypes.OPERATOR -> pack(XsHighlightingColors.OPERATOR)
        else                  -> emptyArray()
    }
}
