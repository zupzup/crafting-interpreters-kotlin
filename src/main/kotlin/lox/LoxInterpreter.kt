package lox

import java.nio.charset.Charset
import java.nio.file.Paths
import java.nio.file.Files
import java.io.BufferedReader
import java.io.InputStreamReader

class Lox {
    companion object {
        var hadError = false
        var hadRuntimeError = false
        fun error(token: Token, message: String) {
            if (token.type === TokenType.EOF) {
                report(token.line, " at end", message)
            } else {
                report(token.line, " at '" + token.lexeme + "'", message)
            }
            hadError = true
        }

        private fun report(line: Int, where: String, message: String) {
            System.err.println(
                    "[line $line] Error$where: $message")
        }

        fun runtimeError(error: RuntimeError) {
            System.err.println(error.message +
                    "\n[line " + error.token.line + "]")
            hadRuntimeError = true
        }
    }

    fun runFile(path: String) {
        val bytes = Files.readAllBytes(Paths.get(path))
        run(String(bytes, Charset.defaultCharset()))
        if (hadError) System.exit(65)
        if (hadRuntimeError) System.exit(70)
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

        val parser = Parser(tokens)
        val expression = parser.parse()

        // Stop if there was a syntax error.
        if (hadError) return

        val interpreter = Interpreter()
        interpreter.interpret(expression)

        if (expression != null) {
            println(AstPrinter().print(expression))
        }

        // For now, just print the tokens.
        for (token in tokens) {
            System.out.println(token)
        }
    }
}

fun main(args: Array<String>) {
    val lox = Lox()
    when {
        args.size > 1 -> System.out.println("Usage: jlox [script]")
        args.size == 1 -> lox.runFile(args[0])
        else -> lox.runPrompt()
    }
}