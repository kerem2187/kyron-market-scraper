package com.kyron.scraper.scraper

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductExtraction
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import org.jsoup.nodes.Document

private val log = KotlinLogging.logger {}

/**
 * Base class for server-rendered (HTTP + Jsoup) chain catalogs.
 * Subclasses provide seed URLs, product-link selection, and pagination.
 */
abstract class AbstractCatalogScraper(
    protected val fetcher: PoliteFetcher,
) : ChainScraper {

    /** Seed category/listing URLs to crawl. */
    protected abstract fun seedUrls(): List<String>

    /** Extract product detail URLs from a listing page. */
    protected abstract fun productLinks(listing: Document): List<String>

    /** Extract the next listing page URL, or null when done. */
    protected open fun nextPage(listing: Document, current: String): String? = null

    /** Safety cap on listing pages per seed. */
    protected open val maxPagesPerSeed: Int = 50

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val visited = HashSet<String>()
        var extracted = 0
        val deadline = System.currentTimeMillis() + com.kyron.scraper.config.Config.maxRunMinutes * 60_000
        fun checkTime() { check(System.currentTimeMillis() < deadline) { "Source run-time budget reached" } }
        for (seed in seedUrls()) {
            checkTime()
            var pageUrl: String? = seed
            var pages = 0
            while (pageUrl != null && pages < maxPagesPerSeed) {
                checkTime()
                val current = pageUrl
                val listing = runCatching { fetcher.getHtml(current) }
                    .onFailure { if (it is CancellationException) throw it; log.warn { "[$slug] listing failed $current: ${it.message}" } }
                    .getOrNull() ?: break
                for (link in productLinks(listing)) {
                    checkTime()
                    if (!visited.add(link)) continue
                    val product = runCatching { scrapeProduct(link) }
                        .onFailure { if (it is CancellationException) throw it; log.warn { "[$slug] product failed $link: ${it.message}" } }
                        .getOrNull()
                    if (product != null) { extracted++; emit(product) }
                }
                pageUrl = nextPage(listing, current)
                pages++
            }
        }
        check(extracted > 0) { "[$slug] No products extracted; check HTTP access, catalog URLs and selectors" }
    }

    protected open suspend fun scrapeProduct(url: String): RawProduct? {
        val doc = fetcher.getHtml(url)
        return ProductExtraction.fromJsonLd(doc, url) ?: ProductExtraction.fromMeta(doc, url)
    }
}
