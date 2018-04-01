package lox

import lox.TokenType.*
import java.util.ArrayList
import java.util.HashMap

class RuntimeError(val token: Token, message: String) : RuntimeException(message)

class Return(val value: Any?) : RuntimeException(null, null, false, false)

class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    private val globals = Environment(null)
    private var environment = globals
    private val locals = HashMap<Expr, Int>()

    init {
        globals.define("clock", object : LoxCallable {
            override fun arity(): Int {
                return 0
            }

            override fun call(interpreter: Interpreter,
                              arguments: List<Any?>): Any {
                return System.currentTimeMillis().toDouble() / 1000.0
            }
        })
    }

    fun interpret(statements: List<Stmt?>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: RuntimeError) {
            Lox.runtimeError(error)
        }
    }

    private fun execute(stmt: Stmt?) {
        stmt!!.accept(this)
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    override fun visitSuperExpr(expr: Expr.Super): Any? {
        val distance = locals[expr]
        val superclass = environment.getAt(distance!!, "super") as LoxClass
        // "this" is always one level nearer than "super"'s environment.
        val obj = environment.getAt(distance - 1, "this") as LoxInstance
        return superclass.findMethod(obj, expr.method.lexeme) ?: throw RuntimeError(expr.method,
                "Undefined property '" + expr.method.lexeme + "'.")
    }


    override fun visitThisExpr(expr: Expr.This): Any? {
        return lookUpVariable(expr.keyword, expr)
    }

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val obj = evaluate(expr.obj) as? LoxInstance ?: throw RuntimeError(expr.name, "Only instances have fields.")
        val value = evaluate(expr.value)
        obj.set(expr.name, value)
        return value
    }

    override fun visitGetExpr(expr: Expr.Get): Any? {
        val obj = evaluate(expr.obj)
        if (obj is LoxInstance) {
            return obj.get(expr.name)
        }
        throw RuntimeError(expr.name, "Only instances have properties.")
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        environment.define(stmt.name.lexeme, null)
        var superclass: Any? = null
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass)
            if (superclass !is LoxClass) {
                throw RuntimeError(stmt.name, "Superclass must be a class.")
            }
            environment = Environment(environment)
            environment.define("super", superclass)
        }
        val methods = HashMap<String, LoxFunction>()
        for (method in stmt.methods) {
            val function = LoxFunction(method, environment, method.name.lexeme == "init")
            methods[method.name.lexeme] = function
        }
        val klass = LoxClass(stmt.name.lexeme, superclass as LoxClass?, methods)
        if (superclass != null) {
            environment = environment.enclosing!!
        }
        environment.assign(stmt.name, klass)
        return
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        var value: Any? = null
        if (stmt.value != null) value = evaluate(stmt.value)

        throw Return(value)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val function = LoxFunction(stmt, environment, false)
        environment.define(stmt.name.lexeme, function)
        return
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)
        val arguments = ArrayList<Any?>()
        for (argument in expr.arguments) {
            arguments.add(evaluate(argument))
        }
        if (callee !is LoxCallable) {
            throw RuntimeError(expr.paren,
                    "Can only call functions and classes.")
        }
        if (arguments.size != callee.arity()) {
            throw RuntimeError(expr.paren, "Expected " +
                    callee.arity() + " arguments but got " +
                    arguments.size + ".")
        }
        return callee.call(this, arguments)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
        return
    }

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.left)

        if (expr.operator.type === TokenType.OR) {
            if (isTruthy(left)) return left
        } else {
            if (!isTruthy(left)) return left
        }

        return evaluate(expr.right)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch)
        }
        return
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment(environment))
        return
    }

    fun executeBlock(statements: List<Stmt?>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            for (statement in statements) {
                execute(statement)
            }
        } finally {
            this.environment = previous
        }
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)
        environment.assign(expr.name, value)
        val distance = locals[expr]
        if (distance != null) {
            environment.assignAt(distance, expr.name, value)
        } else {
            globals.assign(expr.name, value)
        }
        return value
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? = lookUpVariable(expr.name, expr)

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if (distance != null) {
            environment.getAt(distance, name.lexeme)
        } else {
            globals.get(name)
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        var value: Any? = null
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer)
        }
        environment.define(stmt.name.lexeme, value)
        return
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
        return
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
        return
    }

    private fun stringify(obj: Any?): String {
        if (obj == null) return "nil"

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (obj is Double) {
            var text = obj.toString()
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length - 2)
            }
            return text
        }

        return obj.toString()
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            MINUS -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double - right as Double
            }
            SLASH -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double / right as Double
            }
            STAR -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double * right as Double
            }
            PLUS -> {
                if (left is Double && right is Double) {
                    return left + right
                }

                if (left is String && right is String) {
                    return left + right
                } else {
                    throw RuntimeError(expr.operator, "Operands must be numbers.")
                }
            }
            GREATER -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) > (right as Double)
            }
            GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) >= (right as Double)
            }
            LESS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) < (right as Double)
            }
            LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) <= (right as Double)
            }
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            else -> null
        }
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? = evaluate(expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): Any? = expr.value!!

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            MINUS -> -(right as Double)
            BANG -> !isTruthy(right)
            else -> null
        }
    }

    private fun evaluate(expr: Expr?): Any? = expr?.accept(this)

    private fun isTruthy(obj: Any?): Boolean = if (obj == null) false else obj as? Boolean ?: true

    private fun isEqual(a: Any?, b: Any?): Boolean {
        // nil is only equal to nil.
        if (a == null && b == null) return true
        return if (a == null) false else a == b

    }

    private fun checkNumberOperands(operator: Token,
                                    left: Any?, right: Any?) {
        if (left is Double && right is Double) return

        throw RuntimeError(operator, "Operands must be numbers.")
    }
}