package com.kyron.scraper.scraper

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductExtraction
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
        for (seed in seedUrls()) {
            var pageUrl: String? = seed
            var pages = 0
            while (pageUrl != null && pages < maxPagesPerSeed) {
                val current = pageUrl
                val listing = runCatching { fetcher.getHtml(current) }
                    .onFailure { log.warn { "[$slug] listing failed $current: ${it.message}" } }
                    .getOrNull() ?: break
                for (link in productLinks(listing)) {
                    if (!visited.add(link)) continue
                    val product = runCatching { scrapeProduct(link) }
                        .onFailure { log.warn { "[$slug] product failed $link: ${it.message}" } }
                        .getOrNull()
                    if (product != null) emit(product)
                }
                pageUrl = nextPage(listing, current)
                pages++
            }
        }
    }

    protected open suspend fun scrapeProduct(url: String): RawProduct? {
        val doc = fetcher.getHtml(url)
        return ProductExtraction.fromJsonLd(doc, url) ?: ProductExtraction.fromMeta(doc, url)
    }
}
