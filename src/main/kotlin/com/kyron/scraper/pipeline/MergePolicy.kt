package com.kyron.scraper.pipeline

import com.kyron.scraper.model.Product

object MergePolicy {
    private val sourceRank = mapOf(
        "off" to 100,
        "marketfiyati" to 92,
        "migros" to 80,
        "carrefoursa" to 78,
        "macrocenter" to 76,
        "sok" to 70,
        "a101" to 68,
        "file" to 66,
        "hakmar" to 64,
        "user" to 40,
    )

    fun confidenceFor(source: String, hasBarcode: Boolean): Double {
        val base = (sourceRank[source] ?: 50) / 100.0
        return if (hasBarcode) base else base * 0.5
    }

    /** Deterministic merge: lower-trust records may fill gaps but never erase or replace richer trusted data. */
    fun merge(current: Product?, incoming: Product): Product {
        if (current == null) return incoming.sanitized()
        val cleanIncoming = incoming.sanitized()
        val incomingWins = compare(cleanIncoming, current) > 0
        val primary = if (incomingWins) cleanIncoming else current
        val secondary = if (incomingWins) current else cleanIncoming
        return primary.copy(
            name = primary.name.ifBlank { secondary.name },
            brand = primary.brand ?: secondary.brand,
            imageUrl = primary.imageUrl ?: secondary.imageUrl,
            category = primary.category ?: secondary.category,
            unit = primary.unit ?: secondary.unit,
            sourceUrl = primary.sourceUrl ?: secondary.sourceUrl,
            updatedAt = maxOf(current.updatedAt, incoming.updatedAt),
        )
    }

    private fun compare(a: Product, b: Product): Int {
        val confidence = a.confidence.compareTo(b.confidence)
        if (confidence != 0) return confidence
        val completeness = completeness(a).compareTo(completeness(b))
        if (completeness != 0) return completeness
        if (a.source == b.source) return a.updatedAt.compareTo(b.updatedAt)
        return -a.source.compareTo(b.source) // stable tie-break, independent of arrival order
    }

    private fun completeness(p: Product): Int = listOf(p.brand, p.imageUrl, p.category, p.unit, p.sourceUrl)
        .count { !it.isNullOrBlank() }

    private fun Product.sanitized(): Product = copy(
        name = name.trim().replace(Regex("\\s+"), " "),
        brand = brand.clean(), imageUrl = imageUrl.clean(), category = category.clean(),
        unit = unit.clean(), sourceUrl = sourceUrl.clean(),
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
