package com.xscheck

import com.intellij.psi.tree.IElementType

object XsTokenTypes {
    val KEYWORD    = IElementType("XS_KEYWORD",    XsLanguage)
    val TYPE       = IElementType("XS_TYPE",       XsLanguage)
    val FUNCTION   = IElementType("XS_FUNCTION",   XsLanguage)
    val CONSTANT   = IElementType("XS_CONSTANT",   XsLanguage)
    val STRING     = IElementType("XS_STRING",     XsLanguage)
    val NUMBER     = IElementType("XS_NUMBER",     XsLanguage)
    val COMMENT    = IElementType("XS_COMMENT",    XsLanguage)
    val DOC_TAG    = IElementType("XS_DOC_TAG",    XsLanguage)
    val OPERATOR   = IElementType("XS_OPERATOR",   XsLanguage)
    val IDENTIFIER = IElementType("XS_IDENTIFIER", XsLanguage)
}
