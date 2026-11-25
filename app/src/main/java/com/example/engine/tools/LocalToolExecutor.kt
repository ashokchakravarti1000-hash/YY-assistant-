package com.example.engine.tools

import com.example.core.common.DeviceTier
import com.example.core.common.ToolApprovalRequest
import com.example.core.common.formatBytes
import com.example.core.device.DeviceProfiler
import com.example.engine.rag.LocalRagEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Section 11 & 14 #31: On-Device Tool Use with Explicit User Approval.
 * Tools never execute silently — they emit a ToolApprovalRequest first, and only run
 * locally once the user taps Approve.
 */
class LocalToolExecutor(
    private val deviceProfiler: DeviceProfiler,
    private val ragEngine: LocalRagEngine
) {
    fun detectToolRequest(prompt: String, conversationId: String): ToolApprovalRequest? {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase()

        // 1. Local Calculator tool detection
        val calcRegex = Regex("(?:calculate|compute|solve|eval|what is)\\s+([0-9()+\\-*/.^\\s]{3,60})", RegexOption.IGNORE_CASE)
        val directExprRegex = Regex("^([0-9.\\s()+\\-*/^]+)$")
        val calcMatch = calcRegex.find(trimmed)?.groupValues?.get(1)?.trim()
            ?: if (directExprRegex.matches(trimmed) && trimmed.any { it in "+-*/^" }) trimmed else null
        if (!calcMatch.isNullOrBlank()) {
            return ToolApprovalRequest(
                id = "tool-" + UUID.randomUUID().toString().take(6),
                toolName = "local_calculator",
                description = "Evaluate math expression locally on-device",
                arguments = calcMatch,
                conversationId = conversationId,
                originalPrompt = prompt
            )
        }

        // 2. Local Library / File Search tool detection
        if (lower.startsWith("/search ") || lower.contains("search my library") || lower.contains("find in my files")) {
            val query = trimmed
                .removePrefix("/search ")
                .replace(Regex("(?i)(search my library for|search my library|find in my files for|find in my files)"), "")
                .trim()
                .ifBlank { trimmed }
            return ToolApprovalRequest(
                id = "tool-" + UUID.randomUUID().toString().take(6),
                toolName = "local_file_search",
                description = "Search indexed documents in encrypted private RAG library",
                arguments = query,
                conversationId = conversationId,
                originalPrompt = prompt
            )
        }

        // 3. Hardware Telemetry / Diagnostics tool
        if (lower.contains("check my ram") || lower.contains("device status") || lower.contains("thermal status") || lower.startsWith("/device")) {
            return ToolApprovalRequest(
                id = "tool-" + UUID.randomUUID().toString().take(6),
                toolName = "device_diagnostics",
                description = "Read local RAM budget, free storage, SoC, and thermal state",
                arguments = "hardware_snapshot",
                conversationId = conversationId,
                originalPrompt = prompt
            )
        }

        // 4. Local Clock & Calendar date check
        if (lower.contains("what time is it") || lower.contains("today's date") || lower.startsWith("/time")) {
            return ToolApprovalRequest(
                id = "tool-" + UUID.randomUUID().toString().take(6),
                toolName = "local_clock",
                description = "Read local system date and time (offline)",
                arguments = "now",
                conversationId = conversationId,
                originalPrompt = prompt
            )
        }

        return null
    }

    suspend fun executeApproved(request: ToolApprovalRequest, tier: DeviceTier): String = when (request.toolName) {
        "local_calculator" -> {
            runCatching {
                val res = evaluateExpression(request.arguments)
                val formatted = if (res == res.toLong().toDouble()) res.toLong().toString() else "%.6f".format(res).trimEnd('0').trimEnd('.')
                "Tool `local_calculator` approved & executed:\n`${request.arguments}` = **$formatted**"
            }.getOrElse {
                "Tool `local_calculator` could not parse `${request.arguments}`: ${it.message}"
            }
        }
        "local_file_search" -> {
            val hits = ragEngine.retrieve(request.arguments, tier)
            if (hits.isEmpty()) {
                "Tool `local_file_search` completed: No indexed chunks matched \"${request.arguments}\". Add documents in the Library tab to search them offline."
            } else {
                buildString {
                    appendLine("Tool `local_file_search` found ${hits.size} local matches for \"${request.arguments}\":")
                    hits.forEachIndexed { idx, h ->
                        appendLine("${idx + 1}. **${h.documentName}** (p.${h.page}, §${h.heading}) — ${h.snippet.take(140)}…")
                    }
                }
            }
        }
        "device_diagnostics" -> {
            deviceProfiler.refresh()
            val p = deviceProfiler.profile.value
            buildString {
                appendLine("Tool `device_diagnostics` snapshot:")
                appendLine("• Assigned Tier: **${p.effectiveTier.label}** (SoC: ${p.socModel}, ABI: ${p.abi})")
                appendLine("• Memory: ${formatBytes(p.availRamBytes)} free / ${formatBytes(p.totalRamBytes)} total (60% AI budget: ${formatBytes(p.memoryBudgetBytes)})")
                appendLine("• Storage: ${formatBytes(p.freeStorageBytes)} free / ${formatBytes(p.totalStorageBytes)} total")
                appendLine("• Thermal Status: **${p.thermalLevel.label}** · Recommended decode threads: ${p.recommendedThreads}")
            }
        }
        "local_clock" -> {
            val fmt = SimpleDateFormat("EEEE, MMMM d, yyyy · hh:mm:ss a z", Locale.getDefault())
            "Tool `local_clock` result: **${fmt.format(Date())}** (read locally from device clock)."
        }
        else -> "Unknown local tool: ${request.toolName}"
    }

    /**
     * Safe recursive-descent arithmetic parser supporting +, -, *, /, ^, parentheses, and unary minus.
     */
    fun evaluateExpression(expr: String): Double = evaluateMathExpression(expr)

    companion object {
        fun evaluateMathExpression(expr: String): Double {
            val cleaned = expr.replace(" ", "")
            var pos = 0

            fun peek(): Char? = cleaned.getOrNull(pos)
            fun consume(): Char = cleaned[pos++]

            lateinit var parseExpr: () -> Double

            fun parsePrimary(): Double {
                if (peek() == '(') {
                    consume()
                    val v = parseExpr()
                    require(peek() == ')') { "Missing closing parenthesis" }
                    consume()
                    return v
                }
                if (peek() == '-') {
                    consume()
                    return -parsePrimary()
                }
                if (peek() == '+') {
                    consume()
                    return parsePrimary()
                }
                val start = pos
                while (peek()?.let { it.isDigit() || it == '.' } == true) consume()
                require(pos > start) { "Expected number at position $pos" }
                return cleaned.substring(start, pos).toDouble()
            }

            fun parsePower(): Double {
                val base = parsePrimary()
                return if (peek() == '^') {
                    consume()
                    base.pow(parsePower())
                } else {
                    base
                }
            }

            fun parseTerm(): Double {
                var left = parsePower()
                while (peek() == '*' || peek() == '/') {
                    val op = consume()
                    val right = parsePower()
                    left = if (op == '*') left * right else {
                        require(right != 0.0) { "Division by zero" }
                        left / right
                    }
                }
                return left
            }

            parseExpr = {
                var left = parseTerm()
                while (peek() == '+' || peek() == '-') {
                    val op = consume()
                    val right = parseTerm()
                    left = if (op == '+') left + right else left - right
                }
                left
            }

            val result = parseExpr()
            require(pos == cleaned.length) { "Unexpected character '${cleaned[pos]}'" }
            require(!result.isNaN() && !result.isInfinite()) { "Result out of finite range" }
            return result
        }
    }
}
