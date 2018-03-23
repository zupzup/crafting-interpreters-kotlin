package lox

import java.nio.charset.Charset
import java.nio.file.Paths
import java.nio.file.Files
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList


fun main(args: Array<String>) {
    val lox = Lox()
    if (args.size > 1) {
        System.out.println("Usage: jlox [script]")
    } else if (args.size == 1) {
        lox.runFile(args[0])
    } else {
        lox.runPrompt()
    }
}


class Lox {
    var hadError = false

    companion object {
        fun error(line: Int, message: String) {
            report(line, "", message)
        }

        private fun report(line: Int, where: String, message: String) {
            System.err.println(
                    "[line $line] Error$where: $message")
        }
    }

    fun runFile(path: String) {
        val bytes = Files.readAllBytes(Paths.get(path))
        run(String(bytes, Charset.defaultCharset()))
        if (hadError) System.exit(65)
    }

    fun runPrompt() {
        val input = InputStreamReader(System.`in`)
        val reader = BufferedReader(input)

        while (true) {
            print("> ")
            run(reader.readLine())
            hadError = false
        }
    }

    private fun run(source: String) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()

        // For now, just print the tokens.
        for (token in tokens) {
            System.out.println(token)
        }
    }
}


internal class Scanner(private val source: String) {
    private val tokens = ArrayList<Token>()
    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            '{' -> addToken(TokenType.LEFT_BRACE)
            '}' -> addToken(TokenType.RIGHT_BRACE)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            '-' -> addToken(TokenType.MINUS)
            '+' -> addToken(TokenType.PLUS)
            ';' -> addToken(TokenType.SEMICOLON)
            '*' -> addToken(TokenType.STAR)
            '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '/' -> {
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(TokenType.SLASH);
                }
            }
            ' ' -> {
            }
            '\r' -> {
            }
            '\t' -> {
            }
        // Ignore whitespace.
            '\n' -> {
                line++
            }
            '"' -> string()
            else -> Lox.error(line, "Unexpected character.")
        }
    }

    private fun advance(): Char {
        current++
        return source[current - 1]
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    private fun peek(): Char {
        return if (isAtEnd()) '\u0000' else source[current]
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        // Unterminated string.
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.")
            return
        }

        // The closing ".
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }
}

internal enum class TokenType {
    // Single-character tokens.
    LEFT_PAREN,
    RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    // One or two character tokens.
    BANG,
    BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // Literals.
    IDENTIFIER,
    STRING, NUMBER,

    // Keywords.
    AND,
    CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

    EOF
}

internal class Token(val type: TokenType, val lexeme: String, val literal: Any?, val line: Int) {

    override fun toString(): String {
        return type.toString() + " " + lexeme + " " + literal
    }
}