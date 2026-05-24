package com.selinuxtoolbox.core.domain.parser

// Token types in CIL
enum class TokenType {
    LPAREN,    // (
    RPAREN,    // )
    SYMBOL,    // any word/identifier
    EOF
}

data class Token(
    val type: TokenType,
    val value: String,
    val line: Int
)

class CilLexer(private val input: String) {

    private var pos = 0
    private var line = 1

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < input.length) {
            skipWhitespaceAndComments()
            if (pos >= input.length) break

            val ch = input[pos]
            val currentLine = line

            when {
                ch == '(' -> {
                    tokens.add(Token(TokenType.LPAREN, "(", currentLine))
                    pos++
                }
                ch == ')' -> {
                    tokens.add(Token(TokenType.RPAREN, ")", currentLine))
                    pos++
                }
                ch == '"' -> {
                    // Quoted string
                    val sym = readQuotedString()
                    tokens.add(Token(TokenType.SYMBOL, sym, currentLine))
                }
                !ch.isWhitespace() -> {
                    val sym = readSymbol()
                    tokens.add(Token(TokenType.SYMBOL, sym, currentLine))
                }
            }
        }
        tokens.add(Token(TokenType.EOF, "", line))
        return tokens
    }

    private fun skipWhitespaceAndComments() {
        while (pos < input.length) {
            val ch = input[pos]
            when {
                ch == '\n' -> { line++; pos++ }
                ch.isWhitespace() -> pos++
                ch == ';' -> {
                    // Comment — skip to end of line
                    while (pos < input.length && input[pos] != '\n') pos++
                }
                else -> return
            }
        }
    }

    private fun readSymbol(): String {
        val start = pos
        while (pos < input.length &&
            !input[pos].isWhitespace() &&
            input[pos] != '(' &&
            input[pos] != ')' &&
            input[pos] != ';'
        ) {
            pos++
        }
        return input.substring(start, pos)
    }

    private fun readQuotedString(): String {
        pos++ // skip opening quote
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != '"') {
            if (input[pos] == '\n') line++
            sb.append(input[pos])
            pos++
        }
        if (pos < input.length) pos++ // skip closing quote
        return sb.toString()
    }
}
