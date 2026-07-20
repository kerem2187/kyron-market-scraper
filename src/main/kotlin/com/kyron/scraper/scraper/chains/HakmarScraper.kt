package com.kyron.scraper.scraper.chains

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.AbstractCatalogScraper
import org.jsoup.nodes.Document

/**
 * Hakmar. Limited online catalog; barcodes rarely exposed.
 * Included as a skeleton — enable/tune when the site provides product pages.
 */
class HakmarScraper(fetcher: PoliteFetcher) : AbstractCatalogScraper(fetcher) {
    override val slug = "hakmar"
    override val displayName = "Hakmar"
    override val barcodeAvailable = false

    private val base = "https://www.hakmar.com.tr"

    override fun seedUrls(): List<String> = listOf(
        // TODO: add category URLs once the live catalog structure is confirmed.
    )

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-'], a.product-link")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
