package com.kyron.scraper.scraper

import com.kyron.scraper.model.RawProduct

enum class SourceStatus { VERIFIED, EXPERIMENTAL, DISABLED }

interface ChainScraper {
    val slug: String
    val displayName: String
    val barcodeAvailable: Boolean
    val status: SourceStatus get() = SourceStatus.EXPERIMENTAL
    suspend fun scrape(emit: suspend (RawProduct) -> Unit)
}
