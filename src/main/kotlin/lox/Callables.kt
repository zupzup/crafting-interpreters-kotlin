package lox

import java.util.HashMap

interface LoxCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

class LoxFunction(
        private val declaration: Stmt.Function,
        private val closure: Environment? = null,
        private val isInitializer: Boolean
) : LoxCallable {
    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in 0 until declaration.parameters.size) {
            environment.define(declaration.parameters[i].lexeme,
                    arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            return returnValue.value
        }
        if (isInitializer) return closure?.getAt(0, "this")
        return null
    }

    override fun arity(): Int {
        return declaration.parameters.size
    }

    override fun toString(): String {
        return "<fn " + declaration.name.lexeme + ">"
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }
}

class LoxClass(
        val name: String,
        private val superclass: LoxClass?,
        private val methods: Map<String, LoxFunction>
) : LoxCallable {

    override fun arity(): Int {
        val initializer = methods["init"] ?: return 0
        return initializer.arity()
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = LoxInstance(this)
        val initializer = methods["init"]
        initializer?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun toString(): String {
        return name
    }

    fun findMethod(instance: LoxInstance, name: String): LoxFunction? {
        if (methods.containsKey(name)) {
            return methods[name]?.bind(instance)
        }
        if (superclass != null) {
            return superclass.findMethod(instance, name)
        }
        return null
    }
}


class LoxInstance(private val klass: LoxClass) {
    private val fields = HashMap<String, Any?>()

    fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) {
            return fields[name.lexeme]
        }
        val method = klass.findMethod(this, name.lexeme)
        if (method != null) return method
        throw RuntimeError(name, "Undefined property '" + name.lexeme + "'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String {
        return klass.name + " instance"
    }
}