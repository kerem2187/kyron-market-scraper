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
    override val displayName = "Hakmar Alışveriş Merkezleri"
    override val barcodeAvailable = false

    private val base = "https://www.hakmar.com.tr"

    override fun seedUrls(): List<String> = error(
        "Hakmar Alisveris Merkezleri (hakmar.com.tr): public discount leaflets do not provide " +
            "a structured barcode catalog. An authorized product feed is required. " +
            "Hakmar Express is a different retailer and must not be substituted.")

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-'], a.product-link")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
