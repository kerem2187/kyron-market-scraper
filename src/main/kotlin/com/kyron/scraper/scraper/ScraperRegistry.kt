package com.kyron.scraper.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.marketfiyati.MarketFiyatiImporter
import com.kyron.scraper.off.OpenFoodFactsImporter
import com.kyron.scraper.scraper.chains.A101Scraper
import com.kyron.scraper.scraper.chains.CarrefourSaScraper
import com.kyron.scraper.scraper.chains.FileMarketScraper
import com.kyron.scraper.scraper.chains.HakmarScraper
import com.kyron.scraper.scraper.chains.MacrocenterScraper
import com.kyron.scraper.scraper.chains.MigrosScraper
import com.kyron.scraper.scraper.chains.SokScraper

/** Central catalog of all available sources. */
class ScraperRegistry(
    fetcher: PoliteFetcher,
    browser: BrowserRunner,
) {
    private val all: List<ChainScraper> = listOf(
        OpenFoodFactsImporter(),        // off          — global base layer
        MarketFiyatiImporter(),         // marketfiyati — TÜBİTAK, 6 TR chains, daily, no key
        MigrosScraper(browser),         // migros
        CarrefourSaScraper(browser),    // carrefoursa
        MacrocenterScraper(fetcher),    // macrocenter
        SokScraper(fetcher),            // sok
        A101Scraper(fetcher),           // a101
        FileMarketScraper(fetcher),     // file
        HakmarScraper(fetcher),         // hakmar
    )

    /** Resolves the enabled scrapers. Pass ["all"] (or empty) for everything. */
    fun resolve(enabled: List<String>): List<ChainScraper> =
        if (enabled.isEmpty() || enabled.contains("all")) all
        else all.filter { it.slug in enabled }

    fun slugs(): List<String> = all.map { it.slug }
}
