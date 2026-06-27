package com.xscheck

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Minimal parser definition for XS. It reuses [XsLexer] and a flat [XsParser]; its purpose
 * is to give the language a PSI tree so it can be used as a language-injection target.
 * It does not affect the LSP analysis of real `.xs` files.
 */
class XsParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = XsLexer()
    override fun createParser(project: Project?): PsiParser = XsParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = STRINGS
    override fun createFile(viewProvider: FileViewProvider): PsiFile = XsFile(viewProvider)

    // The flat parser produces no composite elements, so this is never called.
    override fun createElement(node: ASTNode): PsiElement =
        throw UnsupportedOperationException("XS has no composite PSI elements: ${node.elementType}")

    companion object {
        val FILE = IFileElementType(XsLanguage)
        val COMMENTS = TokenSet.create(XsTokenTypes.COMMENT, XsTokenTypes.DOC_TAG)
        val STRINGS = TokenSet.create(XsTokenTypes.STRING)
    }
}
