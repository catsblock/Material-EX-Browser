package com.catsblock.materialex.core.siteedit

enum class UaMode { DEFAULT, DESKTOP, MOBILE, CUSTOM }

data class UaChoice(val mode: UaMode, val custom: String = "")

object UserAgentRules {
    const val MAX_CUSTOM_LENGTH = 256

    fun validateCustom(value: String): Boolean {
        val v = value.trim()
        return v.isNotEmpty() && v.length <= MAX_CUSTOM_LENGTH && v.none { it.isISOControl() }
    }

    fun encode(choices: Map<String, UaChoice>): String = SiteStyleRules.encode(
        choices.mapValues { (_, c) -> c.mode.name + ":" + c.custom.trim() }
    )

    fun decode(raw: String?): Map<String, UaChoice> {
        val out = LinkedHashMap<String, UaChoice>()
        for ((host, value) in SiteStyleRules.decode(raw)) {
            val sep = value.indexOf(':')
            if (sep <= 0) continue
            val mode = UaMode.entries.firstOrNull { it.name == value.substring(0, sep) } ?: continue
            val custom = value.substring(sep + 1)
            if (mode == UaMode.DEFAULT) continue
            if (mode == UaMode.CUSTOM && !validateCustom(custom)) continue
            out[host] = UaChoice(mode, if (mode == UaMode.CUSTOM) custom else "")
        }
        return out
    }

    fun with(choices: Map<String, UaChoice>, host: String, choice: UaChoice?): Map<String, UaChoice> {
        val next = LinkedHashMap(choices)
        next.remove(host)
        if (choice != null && choice.mode != UaMode.DEFAULT) next[host] = choice
        while (next.size > SiteStyleRules.MAX_SITES) next.remove(next.keys.first())
        return next
    }
}
