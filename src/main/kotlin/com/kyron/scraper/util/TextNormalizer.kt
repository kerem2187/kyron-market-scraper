package com.kyron.scraper.util

/** Product name normalization helpers. */
object TextNormalizer {
    private val whitespace = Regex("\\s+")

    /** Collapses whitespace and trims. */
    fun cleanName(raw: String): String = raw.replace(whitespace, " ").trim()

    /** Lowercased, punctuation-stripped key useful for fuzzy dedup / grouping. */
    fun canonicalKey(name: String): String =
        name.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd} ]"), "")
            .replace(whitespace, " ")
            .trim()
}
