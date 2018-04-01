package lox

import java.util.HashMap
import java.util.Stack

private enum class FunctionType {
    NONE,
    FUNCTION
}

internal class Resolver(private val interpreter: Interpreter) : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
    private val scopes = Stack<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE

    override fun visitAssignExpr(expr: Expr.Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
        return
    }

    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.left)
        resolve(expr.right)
        return
    }

    override fun visitCallExpr(expr: Expr.Call) {
        resolve(expr.callee)
        for (argument in expr.arguments) {
            resolve(argument)
        }
        return
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expression)
        return
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {
        return
    }

    override fun visitLogicalExpr(expr: Expr.Logical) {
        resolve(expr.left)
        resolve(expr.right)
        return
    }

    override fun visitUnaryExpr(expr: Expr.Unary) {
        resolve(expr.right)
        return
    }

    override fun visitVariableExpr(expr: Expr.Variable) {
        if (!scopes.isEmpty() &&
                scopes.peek().get(expr.name.lexeme) == false) {
            Lox.error(expr.name,
                    "Cannot read local variable in its own initializer.")
        }
        resolveLocal(expr, expr.name)
        return
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.size - 1 downTo 0) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
        // Not found. Assume it is global.
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
        return
    }

    fun resolve(statements: List<Stmt?>) {
        for (statement in statements) {
            resolve(statement)
        }
    }

    private fun resolve(stmt: Stmt?) {
        stmt!!.accept(this)
    }

    private fun beginScope() {
        scopes.push(HashMap())
    }

    private fun endScope() {
        scopes.pop()
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        resolve(stmt.expression)
        return
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        declare(stmt.name)
        define(stmt.name)
        resolveFunction(stmt, FunctionType.FUNCTION);
        return
    }

    private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type
        beginScope()
        for (param in function.parameters) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()
        currentFunction = enclosingFunction
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        if (stmt.elseBranch != null) resolve(stmt.elseBranch)
        return
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        resolve(stmt.expression)
        return
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Cannot return from top-level code.");
        }
        if (stmt.value != null) {
            resolve(stmt.value)
        }
        return
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        declare(stmt.name)
        if (stmt.initializer != null) {
            resolve(stmt.initializer)
        }
        define(stmt.name)
        return
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name,
                    "Variable with this name already declared in this scope.")
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        scopes.peek()[name.lexeme] = true
    }

    private fun resolve(expr: Expr) {
        expr.accept(this)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
        return
    }
}