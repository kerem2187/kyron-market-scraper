package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.scraper.AbstractBrowserCatalogScraper
import org.jsoup.nodes.Document

/**
 * Migros Sanal Market (JS-rendered SPA -> browser).
 * Selectors verified against the site as of writing; re-check periodically.
 */
class MigrosScraper(browser: BrowserRunner) : AbstractBrowserCatalogScraper(browser) {
    override val slug = "migros"
    override val displayName = "Migros Sanal Market"
    override val barcodeAvailable = true
    override val waitSelector = "a[href*='-p-']"

    private val base = "https://www.migros.com.tr"

    override fun seedUrls(): List<String> = listOf(
        "$base/meyve-sebze-c-2",
        "$base/et-tavuk-balik-c-3",
        "$base/sut-kahvaltilik-c-4",
        "$base/temel-gida-c-5b",
        "$base/meze-hazir-yemek-donuk-c-6",
        "$base/atistirmalik-c-7",
        "$base/icecek-c-8",
        "$base/deterjan-temizlik-c-9",
        "$base/kisisel-bakim-kozmetik-c-10",
        "$base/bebek-c-11",
        // TODO: complete category list from the site's category menu.
    )

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-']")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
