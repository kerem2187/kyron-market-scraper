package com.kyron.scraper.util

import kotlinx.serialization.json.*
import org.jsoup.nodes.Document

/** Reads serialized JSON data only; never evaluates scripts or copies session headers. */
object NextFlight {
    private val json = Json { ignoreUnknownKeys = true }

    fun payload(doc: Document): String = buildString {
        for (script in doc.select("script")) {
            val text = script.data().trim()
            if (!text.startsWith("self.__next_f.push(")) continue
            val array = runCatching {
                json.parseToJsonElement(text.removePrefix("self.__next_f.push(").substringBeforeLast(')')).jsonArray
            }.getOrNull() ?: continue
            (array.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.let { append(it) }
        }
    }

    fun objectProperty(payload: String, name: String): JsonObject? {
        val marker = "\"$name\":"
        var offset = 0
        while (offset < payload.length) {
            val at = payload.indexOf(marker, offset)
            if (at < 0) return null
            var begin = at + marker.length
            while (begin < payload.length && payload[begin].isWhitespace()) begin++
            offset = begin + 1
            if (payload.getOrNull(begin) != '{') continue
            var depth = 0; var quoted = false; var escaped = false
            for (i in begin until payload.length) {
                val c = payload[i]
                if (quoted) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') quoted = false
                } else when (c) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return runCatching {
                        json.parseToJsonElement(payload.substring(begin, i + 1)).jsonObject
                    }.getOrNull() }
                }
            }
        }
        return null
    }
}
