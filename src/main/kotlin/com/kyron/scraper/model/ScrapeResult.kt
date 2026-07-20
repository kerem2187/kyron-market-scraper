package com.kyron.scraper.model

import kotlinx.serialization.Serializable

/** Raw, unnormalized product emitted by a chain scraper. */
data class RawProduct(
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val sourceImageUrl: String? = null,
    val category: String? = null,
    val unit: String? = null,
    val sourceUrl: String? = null,
)

/** Per-chain run report, returned by the scrape endpoint. */
@Serializable
data class ChainScrapeReport(
    val chain: String,
    val discovered: Int,
    val withBarcode: Int,
    val upserted: Int,
    val skipped: Int,
    val errors: Int,
    val durationMs: Long,
    val failures: List<String> = emptyList(),
)
