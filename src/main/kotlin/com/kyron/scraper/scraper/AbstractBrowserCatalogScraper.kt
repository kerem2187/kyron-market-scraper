package com.kyron.scraper.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductExtraction
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val log = KotlinLogging.logger {}

/**
 * Base class for JS-rendered SPA catalogs (Migros, CarrefourSA, ...).
 * Uses headless Chromium to render pages, then parses with Jsoup.
 */
abstract class AbstractBrowserCatalogScraper(
    protected val browser: BrowserRunner,
) : ChainScraper {

    protected abstract fun seedUrls(): List<String>
    protected abstract fun productLinks(listing: Document): List<String>

    /** CSS selector to wait for before reading rendered content. */
    protected open val waitSelector: String? = null

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val visited = HashSet<String>()
        var extracted = 0
        val deadline = System.currentTimeMillis() + com.kyron.scraper.config.Config.maxRunMinutes * 60_000
        fun checkTime() { check(System.currentTimeMillis() < deadline) { "Source run-time budget reached" } }
        for (seed in seedUrls()) {
            checkTime()
            val links = runCatching { collectLinks(seed) }
                .onFailure { if (it is CancellationException) throw it; log.warn { "[$slug] seed failed $seed: ${it.message}" } }
                .getOrDefault(emptyList())
            for (link in links) {
                checkTime()
                if (!visited.add(link)) continue
                val product = runCatching { scrapeProduct(link) }
                    .onFailure { if (it is CancellationException) throw it; log.warn { "[$slug] product failed $link: ${it.message}" } }
                    .getOrNull()
                if (product != null) { extracted++; emit(product) }
            }
        }
        check(extracted > 0) { "[$slug] No products extracted; check HTTP access, catalog URLs and selectors" }
    }

    private fun collectLinks(seed: String): List<String> = browser.withPage { page ->
        page.navigate(seed)
        waitSelector?.let { runCatching { page.waitForSelector(it) } }
        productLinks(Jsoup.parse(page.content(), seed))
    }

    protected open fun scrapeProduct(url: String): RawProduct? = browser.withPage { page ->
        page.navigate(url)
        runCatching { page.waitForSelector("h1", com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(12_000.0)) }
        val doc = Jsoup.parse(page.content(), url)
        ProductExtraction.fromJsonLd(doc, url) ?: ProductExtraction.fromMeta(doc, url)
    }
}
