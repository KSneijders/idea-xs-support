package com.xscheck

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Flat parser: drains every token from [XsLexer] into the file root node.
 *
 * XS has no client-side grammar — real analysis is done by the LSP server — but a parser
 * definition is required for the language to have a PSI tree. That tree is what makes XS
 * available for language injection, and what gives injected fragments the lexer's
 * highlighting.
 */
class XsParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }
}
