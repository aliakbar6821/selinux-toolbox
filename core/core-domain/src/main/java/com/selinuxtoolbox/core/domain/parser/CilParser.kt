package com.selinuxtoolbox.core.domain.parser

import com.selinuxtoolbox.core.model.CilStatement
import com.selinuxtoolbox.core.model.Partition

class CilParser(
    private val sourceFile: String,
    private val partition: Partition
) {

    fun parse(content: String): List<CilStatement> {
        val tokens = CilLexer(content).tokenize()
        val reader = TokenReader(tokens)
        val statements = mutableListOf<CilStatement>()

        while (!reader.isEof()) {
            try {
                if (reader.peek().type == TokenType.LPAREN) {
                    parseStatement(reader)?.let { statements.add(it) }
                } else {
                    reader.consume()
                }
            } catch (e: Exception) {
                // Skip malformed statement — continue parsing
                reader.skipToNextTopLevel()
            }
        }
        return statements
    }

    private fun parseStatement(reader: TokenReader): CilStatement? {
        val startLine = reader.peek().line
        reader.expect(TokenType.LPAREN)

        if (reader.isEof() || reader.peek().type == TokenType.RPAREN) {
            reader.tryConsume(TokenType.RPAREN)
            return null
        }

        val keyword = reader.expectSymbol()

        val statement = when (keyword) {
            "type" -> parseType(reader, startLine)
            "typeattribute" -> parseTypeAttribute(reader, startLine)
            "typeattributeset" -> parseTypeAttributeSet(reader, startLine)
            "allow" -> parseAccessRule(reader, startLine, keyword)
            "neverallow" -> parseAccessRule(reader, startLine, keyword)
            "auditallow" -> parseAccessRule(reader, startLine, keyword)
            "dontaudit" -> parseAccessRule(reader, startLine, keyword)
            "typetransition" -> parseTypeTransition(reader, startLine)
            "typechange" -> parseTypeChange(reader, startLine)
            "roleallow" -> parseRoleAllow(reader, startLine)
            "genfscon" -> parseGenFsCon(reader, startLine)
            "typebounds" -> parseGeneric(reader, startLine, keyword)
            "permissive" -> parseGeneric(reader, startLine, keyword)
            "expandtypeattribute" -> parseGeneric(reader, startLine, keyword)
            "sensitivityorder" -> parseGeneric(reader, startLine, keyword)
            "sensitivity" -> parseGeneric(reader, startLine, keyword)
            "dominance" -> parseGeneric(reader, startLine, keyword)
            "category" -> parseGeneric(reader, startLine, keyword)
            "level" -> parseGeneric(reader, startLine, keyword)
            "levelrange" -> parseGeneric(reader, startLine, keyword)
            "mlsconstrain" -> parseGeneric(reader, startLine, keyword)
            "mlsvalidatetrans" -> parseGeneric(reader, startLine, keyword)
            "constrain" -> parseGeneric(reader, startLine, keyword)
            "validatetrans" -> parseGeneric(reader, startLine, keyword)
            "sidcontext" -> parseGeneric(reader, startLine, keyword)
            "portcon" -> parseGeneric(reader, startLine, keyword)
            "netifcon" -> parseGeneric(reader, startLine, keyword)
            "nodecon" -> parseGeneric(reader, startLine, keyword)
            "fsuse" -> parseGeneric(reader, startLine, keyword)
            "ibpkeycon" -> parseGeneric(reader, startLine, keyword)
            "ibendportcon" -> parseGeneric(reader, startLine, keyword)
            "role" -> parseGeneric(reader, startLine, keyword)
            "roletype" -> parseGeneric(reader, startLine, keyword)
            "rolebounds" -> parseGeneric(reader, startLine, keyword)
            "user" -> parseGeneric(reader, startLine, keyword)
            "userrole" -> parseGeneric(reader, startLine, keyword)
            "userlevel" -> parseGeneric(reader, startLine, keyword)
            "userrange" -> parseGeneric(reader, startLine, keyword)
            "userbounds" -> parseGeneric(reader, startLine, keyword)
            "userprefix" -> parseGeneric(reader, startLine, keyword)
            "selinuxuser" -> parseGeneric(reader, startLine, keyword)
            "selinuxuserdefault" -> parseGeneric(reader, startLine, keyword)
            "block" -> parseBlock(reader, startLine)
            "blockinherit" -> parseGeneric(reader, startLine, keyword)
            "blockabstract" -> parseGeneric(reader, startLine, keyword)
            "in" -> parseInBlock(reader, startLine)
            "macro" -> parseMacro(reader, startLine)
            "call" -> parseGeneric(reader, startLine, keyword)
            "optional" -> parseOptional(reader, startLine)
            else -> parseGeneric(reader, startLine, keyword)
        }

        reader.tryConsume(TokenType.RPAREN)
        return statement
    }

    private fun parseType(reader: TokenReader, line: Int): CilStatement {
        val name = reader.expectSymbol()
        reader.skipRemaining()
        return CilStatement.TypeDeclaration(name, sourceFile, line, partition)
    }

    private fun parseTypeAttribute(reader: TokenReader, line: Int): CilStatement {
        val name = reader.expectSymbol()
        reader.skipRemaining()
        return CilStatement.TypeAttribute(name, sourceFile, line, partition)
    }

    private fun parseTypeAttributeSet(reader: TokenReader, line: Int): CilStatement {
        val attribute = reader.expectSymbol()
        val members = mutableListOf<String>()

        // Members can be a single symbol or a list in parens
        if (reader.peek().type == TokenType.LPAREN) {
            reader.expect(TokenType.LPAREN)
            while (reader.peek().type != TokenType.RPAREN && !reader.isEof()) {
                val token = reader.consume()
                if (token.type == TokenType.SYMBOL) {
                    // Handle set operators: and, or, not, xor — just collect symbols
                    if (token.value !in listOf("and", "or", "not", "xor")) {
                        members.add(token.value)
                    }
                } else if (token.type == TokenType.LPAREN) {
                    // Nested set expression — collect inner symbols
                    var depth = 1
                    while (!reader.isEof() && depth > 0) {
                        val t = reader.consume()
                        when (t.type) {
                            TokenType.LPAREN -> depth++
                            TokenType.RPAREN -> depth--
                            TokenType.SYMBOL -> {
                                if (depth > 0 && t.value !in listOf("and", "or", "not", "xor")) {
                                    members.add(t.value)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            reader.tryConsume(TokenType.RPAREN)
        } else if (reader.peek().type == TokenType.SYMBOL) {
            members.add(reader.expectSymbol())
        }

        return CilStatement.TypeAttributeSet(attribute, members, sourceFile, line, partition)
    }

    private fun parseAccessRule(
        reader: TokenReader,
        line: Int,
        keyword: String
    ): CilStatement {
        val source = reader.expectSymbol()
        val target = reader.expectSymbol()
        val objectClass = reader.expectSymbol()
        val permissions = parsePermissionSet(reader)

        return when (keyword) {
            "allow" -> CilStatement.AllowRule(
                source, target, objectClass, permissions, sourceFile, line, partition
            )
            "neverallow" -> CilStatement.NeverAllowRule(
                source, target, objectClass, permissions, sourceFile, line, partition
            )
            "auditallow" -> CilStatement.AuditAllowRule(
                source, target, objectClass, permissions, sourceFile, line, partition
            )
            "dontaudit" -> CilStatement.DontAuditRule(
                source, target, objectClass, permissions, sourceFile, line, partition
            )
            else -> CilStatement.GenericStatement(keyword,
                "$source $target $objectClass", sourceFile, line, partition)
        }
    }

    private fun parsePermissionSet(reader: TokenReader): List<String> {
        val perms = mutableListOf<String>()
        if (reader.peek().type == TokenType.LPAREN) {
            reader.expect(TokenType.LPAREN)
            while (reader.peek().type != TokenType.RPAREN && !reader.isEof()) {
                val t = reader.consume()
                if (t.type == TokenType.SYMBOL) perms.add(t.value)
            }
            reader.tryConsume(TokenType.RPAREN)
        } else if (reader.peek().type == TokenType.SYMBOL) {
            perms.add(reader.expectSymbol())
        }
        return perms
    }

    private fun parseTypeTransition(reader: TokenReader, line: Int): CilStatement {
        val source = reader.expectSymbol()
        val target = reader.expectSymbol()
        val objectClass = reader.expectSymbol()
        val defaultType = reader.expectSymbol()
        // Optional object name (quoted string)
        val objectName = if (reader.peek().type == TokenType.SYMBOL &&
            reader.peek().type != TokenType.RPAREN) {
            reader.expectSymbol()
        } else null
        reader.skipRemaining()
        return CilStatement.TypeTransition(
            source, target, objectClass, defaultType, objectName,
            sourceFile, line, partition
        )
    }

    private fun parseTypeChange(reader: TokenReader, line: Int): CilStatement {
        val source = reader.expectSymbol()
        val target = reader.expectSymbol()
        val objectClass = reader.expectSymbol()
        val defaultType = reader.expectSymbol()
        reader.skipRemaining()
        return CilStatement.TypeChange(
            source, target, objectClass, defaultType, sourceFile, line, partition
        )
    }

    private fun parseRoleAllow(reader: TokenReader, line: Int): CilStatement {
        val source = reader.expectSymbol()
        val target = reader.expectSymbol()
        reader.skipRemaining()
        return CilStatement.RoleAllow(source, target, sourceFile, line, partition)
    }

    private fun parseGenFsCon(reader: TokenReader, line: Int): CilStatement {
        val fsType = reader.expectSymbol()
        val path = reader.expectSymbol()
        val context = reader.expectSymbol()
        reader.skipRemaining()
        return CilStatement.GenFsCon(fsType, path, context, sourceFile, line, partition)
    }

    // For block/in/macro/optional — parse recursively but return as generic
    private fun parseBlock(reader: TokenReader, line: Int): CilStatement {
        val name = if (reader.peek().type == TokenType.SYMBOL) reader.expectSymbol() else ""
        val raw = StringBuilder("block $name")
        reader.skipRemaining()
        return CilStatement.GenericStatement("block", raw.toString(), sourceFile, line, partition)
    }

    private fun parseInBlock(reader: TokenReader, line: Int): CilStatement {
        reader.skipRemaining()
        return CilStatement.GenericStatement("in", "", sourceFile, line, partition)
    }

    private fun parseMacro(reader: TokenReader, line: Int): CilStatement {
        reader.skipRemaining()
        return CilStatement.GenericStatement("macro", "", sourceFile, line, partition)
    }

    private fun parseOptional(reader: TokenReader, line: Int): CilStatement {
        reader.skipRemaining()
        return CilStatement.GenericStatement("optional", "", sourceFile, line, partition)
    }

    private fun parseGeneric(reader: TokenReader, line: Int, keyword: String): CilStatement {
        val raw = StringBuilder()
        var depth = 0
        while (!reader.isEof()) {
            val t = reader.peek()
            if (t.type == TokenType.RPAREN && depth == 0) break
            val consumed = reader.consume()
            when (consumed.type) {
                TokenType.LPAREN -> { depth++; raw.append("(") }
                TokenType.RPAREN -> { depth--; raw.append(")") }
                TokenType.SYMBOL -> raw.append(consumed.value).append(" ")
                else -> {}
            }
        }
        return CilStatement.GenericStatement(keyword, raw.toString().trim(), sourceFile, line, partition)
    }
}

// Helper to navigate token list
class TokenReader(private val tokens: List<Token>) {
    private var pos = 0

    fun peek(): Token = tokens.getOrElse(pos) {
        Token(TokenType.EOF, "", 0)
    }

    fun consume(): Token {
        val t = peek()
        pos++
        return t
    }

    fun expect(type: TokenType): Token {
        val t = consume()
        if (t.type != type) {
            throw IllegalStateException(
                "Expected $type but got ${t.type} ('${t.value}') at line ${t.line}"
            )
        }
        return t
    }

    fun expectSymbol(): String = expect(TokenType.SYMBOL).value

    fun tryConsume(type: TokenType): Boolean {
        return if (peek().type == type) {
            consume()
            true
        } else false
    }

    fun isEof(): Boolean = peek().type == TokenType.EOF

    // Skip tokens until we close the current statement
    fun skipRemaining() {
        var depth = 0
        while (!isEof()) {
            val t = peek()
            when (t.type) {
                TokenType.LPAREN -> { depth++; consume() }
                TokenType.RPAREN -> {
                    if (depth == 0) return  // let caller consume closing paren
                    depth--
                    consume()
                }
                TokenType.EOF -> return
                else -> consume()
            }
        }
    }

    // Skip to next top-level statement after a parse error
    fun skipToNextTopLevel() {
        var depth = 0
        while (!isEof()) {
            val t = consume()
            when (t.type) {
                TokenType.LPAREN -> depth++
                TokenType.RPAREN -> {
                    depth--
                    if (depth <= 0) return
                }
                else -> {}
            }
        }
    }
}
