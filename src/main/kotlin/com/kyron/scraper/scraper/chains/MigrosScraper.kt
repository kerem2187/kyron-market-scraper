package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.scraper.AbstractBrowserCatalogScraper
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.macrocenter.LabelBarcodeDecoder
import com.kyron.scraper.util.ProductExtraction
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.WaitUntilState
import com.microsoft.playwright.options.RequestOptions
import kotlinx.serialization.json.JsonObject
import java.util.regex.Pattern

/**
 * Migros Sanal Market (JS-rendered SPA -> browser).
 * Selectors verified against the site as of writing; re-check periodically.
 */
class MigrosScraper(browser: BrowserRunner) : AbstractBrowserCatalogScraper(browser) {
    override val slug = "migros"
    override val displayName = "Migros Sanal Market"
    override val barcodeAvailable = true
    override val waitSelector = "a[href*='-p-']"

    private val base = "https://www.migros.com.tr"

    override fun seedUrls(): List<String> = listOf(
        "$base/uzun-omurlu-sut-c-40a",
        "$base/atistirmalik-c-113fb",
    )

    override fun catalogSeeds(): List<String> {
        val discovered = runCatching { browser.withPage { page ->
            page.navigate(base)
            page.waitForSelector("a[href*='-c-']", Page.WaitForSelectorOptions().setTimeout(20_000.0))
            com.kyron.scraper.util.CatalogUrls.links(Jsoup.parse(page.content(), base), "www.migros.com.tr", "-c-")
        } }.getOrElse {
            println("CATALOG_COVERAGE source=$slug categoryDiscoveryFailed=true fallback=true fullCatalogGuaranteed=false")
            emptyList()
        }
        println("CATALOG_COVERAGE source=$slug categories=${discovered.size} fullCatalogGuaranteed=false")
        return (seedUrls() + discovered).distinct()
    }

    override fun scrapeProduct(url: String): RawProduct? = browser.withPage { page ->
        var captured: JsonObject? = null
        val endpoint = Pattern.compile("https://www\\.migros\\.com\\.tr/rest/products/screens/[0-9]+(?:\\?.*)?")
        page.route(endpoint) { route ->
            val response = route.fetch(Route.FetchOptions().setTimeout(15_000.0))
            try {
                if (response.ok()) {
                    MacrocenterProductParser.detail(response.text(), url)?.let { captured = it }
                }
                route.fulfill(Route.FulfillOptions().setResponse(response))
            } finally { response.dispose() }
        }
        page.waitForResponse({ endpoint.matcher(it.url()).matches() },
            Page.WaitForResponseOptions().setTimeout(20_000.0)) {
            page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(20_000.0))
        }
        val data = captured ?: error("Migros: no matching product API detail for $url")
        var raw = MacrocenterProductParser.product(data, url) ?: error("Migros: missing product name")
        val fallback = ProductExtraction.fromJsonLd(Jsoup.parse(page.content(), url), url)
        raw = raw.copy(barcode = raw.barcode ?: fallback?.barcode,
            sourceImageUrl = raw.sourceImageUrl ?: fallback?.sourceImageUrl)
        if (raw.barcode == null) {
            val images = (MacrocenterProductParser.labelImages(data) + MacrocenterProductParser.images(data))
                .distinct().take(3)
            for (image in images) {
                val code = runCatching {
                    val response = page.request().get(image, RequestOptions.create().setTimeout(8_000.0))
                    try { if (response.ok()) LabelBarcodeDecoder.decode(response.body()) else null }
                    finally { response.dispose() }
                }.getOrNull() ?: continue
                raw = raw.copy(barcode = code)
                break
            }
        }
        raw
    }

    override fun productLinks(listing: Document): List<String> =
        com.kyron.scraper.util.CatalogUrls.links(listing, "www.migros.com.tr", "-p-")

}
