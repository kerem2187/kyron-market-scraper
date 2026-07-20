package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.scraper.AbstractBrowserCatalogScraper
import org.jsoup.nodes.Document

/** CarrefourSA (JS-rendered SPA -> browser). */
class CarrefourSaScraper(browser: BrowserRunner) : AbstractBrowserCatalogScraper(browser) {
    override val slug = "carrefoursa"
    override val displayName = "CarrefourSA"
    override val barcodeAvailable = true
    override val waitSelector = "a[href*='/p-']"

    private val base = "https://www.carrefoursa.com"

    override fun seedUrls(): List<String> = listOf(
        "$base/meyve-sebze/c/1015",
        "$base/et-tavuk-balik/c/1016",
        "$base/sut-kahvaltilik/c/1017",
        "$base/temel-gida/c/1018",
        "$base/atistirmalik/c/1020",
        "$base/icecek/c/1021",
        "$base/deterjan-temizlik/c/1022",
        "$base/kisisel-bakim/c/1023",
        // TODO: complete category list.
    )

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='/p-'], a[href$='/p']")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
