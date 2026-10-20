package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.config.Config
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.macrocenter.LabelBarcodeDecoder
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import com.kyron.scraper.util.ProductExtraction
import com.microsoft.playwright.Page
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

private val log = KotlinLogging.logger {}

/** Render the SPA, capture its own detail response, then decode label barcodes if necessary. */
class MacrocenterScraper(
    private val browser: BrowserRunner,
    private val fetcher: PoliteFetcher,
    private val seeds: List<String> = System.getenv("MACROCENTER_SEED_URLS")
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    private val maxPagesPerSeed: Int = 50,
) : ChainScraper {
    override val slug = "macrocenter"
    override val displayName = "Macrocenter"
    override val barcodeAvailable = true
    // Deliberately EXPERIMENTAL until real unattended runs demonstrate coverage.

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val categories = if (seeds.isNotEmpty()) seeds.onEach {
            require(isCatalogUrl(it, "-c-")) { "Invalid Macrocenter category URL: $it" }
        } else browser.withPage { page ->
            page.navigate(BASE)
            page.waitForSelector("mc-nav-tree-large-item > a[href*='-c-']")
            categoryLinks(Jsoup.parse(page.content(), BASE))
        }
        check(categories.isNotEmpty()) { "Macrocenter: no categories discovered (blocked page or changed menu)" }
        val visited = hashSetOf<String>()
        var discovered = 0
        var withBarcode = 0
        for (category in categories) {
            delay(Config.requestDelayMs)
            val links = runCatching { collectLinks(category) }.getOrElse {
                log.warn { "[$slug] category failed $category: ${it.message}" }
                emptyList()
            }
            for (url in links) {
                if (!visited.add(url)) continue
                delay(Config.requestDelayMs)
                val product = runCatching { scrapeProduct(url) }.getOrElse {
                    log.warn { "[$slug] product failed $url: ${it.message}" }
                    null
                } ?: continue
                discovered++
                if (product.barcode != null) withBarcode++
                // Outside runCatching: downstream budget/cancellation signals must propagate.
                emit(product)
            }
        }
        check(discovered > 0) { "Macrocenter: no products extracted; inspect access and selectors" }
        log.info { "[$slug] products=$discovered withBarcode=$withBarcode missingBarcode=${discovered - withBarcode}" }
    }

    private fun collectLinks(url: String): List<String> = browser.withPage { page ->
        page.navigate(url)
        page.waitForSelector("a[href*='-p-']")
        val links = linkedSetOf<String>()
        repeat(maxPagesPerSeed) {
            links.addAll(productLinks(Jsoup.parse(page.content(), url)))
            val previous = page.locator("a[href*='-p-']").count()
            // Macrocenter uses infinite scrolling rather than rel=next links.
            page.waitForTimeout(Config.requestDelayMs.toDouble())
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
            val advanced = runCatching {
                page.waitForFunction(
                    "count => document.querySelectorAll(\"a[href*='-p-']\").length > count",
                    previous, Page.WaitForFunctionOptions().setTimeout(8_000.0),
                )
            }.isSuccess
            if (!advanced) return@withPage links.toList()
        }
        links.addAll(productLinks(Jsoup.parse(page.content(), url)))
        log.warn { "[$slug] listing page cap reached: $url; coverage may be incomplete" }
        links.toList()
    }

    private suspend fun scrapeProduct(url: String): RawProduct? {
        val (data, fallback) = browser.withPage { page ->
            val response = runCatching {
                page.waitForResponse(
                    { response ->
                        val uri = URI(response.url())
                        uri.host == "www.macrocenter.com.tr" &&
                            uri.path.matches(Regex("/rest/products/[0-9]+"))
                    },
                    Page.WaitForResponseOptions().setTimeout(25_000.0),
                    { page.navigate(url) },
                )
            }.getOrNull()
            val detail = response?.takeIf { it.ok() }
                ?.let { MacrocenterProductParser.detail(it.text(), url) }
            runCatching { page.waitForSelector("h1") }
            val doc = Jsoup.parse(page.content(), url)
            detail to (ProductExtraction.fromJsonLd(doc, url) ?: ProductExtraction.fromMeta(doc, url))
        }
        var product = data?.let { MacrocenterProductParser.product(it, url) } ?: fallback ?: return null
        if (product.barcode == null) product = product.copy(barcode = fallback?.barcode)
        if (product.sourceImageUrl == null) product = product.copy(sourceImageUrl = fallback?.sourceImageUrl)
        if (product.barcode != null || data == null) return product

        // Only the current product's official label/photos; never recommended product images.
        val candidates = (MacrocenterProductParser.labelImages(data) + MacrocenterProductParser.images(data))
            .distinct().take(6)
        for (imageUrl in candidates) {
            val bytes = try { fetcher.getBytes(imageUrl) } catch (e: Exception) {
                log.debug { "[$slug] label fetch failed: ${e.message}" }
                continue
            }
            val barcode = LabelBarcodeDecoder.decode(bytes) ?: continue
            return product.copy(barcode = barcode)
        }
        return product // The pipeline observes/skips missing GTINs; no synthetic identifiers.
    }

    companion object {
        const val BASE = "https://www.macrocenter.com.tr"

        fun categoryLinks(doc: Document): List<String> = doc.select("mc-nav-tree-large-item > a[href*='-c-']")
            .map { it.absUrl("href") }.filter { isCatalogUrl(it, "-c-") }.distinct()

        fun productLinks(doc: Document): List<String> = doc.select("a[href*='-p-']")
            .map { it.absUrl("href").substringBefore('?').substringBefore('#') }
            .filter { isCatalogUrl(it, "-p-") }.distinct()

        private fun isCatalogUrl(value: String, marker: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "www.macrocenter.com.tr" &&
                uri.userInfo == null && (uri.port == -1 || uri.port == 443) && uri.path.contains(marker)
        }.getOrDefault(false)
    }
}
