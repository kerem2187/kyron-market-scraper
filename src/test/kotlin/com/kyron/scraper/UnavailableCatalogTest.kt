package com.kyron.scraper

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.chains.FileMarketScraper
import com.kyron.scraper.scraper.chains.HakmarScraper
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class UnavailableCatalogTest {
    @Test fun hakmarMustNotSilentlyReturnEmptyOrSubstituteExpress() = runBlocking {
        val e = assertFailsWith<IllegalStateException> { HakmarScraper(PoliteFetcher()).scrape {} }
        assertTrue(e.message!!.contains("Hakmar Express is a different retailer"))
    }
    @Test fun fileMustNotCrawlFabricatedDomainAndCategories() = runBlocking {
        val e = assertFailsWith<IllegalStateException> { FileMarketScraper(PoliteFetcher()).scrape {} }
        assertTrue(e.message!!.contains("authorized barcode/product feed"))
    }
}
