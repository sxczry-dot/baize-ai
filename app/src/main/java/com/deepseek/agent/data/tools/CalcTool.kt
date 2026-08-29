package com.deepseek.agent.data.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 数学表达式求值（shunting-yard）。支持 + - * / ^ % 括号与常用函数。 */
class EvalExprTool : AgentTool {
    override val name = "eval_expr"
    override val description = "计算数学表达式。支持 + - * / ^ % 括号，以及 sqrt/abs/sin/cos/ln/log/exp/min/max/pi/e"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("expression") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("数学表达式，如 \"(3+5)*2^3\" 或 \"sqrt(2)*pi\""))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("expression")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val expr = runCatching { JSONObject(argsJson).getString("expression") }.getOrElse {
            return ToolResult(callId, "参数缺少 expression", false)
        }
        return try {
            val result = Evaluator(expr).evaluate()
            ToolResult(callId, "结果: $result", true)
        } catch (t: Throwable) {
            ToolResult(callId, "计算失败: ${t.message}", false)
        }
    }

    private class Evaluator(private val expr: String) {
        private val tokens = tokenize(expr)
        private var pos = 0

        fun evaluate(): Double {
            val v = parseExpression()
            if (pos < tokens.size) error("表达式在 \"${tokens[pos]}\" 处无法解析")
            return v
        }

        private fun parseExpression(): Double {
            var v = parseTerm()
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
                val op = tokens[pos++]
                val r = parseTerm()
                v = if (op == "+") v + r else v - r
            }
            return v
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/" || tokens[pos] == "%")) {
                val op = tokens[pos++]
                val r = parseFactor()
                v = when (op) {
                    "*" -> v * r
                    "/" -> v / r
                    else -> v % r
                }
            }
            return v
        }

        private fun parseFactor(): Double {
            var v = parseUnary()
            while (pos < tokens.size && tokens[pos] == "^") {
                pos++
                v = v.pow(parseUnary())
            }
            return v
        }

        private fun parseUnary(): Double {
            if (pos < tokens.size && tokens[pos] == "-") { pos++; return -parseUnary() }
            if (pos < tokens.size && tokens[pos] == "+") { pos++; return parseUnary() }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (pos >= tokens.size) error("表达式不完整")
            val t = tokens[pos++]
            if (t == "(") {
                val v = parseExpression()
                if (pos >= tokens.size || tokens[pos] != ")") error("缺少右括号")
                pos++
                return v
            }
            t.toDoubleOrNull()?.let { return it }
            // 函数或常量
            return when (t) {
                "pi" -> Math.PI
                "e" -> Math.E
                "sqrt", "abs", "sin", "cos", "ln", "log", "exp", "min", "max" -> parseFunction(t)
                else -> error("未知符号 \"$t\"")
            }
        }

        private fun parseFunction(name: String): Double {
            if (pos >= tokens.size || tokens[pos] != "(") error("$name 需要括号参数")
            pos++
            val a = parseExpression()
            if (pos >= tokens.size || tokens[pos] != ")") error("$name 缺少右括号")
            pos++
            return when (name) {
                "sqrt" -> sqrt(a)
                "abs" -> abs(a)
                "sin" -> sin(a)
                "cos" -> cos(a)
                "ln" -> ln(a)
                "log" -> log10(a)
                "exp" -> exp(a)
                else -> {
                    // min/max 需要第二个参数
                    if (pos < tokens.size && tokens[pos] == ",") {
                        pos++
                        val b = parseExpression()
                        if (pos < tokens.size && tokens[pos] == ")") pos++ else error("$name 缺少右括号")
                        if (name == "min") min(a, b) else max(a, b)
                    } else error("$name 需要两个参数")
                }
            }
        }

        companion object {
            fun tokenize(s: String): List<String> {
                val result = mutableListOf<String>()
                var i = 0
                while (i < s.length) {
                    val c = s[i]
                    when {
                        c.isWhitespace() -> i++
                        c.isDigit() || c == '.' -> {
                            val start = i
                            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                            result.add(s.substring(start, i))
                        }
                        c.isLetter() -> {
                            val start = i
                            while (i < s.length && s[i].isLetter()) i++
                            result.add(s.substring(start, i).lowercase())
                        }
                        c in "+-*/^%()," -> { result.add(c.toString()); i++ }
                        else -> error("无法识别的字符 \"$c\"")
                    }
                }
                return result
            }
        }
    }
}
