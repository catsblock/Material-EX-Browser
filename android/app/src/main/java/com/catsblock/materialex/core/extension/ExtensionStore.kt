package com.catsblock.materialex.core.extension

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

data class StoreExtension(
    val guid: String,
    val name: String,
    val summary: String,
    val installUrl: String,
    val users: Long,
    val rating: Double?
)

data class StorePage(val items: List<StoreExtension>, val hasMore: Boolean)

object ExtensionStore {
    const val PAGE_SIZE = 25
    private const val API = "https://addons.mozilla.org/api/v5/addons/search/"

    fun searchUrl(query: String, page: Int): String {
        val q = query.trim().take(80)
        val builder = StringBuilder(API)
            .append("?app=android&type=extension&sort=users&lang=en-US")
            .append("&page_size=").append(PAGE_SIZE)
            .append("&page=").append(page.coerceAtLeast(1))
        if (q.isNotEmpty()) builder.append("&q=").append(URLEncoder.encode(q, "UTF-8"))
        return builder.toString()
    }

    fun isTrustedInstallUrl(url: String): Boolean {
        val normalized = ExtensionRules.normalizeInstallUrl(url) ?: return false
        val host = normalized.substring(8).substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
        return host == "addons.mozilla.org" || host == "addons.cdn.mozilla.net"
    }

    fun parse(body: String): StorePage {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return StorePage(emptyList(), false)
        val results = root.optJSONArray("results") ?: JSONArray()
        val items = ArrayList<StoreExtension>()
        for (i in 0 until results.length()) {
            val entry = results.optJSONObject(i) ?: continue
            val guid = entry.optString("guid")
            val name = localized(entry.opt("name"))
            val installUrl = entry.optJSONObject("current_version")?.optJSONObject("file")?.optString("url").orEmpty()
            if (guid.isEmpty() || name.isEmpty() || !isTrustedInstallUrl(installUrl)) continue
            val rating = entry.optJSONObject("ratings")?.let { if (it.has("average")) it.optDouble("average") else null }
            items += StoreExtension(
                guid = guid,
                name = name.take(120),
                summary = localized(entry.opt("summary")).take(300),
                installUrl = installUrl,
                users = entry.optLong("average_daily_users", 0L),
                rating = rating?.takeIf { !it.isNaN() && it > 0.0 }
            )
        }
        return StorePage(items, !root.isNull("next") && root.optString("next").isNotEmpty())
    }

    private fun localized(value: Any?): String = when (value) {
        is String -> value
        is JSONObject -> value.keys().asSequence().firstOrNull()?.let { value.optString(it) }.orEmpty()
        else -> ""
    }.trim()
}
