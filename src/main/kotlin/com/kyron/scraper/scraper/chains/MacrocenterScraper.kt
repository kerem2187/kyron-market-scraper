package com.kyron.scraper.scraper.chains

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.AbstractCatalogScraper
import org.jsoup.nodes.Document

/** Macrocenter (premium Migros banner). */
class MacrocenterScraper(fetcher: PoliteFetcher) : AbstractCatalogScraper(fetcher) {
    override val slug = "macrocenter"
    override val displayName = "Macrocenter"
    override val barcodeAvailable = true

    private val base = "https://www.macrocenter.com.tr"

    override fun seedUrls(): List<String> = listOf(
        "$base/meyve-sebze-c-2",
        "$base/sut-kahvaltilik-c-4",
        "$base/sarkuteri-c-5",
        "$base/temel-gida-c-6",
        "$base/atistirmalik-c-7",
        "$base/icecek-c-8",
        // TODO: complete category list.
    )

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-']")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
