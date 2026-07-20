package com.kyron.scraper.scraper

import com.kyron.scraper.model.RawProduct

/** A single retail chain source. */
interface ChainScraper {
    /** Unique slug, e.g. "migros". */
    val slug: String

    /** Human-readable name. */
    val displayName: String

    /** Whether this chain typically exposes product barcodes online. */
    val barcodeAvailable: Boolean

    /**
     * Crawls the chain and emits raw products one at a time.
     * Implementations must be polite (rate-limited) and resilient:
     * a failure on one page must not abort the whole run.
     */
    suspend fun scrape(emit: suspend (RawProduct) -> Unit)
}
