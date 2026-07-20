package com.kyron.scraper.util

import kotlinx.serialization.json.*
import org.jsoup.nodes.Document

/**
 * Extracts schema.org Product objects from JSON-LD <script> tags.
 * This is the most robust extraction path because most e-commerce sites embed
 * structured data (name, brand, image, gtin13) even when the DOM changes.
 */
object JsonLd {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extractProducts(doc: Document): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        for (el in doc.select("script[type=application/ld+json]")) {
            val raw = el.data().ifBlank { el.html() }
            val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: continue
            collectProducts(parsed, results)
        }
        return results
    }

    private fun collectProducts(el: JsonElement, out: MutableList<JsonObject>) {
        when (el) {
            is JsonArray -> el.forEach { collectProducts(it, out) }
            is JsonObject -> {
                val typeStr = when (val type = el["@type"]) {
                    is JsonPrimitive -> type.contentOrNull
                    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString()
                    else -> null
                }
                if (typeStr != null && typeStr.contains("Product", ignoreCase = true)) out.add(el)
                el["@graph"]?.let { collectProducts(it, out) }
            }
            else -> {}
        }
    }

    /** Reads a string value, resolving common nested shapes (e.g. brand.name, arrays). */
    fun string(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            when (val v = obj[k]) {
                is JsonPrimitive -> v.contentOrNull?.let { if (it.isNotBlank()) return it }
                is JsonObject -> v["name"]?.jsonPrimitive?.contentOrNull?.let { return it }
                is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull?.let { return it }
                else -> {}
            }
        }
        return null
    }
}
