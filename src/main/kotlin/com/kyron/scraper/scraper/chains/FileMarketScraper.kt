package com.kyron.scraper.scraper.chains

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.AbstractCatalogScraper
import org.jsoup.nodes.Document

/**
 * File Market. Online catalog coverage is partial; barcodes are not always
 * exposed. Included for completeness — tune selectors when the site is available.
 */
class FileMarketScraper(fetcher: PoliteFetcher) : AbstractCatalogScraper(fetcher) {
    override val slug = "file"
    override val displayName = "File Market"
    override val barcodeAvailable = false

    private val base = "https://www.file.com.tr"

    override fun seedUrls(): List<String> = error(
        "File: official file.com.tr directs catalog shopping to the mobile app. " +
            "An authorized barcode/product feed is required; fabricated web category URLs are not used.")

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-'], a.product-link")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
