package com.kyron.scraper.pipeline

import com.kyron.scraper.model.Product
import com.kyron.scraper.util.ProductQuality

object MergePolicy {
  private val sourceRank = mapOf(
    "migros" to 100,
    "macrocenter" to 98,
    "marketfiyati" to 92,
    "off" to 85,
    "obf" to 82,
    "carrefoursa" to 78,
    "sok" to 75,
    "opf" to 74,
    "opff" to 72,
    "a101" to 68,
    "file" to 66,
    "hakmar" to 64,
    "user" to 40,
  )

  private val unitPattern = Regex(
    "\\b[0-9]+(?:[.,][0-9]+)?\\s*(?:g|gr|kg|ml|l|lt|cl|adet|li|lı|lu|lü)\\b",
    RegexOption.IGNORE_CASE,
  )

  fun confidenceFor(source: String, hasBarcode: Boolean): Double {
    val base = (sourceRank[source] ?: 50) / 100.0
    return if (hasBarcode) base else base * 0.5
  }

  /** Evaluates structural quality of a product title independent of barcode presence (Firecrawl quality principle). */
  fun nameQualityScore(name: String): Int {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    if (!ProductQuality.isMeaningfulName(clean)) return 0
    var score = 1
    if (clean.length >= 8) score += 1
    if (clean.split(" ").count { it.length >= 2 } >= 2) score += 2
    if (unitPattern.containsMatchIn(clean)) score += 2
    return score
  }

  /** Deterministic merge: lower-trust or low-quality records fill gaps but never overwrite richer verified data. */
  fun merge(current: Product?, incoming: Product): Product {
    if (current == null) return incoming.sanitized()
    val cleanIncoming = incoming.sanitized()
    val incomingWins = compare(cleanIncoming, current) > 0
    val primary = if (incomingWins) cleanIncoming else current
    val secondary = if (incomingWins) current else cleanIncoming
    val primaryScore = nameQualityScore(primary.name)
    val secondaryScore = nameQualityScore(secondary.name)
    val bestName = when {
      primary.name.isBlank() || primaryScore == 0 -> secondary.name.ifBlank { primary.name }
      secondaryScore >= primaryScore + 2 && primary.source !in setOf("migros", "macrocenter") -> secondary.name
      else -> primary.name
    }
    return primary.copy(
      name = bestName,
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
    val nameQuality = nameQualityScore(a.name).compareTo(nameQualityScore(b.name))
    if (nameQuality != 0) return nameQuality
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
