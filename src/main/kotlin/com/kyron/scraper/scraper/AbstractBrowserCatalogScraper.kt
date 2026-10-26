package com.kyron.scraper.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductExtraction
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState

private val log = KotlinLogging.logger {}

/**
 * Base class for JS-rendered SPA catalogs (Migros, CarrefourSA, ...).
 * Uses headless Chromium to render pages, then parses with Jsoup.
 */
abstract class AbstractBrowserCatalogScraper(
    protected val browser: BrowserRunner,
) : ChainScraper {

    protected abstract fun seedUrls(): List<String>
    protected open fun catalogSeeds(): List<String> = seedUrls()
    protected abstract fun productLinks(listing: Document): List<String>

    /** CSS selector to wait for before reading rendered content. */
    protected open val waitSelector: String? = null

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val visited = HashSet<String>()
        var extracted = 0
        val deadline = System.currentTimeMillis() + com.kyron.scraper.config.Config.maxRunMinutes * 60_000
        fun checkTime() { check(System.currentTimeMillis() < deadline) { "Source run-time budget reached" } }
        for (seed in catalogSeeds()) {
            checkTime()
            val links = runCatching { collectLinks(seed) }
                .onFailure { if (it is CancellationException) throw it; log.warn { "[$slug] seed failed $seed: ${it.message}" }; println("CATALOG_FAILURE source=$slug seed=$seed reason=${it.message?.take(500)}") }
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
        val response = page.navigate(seed, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
        val status = response?.status() ?: 0
        check(status in 200..299) {
            "Catalog access failed: HTTP $status title=${page.title().take(120)}"
        }
        waitSelector?.let { selector ->
            runCatching { page.waitForSelector(selector,
                Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(45_000.0)) }
        }
        val maxPasses = (System.getenv("CATALOG_MAX_LISTING_PAGES")?.toIntOrNull() ?: 10).also { require(it in 1..50) }
        var passes = 1
        var stable = 0
        val found = linkedSetOf<String>()
        while (true) {
            found.addAll(productLinks(Jsoup.parse(page.content(), page.url())))
            if (passes >= maxPasses || stable >= 2 || waitSelector == null) break
            val previous = page.locator(waitSelector!!).count()
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
            val advanced = runCatching { page.waitForFunction(
                "arg => document.querySelectorAll(arg.selector).length > arg.count",
                mapOf("selector" to waitSelector, "count" to previous),
                Page.WaitForFunctionOptions().setTimeout(5_000.0)) }.isSuccess
            if (advanced) stable = 0 else stable++
            passes++
        }
        val links = found.toList()
        println("CATALOG_COVERAGE source=$slug listingLinks=${links.size} scrollPasses=$passes capped=${passes >= maxPasses} fullCatalogGuaranteed=false")
        // Only bounded public diagnostics: never dump HTML, cookies or streamed session headers.
        println("CATALOG_DISCOVERY source=$slug status=$status links=${links.size} title=${page.title().take(120)}")
        check(links.isNotEmpty()) {
            "No product links at $seed; HTTP $status title=${page.title().take(120)} selector=$waitSelector"
        }
        links
    }

    protected open fun scrapeProduct(url: String): RawProduct? = browser.withPage { page ->
        page.navigate(url)
        runCatching { page.waitForSelector("h1", com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(12_000.0)) }
        val doc = Jsoup.parse(page.content(), url)
        ProductExtraction.fromJsonLd(doc, url) ?: ProductExtraction.fromMeta(doc, url)
    }
}
