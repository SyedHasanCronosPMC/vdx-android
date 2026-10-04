package com.vdx.call

import java.util.Locale

data class CallTarget(val contactId: String, val name: String, val number: String)

/** No Android or network dependencies. A pending target is never an executed call. */
class CallFlow(private val now: () -> Long) {
    private var pending: CallTarget? = null
    private var expiresAt = 0L
    val awaitingConfirmation: Boolean get() = pending != null

    fun propose(target: CallTarget): Boolean {
        cancel()
        val number = normalizeNumber(target.number) ?: return false
        pending = target.copy(number = number)
        expiresAt = now() + 60_000
        return true
    }

    fun confirm(): CallTarget? {
        val target = pending
        val valid = now() < expiresAt
        cancel() // consume before handing the target to Android, including failed launches
        return target.takeIf { valid }
    }

    fun cancel() { pending = null; expiresAt = 0 }

    companion object {
        fun normalizeNumber(raw: String): String? {
            val value = raw.trim()
            if (!value.matches(Regex("\\+?[0-9() .-]+"))) return null
            val normalized = value.filter { it in '0'..'9' || it == '+' }
            return normalized.takeIf { it.count { c -> c in '0'..'9' } in 3..15 }
        }

        fun targetFromInput(raw: String): String? {
            val text = raw.trim().replace(Regex("\\s+"), " ")
            if (text.length !in 1..100) return null
            val target = text.replace(Regex("^(?:please )?(?:call|dial|phone)(?:\\s+|$)", RegexOption.IGNORE_CASE), "").trim()
            if (target.lowercase(Locale.ROOT) in setOf("", "yes", "no", "cancel", "never mind", "uh", "um")) return null
            return target.takeIf { normalizeNumber(it) != null || it.matches(Regex("[\\p{L}][\\p{L}\\p{M} .'-]*")) }
        }

        fun isYes(text: String) = text.trim().lowercase(Locale.ROOT) in setOf("yes", "yes please", "confirm")
        fun isCancel(text: String) = text.trim().lowercase(Locale.ROOT) in setOf("no", "cancel", "never mind", "stop")
    }
}
