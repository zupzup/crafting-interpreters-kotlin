//package lox
//
//internal class AstPrinter : Expr.Visitor<String> {
//    override fun visitBinaryExpr(expr: Expr.Binary): String = parenthesize(expr.operator.lexeme, expr.left, expr.right)
//
//    override fun visitGroupingExpr(expr: Expr.Grouping): String = parenthesize("group", expr.expression)
//
//    override fun visitLiteralExpr(expr: Expr.Literal): String {
//        if (expr.value == null) return "nil"
//        return expr.value.toString()
//    }
//
//    override fun visitUnaryExpr(expr: Expr.Unary): String = parenthesize(expr.operator.lexeme, expr.right)
//
//    private fun parenthesize(name: String, vararg exprs: Expr): String {
//        val builder = StringBuilder()
//
//        builder.append("(").append(name)
//        for (expr in exprs) {
//            builder.append(" ")
//            builder.append(expr.accept(this))
//        }
//        builder.append(")")
//
//        return builder.toString()
//    }
//}
