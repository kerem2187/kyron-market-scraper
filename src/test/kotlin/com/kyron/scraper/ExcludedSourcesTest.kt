package com.kyron.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.ScraperRegistry
import kotlin.test.*

class ExcludedSourcesTest {
    @Test fun excludedRetailersCannotBeSelectedExplicitly() {
        BrowserRunner().use { browser ->
            val registry = ScraperRegistry(PoliteFetcher(), browser)
            for (source in listOf("file", "hakmar", "a101", "carrefoursa")) {
                val e = assertFailsWith<IllegalArgumentException> { registry.resolve(listOf(source)) }
                assertTrue(e.message!!.contains("Disabled source(s)"))
            }
        }
    }
    @Test fun remainingFourSourcesAreStillSelectable() {
        BrowserRunner().use { browser ->
            val expected = setOf("macrocenter", "migros", "sok", "off")
            val selected = ScraperRegistry(PoliteFetcher(), browser).resolve(expected.toList())
            assertEquals(expected, selected.map { it.slug }.toSet())
            assertTrue(selected.none { it.slug in setOf("file", "hakmar", "a101", "carrefoursa") })
        }
    }
}
