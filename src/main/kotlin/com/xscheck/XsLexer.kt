package com.xscheck

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class XsLexer : LexerBase() {

    companion object {
        private val KEYWORDS = setOf(
            "include", "switch", "case", "while", "break", "default", "rule",
            "if", "then", "else", "goto", "label", "for", "dbg", "return", "void",
            "const", "priority", "minInterval", "maxInterval", "highFrequency",
            "active", "inactive", "group", "breakpoint", "static", "continue",
            "extern", "export", "runImmediately", "mutable", "class", "true", "false"
        )
        // Type names, case-insensitive in XS
        private val TYPES = setOf("int", "bool", "float", "string", "vector")
    }

    private data class Tok(val start: Int, val end: Int, val type: IElementType)

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokens: List<Tok> = emptyList()
    private var index = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokens = tokenize(buffer, startOffset, endOffset)
        this.index = 0
    }

    override fun getState() = 0
    override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type
    override fun getTokenStart() = if (index < tokens.size) tokens[index].start else bufferEnd
    override fun getTokenEnd() = if (index < tokens.size) tokens[index].end else bufferEnd
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = bufferEnd
    override fun advance() { if (index < tokens.size) index++ }

    private fun tokenize(text: CharSequence, from: Int, to: Int): List<Tok> {
        val out = ArrayList<Tok>()
        var i = from
        while (i < to) {
            val c = text[i]
            when {
                // Line comment
                c == '/' && i + 1 < to && text[i + 1] == '/' -> {
                    var e = i + 2
                    while (e < to && text[e] != '\n') e++
                    emitComment(text, i, e, out)
                    i = e
                }
                // Block comment
                c == '/' && i + 1 < to && text[i + 1] == '*' -> {
                    var e = i + 2
                    while (e + 1 < to && !(text[e] == '*' && text[e + 1] == '/')) e++
                    e = if (e + 1 < to) e + 2 else to
                    emitComment(text, i, e, out)
                    i = e
                }
                // String literal
                c == '"' -> {
                    var e = i + 1
                    while (e < to && text[e] != '"') {
                        if (text[e] == '\\') e++
                        e++
                    }
                    if (e < to) e++
                    out.add(Tok(i, e, XsTokenTypes.STRING))
                    i = e
                }
                // Number
                c.isDigit() -> {
                    var e = i + 1
                    while (e < to && (text[e].isDigit() || text[e] == '.')) e++
                    out.add(Tok(i, e, XsTokenTypes.NUMBER))
                    i = e
                }
                // Identifier / keyword / type / function / constant
                c.isLetter() || c == '_' -> {
                    var e = i + 1
                    while (e < to && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                    val word = text.subSequence(i, e).toString()
                    out.add(Tok(i, e, classifyWord(word, text, e, to)))
                    i = e
                }
                // Whitespace
                c.isWhitespace() -> {
                    var e = i + 1
                    while (e < to && text[e].isWhitespace()) e++
                    out.add(Tok(i, e, TokenType.WHITE_SPACE))
                    i = e
                }
                // Operators / punctuation
                else -> {
                    out.add(Tok(i, i + 1, XsTokenTypes.OPERATOR))
                    i += 1
                }
            }
        }
        return out
    }

    private fun classifyWord(word: String, text: CharSequence, wordEnd: Int, to: Int): IElementType {
        if (KEYWORDS.contains(word)) return XsTokenTypes.KEYWORD
        if (TYPES.contains(word.lowercase())) return XsTokenTypes.TYPE

        // Function: an identifier immediately followed by '(' (skipping whitespace).
        var p = wordEnd
        while (p < to && text[p].isWhitespace()) p++
        if (p < to && text[p] == '(') return XsTokenTypes.FUNCTION

        // Constant: ALL_CAPS identifier (the common XS convention for consts).
        if (isAllCaps(word)) return XsTokenTypes.CONSTANT

        return XsTokenTypes.IDENTIFIER
    }

    private fun isAllCaps(word: String): Boolean {
        var hasLetter = false
        for (ch in word) {
            if (ch in 'a'..'z') return false
            if (ch in 'A'..'Z') hasLetter = true
        }
        return hasLetter
    }

    /** Splits a comment region into plain comment text and doxygen `@tag` pieces. */
    private fun emitComment(text: CharSequence, start: Int, end: Int, out: MutableList<Tok>) {
        var chunk = start
        var i = start
        while (i < end) {
            if (text[i] == '@' && i + 1 < end && text[i + 1].isLetter()) {
                if (i > chunk) out.add(Tok(chunk, i, XsTokenTypes.COMMENT))
                var e = i + 1
                while (e < end && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                out.add(Tok(i, e, XsTokenTypes.DOC_TAG))
                chunk = e
                i = e
            } else {
                i++
            }
        }
        if (chunk < end) out.add(Tok(chunk, end, XsTokenTypes.COMMENT))
    }
}
