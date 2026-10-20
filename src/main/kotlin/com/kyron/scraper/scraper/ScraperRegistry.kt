package com.kyron.scraper.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.marketfiyati.MarketFiyatiImporter
import com.kyron.scraper.off.OpenFoodFactsImporter
import com.kyron.scraper.scraper.chains.*

class ScraperRegistry(fetcher: PoliteFetcher, browser: BrowserRunner) {
    private val all: List<ChainScraper> = listOf(
        OpenFoodFactsImporter(), MarketFiyatiImporter(), MigrosScraper(browser),
        CarrefourSaScraper(browser), MacrocenterScraper(browser, fetcher), SokScraper(fetcher),
        A101Scraper(fetcher), FileMarketScraper(fetcher), HakmarScraper(fetcher),
    )

    fun resolve(enabled: List<String>): List<ChainScraper> {
        val requested = enabled.ifEmpty { listOf("off") }
        if (requested.contains("all")) return all.filter { it.status == SourceStatus.VERIFIED }
        val unknown = requested - all.map { it.slug }.toSet()
        require(unknown.isEmpty()) { "Unknown source(s): ${unknown.joinToString()}; available=${all.joinToString { it.slug }}" }
        val selected = all.filter { it.slug in requested }
        val disabled = selected.filter { it.status == SourceStatus.DISABLED }
        require(disabled.isEmpty()) { "Disabled source(s): ${disabled.joinToString { it.slug }}" }
        return selected
    }

    fun slugs(): List<String> = all.map { it.slug }
}
