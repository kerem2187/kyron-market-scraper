package com.kyron.scraper.pipeline

/** Assigns a trust score per source, stored on each record for later reconciliation. */
object MergePolicy {
    // Higher = more trusted.
    private val sourceRank = mapOf(
        "off" to 100,          // Open Food Facts (curated, open license)
        "marketfiyati" to 92,  // TÜBİTAK price platform (6 TR chains, daily)
        "migros" to 80,
        "carrefoursa" to 78,
        "macrocenter" to 76,
        "sok" to 70,
        "a101" to 68,
        "file" to 66,
        "hakmar" to 64,
        "user" to 40,          // crowdsourced (unverified)
    )

    fun confidenceFor(source: String, hasBarcode: Boolean): Double {
        val base = (sourceRank[source] ?: 50) / 100.0
        return if (hasBarcode) base else base * 0.5
    }
}
