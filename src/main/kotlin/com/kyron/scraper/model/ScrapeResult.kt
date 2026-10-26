package com.kyron.scraper.model

import kotlinx.serialization.Serializable

data class RawProduct(
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val sourceImageUrl: String? = null,
    val category: String? = null,
    val unit: String? = null,
    val sourceUrl: String? = null,
)

@Serializable
data class ChainScrapeReport(
    val chain: String,
    val discovered: Int,
    val withBarcode: Int,
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
    val skipped: Int,
    val errors: Int,
    val stoppedByBudget: Boolean,
    val durationMs: Long,
    val failures: List<String> = emptyList(),
) {
    val written: Int get() = inserted + updated
    /** Complete records include unchanged products; partial fatal failures never pass. */
    val successful: Boolean get() = errors == 0 && withBarcode > 0
}
