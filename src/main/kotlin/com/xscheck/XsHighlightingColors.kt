package com.xscheck

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object XsHighlightingColors {
    val KEYWORD  = key("XS_KEYWORD",  DefaultLanguageHighlighterColors.KEYWORD)
    val TYPE     = key("XS_TYPE",     DefaultLanguageHighlighterColors.CLASS_NAME)
    val FUNCTION = key("XS_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL)
    val CONSTANT = key("XS_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
    val PARAMETER = key("XS_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)
    val STRING   = key("XS_STRING",   DefaultLanguageHighlighterColors.STRING)
    val NUMBER   = key("XS_NUMBER",   DefaultLanguageHighlighterColors.NUMBER)
    val COMMENT  = key("XS_COMMENT",  DefaultLanguageHighlighterColors.LINE_COMMENT)
    val DOC_TAG  = key("XS_DOC_TAG",  DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
    val OPERATOR = key("XS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    private fun key(name: String, fallback: TextAttributesKey) =
        TextAttributesKey.createTextAttributesKey(name, fallback)
}
