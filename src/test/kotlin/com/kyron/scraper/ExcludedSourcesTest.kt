package com.kyron.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.ScraperRegistry
import kotlin.test.*

class ExcludedSourcesTest {
    @Test fun excludedRetailersCannotBeSelectedExplicitly() {
        BrowserRunner().use { browser ->
            val registry = ScraperRegistry(PoliteFetcher(), browser)
            for (source in listOf("file", "hakmar")) {
                val e = assertFailsWith<IllegalArgumentException> { registry.resolve(listOf(source)) }
                assertTrue(e.message!!.contains("Disabled source(s)"))
            }
        }
    }
    @Test fun remainingSixSourcesAreStillSelectable() {
        BrowserRunner().use { browser ->
            val expected = setOf("macrocenter", "migros", "carrefoursa", "sok", "a101", "off")
            val selected = ScraperRegistry(PoliteFetcher(), browser).resolve(expected.toList())
            assertEquals(expected, selected.map { it.slug }.toSet())
            assertTrue(selected.none { it.slug == "file" || it.slug == "hakmar" })
        }
    }
}
