package com.catsblock.materialex.core

data class AppLanguage(val tag: String, val nativeName: String)

object AppLanguages {
    const val SYSTEM = ""

    val all: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("en-GB", "English (United Kingdom)"),
        AppLanguage("pt", "Português"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("es", "Español"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("fr", "Français"),
        AppLanguage("it", "Italiano"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("fi", "Suomi"),
        AppLanguage("sv", "Svenska"),
        AppLanguage("pl", "Polski"),
        AppLanguage("uk", "Українська"),
        AppLanguage("ru", "Русский"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("th", "ไทย"),
        AppLanguage("ar", "العربية")
    )

    private val rtlLanguages = setOf("ar")

    fun isRtl(tag: String): Boolean = tag.substringBefore('-').lowercase() in rtlLanguages

    fun fromTag(tag: String?): AppLanguage? =
        all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

    fun resolve(deviceTags: List<String>): AppLanguage {
        for (raw in deviceTags) {
            val tag = raw.replace('_', '-')
            val exact = fromTag(tag)
            if (exact != null) return exact
            val language = tag.substringBefore('-').lowercase()
            val byLanguage = all.firstOrNull { it.tag.substringBefore('-').equals(language, ignoreCase = true) }
            if (byLanguage != null) return all.first { it.tag.equals(byLanguage.tag.substringBefore('-'), ignoreCase = true) }
        }
        return all.first()
    }
}
