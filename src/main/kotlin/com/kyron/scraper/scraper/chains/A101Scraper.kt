package com.kyron.scraper.scraper.chains

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.AbstractCatalogScraper
import org.jsoup.nodes.Document

/** A101 Kapida (mostly server-rendered -> HTTP). */
class A101Scraper(fetcher: PoliteFetcher) : AbstractCatalogScraper(fetcher) {
    override val slug = "a101"
    override val displayName = "A101"
    override val barcodeAvailable = true

    private val base = "https://www.a101.com.tr"

    override fun seedUrls(): List<String> = listOf(
        "$base/kapida/meyve-sebze",
        "$base/kapida/sut-kahvaltilik",
        "$base/kapida/temel-gida",
        "$base/kapida/atistirmalik",
        "$base/kapida/icecek",
        "$base/kapida/deterjan-temizlik",
        "$base/kapida/kisisel-bakim-kozmetik",
        "$base/kapida/bebek",
        // TODO: complete category list.
    )

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-'], a[href*='/kapida/'][href*='/p/']")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()

    override fun nextPage(listing: Document, current: String): String? {
        val next = listing.selectFirst("a[rel=next], a.next, li.pagination-next a, a[aria-label=Sonraki]")
            ?.absUrl("href")
        return next?.takeIf { it.isNotBlank() && it != current }
    }
}
