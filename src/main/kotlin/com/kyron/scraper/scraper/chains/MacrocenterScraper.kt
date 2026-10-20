package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.config.Config
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.macrocenter.LabelBarcodeDecoder
import com.kyron.scraper.macrocenter.MacrocenterBudgetReached
import com.kyron.scraper.macrocenter.MacrocenterRunBudget
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import com.kyron.scraper.util.ProductExtraction
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.WaitUntilState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.regex.Pattern

private val log = KotlinLogging.logger {}

class MacrocenterScraper(
    private val browser: BrowserRunner,
    private val fetcher: PoliteFetcher,
    private val seeds: List<String> = System.getenv("MACROCENTER_SEED_URLS")
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    private val maxPagesPerSeed: Int = setting("MACROCENTER_MAX_LISTING_PAGES", 3, 1..10),
    private val maxProducts: Int = setting("MACROCENTER_MAX_PRODUCTS", 30, 1..1000),
    private val maxRunMinutes: Int = setting("MACROCENTER_MAX_RUN_MINUTES", 10, 1..30),
) : ChainScraper {
    override val slug = "macrocenter"
    override val displayName = "Macrocenter"
    override val barcodeAvailable = true
    // EXPERIMENTAL: live coverage is not guaranteed.

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val budget = MacrocenterRunBudget(maxProducts, maxRunMinutes * 60_000L)
        var discovered = 0
        var withBarcode = 0
        var errors = 0
        var consecutiveErrors = 0
        var stopReason = "completed"
        val visited = hashSetOf<String>()
        log.info { "[$slug] limits: attempts=$maxProducts minutes=$maxRunMinutes listingPages=$maxPagesPerSeed" }
        try {
            browser.withContext { context ->
                val listing = context.newPage()
                val detail = context.newPage()
                listing.setDefaultTimeout(12_000.0)
                detail.setDefaultTimeout(12_000.0)
                var currentUrl = ""
                var captured: JsonObject? = null
                var captureError: String? = null

                // Fetch via Playwright's request client and fulfill the original browser request.
                // Its body is retained independently of Chromium's Network.getResponseBody cache.
                detail.route(DETAIL_URL) { route ->
                    try {
                        val response = route.fetch(Route.FetchOptions().setTimeout(budget.timeoutMs(12_000)))
                        try {
                            if (response.ok()) captured = MacrocenterProductParser.detail(response.text(), currentUrl)
                            else captureError = "detail HTTP ${response.status()}"
                            route.fulfill(Route.FulfillOptions().setResponse(response))
                        } finally { response.dispose() }
                    } catch (e: Exception) {
                        captureError = e.message
                        runCatching { route.abort() }
                    }
                }

                val categories = if (seeds.isNotEmpty()) seeds.onEach {
                    require(isSeedUrl(it)) { "Invalid Macrocenter category/list URL: $it" }
                } else {
                    listing.navigate(BASE, Page.NavigateOptions().setTimeout(budget.timeoutMs(15_000)))
                    listing.waitForSelector("mc-nav-tree-large-item > a[href*='-c-']",
                        Page.WaitForSelectorOptions().setTimeout(budget.timeoutMs(12_000)))
                    // Packaged goods are more likely to have readable GTIN labels than loose produce.
                    categoryLinks(Jsoup.parse(listing.content(), BASE)).sortedBy {
                        if (it.contains("sut-urunleri") || it.contains("atistirmalik")) 0 else 1
                    }
                }
                check(categories.isNotEmpty()) { "Macrocenter: no categories discovered" }
                for (category in categories) {
                    budget.checkTime()
                    delay(Config.requestDelayMs)
                    try {
                        listing.navigate(category, Page.NavigateOptions().setTimeout(budget.timeoutMs(15_000)))
                        listing.waitForSelector("a[href*='-p-']",
                            Page.WaitForSelectorOptions().setTimeout(budget.timeoutMs(12_000)))
                    } catch (e: Exception) {
                        rethrowStop(e)
                        errors++
                        log.warn { "[$slug] category failed $category: ${e.message}" }
                        continue
                    }
                    for (pageNumber in 1..maxPagesPerSeed) {
                        budget.checkTime()
                        val links = productLinks(Jsoup.parse(listing.content(), category))
                        for (url in links) {
                            if (!visited.add(url)) continue
                            budget.beforeProduct() // Counts attempts, including failed requests and missing barcodes.
                            delay(Config.requestDelayMs)
                            currentUrl = url
                            captured = null
                            captureError = null
                            val product = try {
                                detail.waitForResponse(
                                    { DETAIL_URL.matcher(it.url()).matches() },
                                    Page.WaitForResponseOptions().setTimeout(budget.timeoutMs(15_000)),
                                    { detail.navigate(url, Page.NavigateOptions()
                                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                                        .setTimeout(budget.timeoutMs(15_000))) },
                                )
                                budget.checkTime()
                                val data = captured ?: error(captureError ?: "No matching product detail received")
                                var raw = MacrocenterProductParser.product(data, url) ?: error("Missing product name")
                                // A missing browser body no longer discards the entire product.
                                val fallback = runCatching {
                                    ProductExtraction.fromJsonLd(Jsoup.parse(detail.content(), url), url)
                                }.getOrNull()
                                raw = raw.copy(barcode = raw.barcode ?: fallback?.barcode,
                                    sourceImageUrl = raw.sourceImageUrl ?: fallback?.sourceImageUrl)
                                if (raw.barcode == null) {
                                    val images = (MacrocenterProductParser.labelImages(data) +
                                        MacrocenterProductParser.images(data)).distinct().take(3)
                                    for (imageUrl in images) {
                                        budget.checkTime()
                                        val bytes = try {
                                            fetcher.getBytes(imageUrl, budget.timeoutMs(8_000).toInt(), attempts = 1)
                                        } catch (e: Exception) {
                                            rethrowStop(e)
                                            log.debug { "[$slug] image failed: ${e.message}" }
                                            continue
                                        }
                                        budget.checkTime()
                                        val code = LabelBarcodeDecoder.decode(bytes) ?: continue
                                        raw = raw.copy(barcode = code)
                                        break
                                    }
                                }
                                raw
                            } catch (e: Exception) {
                                rethrowStop(e)
                                errors++
                                consecutiveErrors++
                                log.warn { "[$slug] attempt=${budget.attempts}/$maxProducts product failed $url: ${e.message?.take(300)}" }
                                if (consecutiveErrors >= 5) error("Macrocenter: stopped after 5 consecutive product errors")
                                null
                            }
                            budget.checkTime()
                            if (product != null) {
                                consecutiveErrors = 0
                                discovered++
                                if (product.barcode != null) withBarcode++
                                // Never catch downstream write-budget or Flow cancellation signals.
                                emit(product)
                            }
                            log.info { "[$slug] progress attempts=${budget.attempts}/$maxProducts products=$discovered barcodes=$withBarcode errors=$errors" }
                        }
                        if (pageNumber == maxPagesPerSeed) break
                        budget.checkTime()
                        delay(Config.requestDelayMs)
                        val previous = listing.locator("a[href*='-p-']").count()
                        listing.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                        val advanced = try {
                            listing.waitForFunction(
                                "count => document.querySelectorAll(\"a[href*='-p-']\").length > count", previous,
                                Page.WaitForFunctionOptions().setTimeout(budget.timeoutMs(5_000)),
                            )
                            true
                        } catch (e: Exception) { rethrowStop(e); false }
                        if (!advanced) break
                    }
                }
            }
        } catch (e: MacrocenterBudgetReached) {
            stopReason = e.message ?: "limit"
            log.warn { "[$slug] stopped safely: $stopReason" }
        } catch (e: Exception) {
            stopReason = "error: ${e.message?.take(300)}"
            throw e
        } finally {
            log.info { "[$slug] FINAL attempts=${budget.attempts} products=$discovered barcodes=$withBarcode errors=$errors stop=$stopReason" }
        }
        check(discovered > 0) { "Macrocenter: no products extracted; inspect access, timeouts and selectors" }
    }

    companion object {
        const val BASE = "https://www.macrocenter.com.tr"
        val DETAIL_URL: Pattern = Pattern.compile("https://www\\.macrocenter\\.com\\.tr/rest/products/[0-9]+(?:\\?.*)?")

        private fun setting(name: String, default: Int, range: IntRange): Int =
            (System.getenv(name)?.toIntOrNull() ?: default).also {
                require(it in range) { "$name must be within $range" }
            }

        private fun rethrowStop(e: Exception) {
            if (e is MacrocenterBudgetReached || e is CancellationException) throw e
        }

        fun categoryLinks(doc: Document): List<String> = doc.select("mc-nav-tree-large-item > a[href*='-c-']")
            .map { it.absUrl("href") }.filter { isCatalogUrl(it, "-c-") }.distinct()

        fun productLinks(doc: Document): List<String> = doc.select("a[href*='-p-']")
            .map { it.absUrl("href").substringBefore('?').substringBefore('#') }
            .filter { isCatalogUrl(it, "-p-") }.distinct()

        fun isSeedUrl(value: String): Boolean =
            isCatalogUrl(value, "-c-") || isCatalogUrl(value, "-l-")

        private fun isCatalogUrl(value: String, marker: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "www.macrocenter.com.tr" &&
                uri.userInfo == null && (uri.port == -1 || uri.port == 443) && uri.path.contains(marker)
        }.getOrDefault(false)
    }
}
