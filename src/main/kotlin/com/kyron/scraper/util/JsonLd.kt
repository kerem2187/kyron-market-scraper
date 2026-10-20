package com.kyron.scraper.util

import kotlinx.serialization.json.*
import org.jsoup.nodes.Document

object JsonLd {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extractProducts(doc: Document): List<JsonObject> = buildList {
        for (el in doc.select("script[type=application/ld+json]")) {
            val raw = el.data().ifBlank { el.html() }
            runCatching { json.parseToJsonElement(raw) }.getOrNull()?.let { collectProducts(it, this) }
        }
    }

    private fun collectProducts(el: JsonElement, out: MutableList<JsonObject>) {
        when (el) {
            is JsonArray -> el.forEach { collectProducts(it, out) }
            is JsonObject -> {
                val types = when (val type = el["@type"]) {
                    is JsonPrimitive -> listOfNotNull(type.contentOrNull)
                    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    else -> emptyList()
                }
                if (types.any { it.equals("Product", ignoreCase = true) }) out.add(el)
                // WebPage -> mainEntity -> offers -> itemOffered is used by Macrocenter.
                el.values.forEach { collectProducts(it, out) }
            }
            else -> Unit
        }
    }

    fun string(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) stringValue(obj[key])?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun stringValue(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> listOf("name", "url", "contentUrl").firstNotNullOfOrNull { stringValue(value[it]) }
        is JsonArray -> value.firstNotNullOfOrNull { stringValue(it) }
        else -> null
    }
}
