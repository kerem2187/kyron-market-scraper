package com.kyron.scraper.scraper.chains

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.AbstractCatalogScraper
import org.jsoup.nodes.Document

/** SOK Market (server-rendered catalog -> HTTP). */
class SokScraper(fetcher: PoliteFetcher) : AbstractCatalogScraper(fetcher) {
    override val slug = "sok"
    override val displayName = "\u015eOK Market"
    override val barcodeAvailable = true

    private val base = "https://www.sokmarket.com.tr"

    override fun seedUrls(): List<String> = listOf(
        "$base/meyve-sebze-c-1",
        "$base/sut-kahvaltilik-c-2",
        "$base/temel-gida-c-3",
        "$base/atistirmalik-c-4",
        "$base/icecek-c-5",
        "$base/temizlik-c-6",
        "$base/kisisel-bakim-c-7",
        // TODO: complete category list.
    )

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-']")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()

    override fun nextPage(listing: Document, current: String): String? {
        val next = listing.selectFirst("a[rel=next], a.next, a[aria-label=Sonraki]")?.absUrl("href")
        return next?.takeIf { it.isNotBlank() && it != current }
    }
}
