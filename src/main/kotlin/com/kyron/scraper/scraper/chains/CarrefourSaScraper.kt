package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.scraper.SourceStatus
import com.kyron.scraper.scraper.AbstractBrowserCatalogScraper
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductExtraction
import com.kyron.scraper.macrocenter.LabelBarcodeDecoder
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.RequestOptions

/** CarrefourSA (JS-rendered SPA -> browser). */
class CarrefourSaScraper(browser: BrowserRunner) : AbstractBrowserCatalogScraper(browser) {
    override val slug = "carrefoursa"
    override val displayName = "CarrefourSA"
    override val barcodeAvailable = true
    // Excluded by user: live GitHub Actions access returns HTTP 403; no authorized API.
    override val status = SourceStatus.DISABLED
    override val waitSelector = "a[href*='-p-']"

    private val base = "https://www.carrefoursa.com"

    override fun seedUrls(): List<String> = listOf(
        "$base/sut-urunleri/c/1310",
        "$base/kahvaltilik-urunler/c/1363",
        "$base/temel-gida/c/1110",
    )

    override fun scrapeProduct(url: String): RawProduct? = browser.withPage { page ->
        page.navigate(url)
        page.waitForSelector("h1", Page.WaitForSelectorOptions().setTimeout(15_000.0))
        val doc = Jsoup.parse(page.content(), url)
        var product = ProductExtraction.fromJsonLd(doc, url) ?: return@withPage null
        if (product.barcode == null) {
            val sku = url.substringAfterLast("-p-").substringBefore('?')
            val images = doc.select("img[src], a[href]").mapNotNull { node ->
                val value = if (node.tagName() == "img") node.absUrl("src") else node.absUrl("href")
                val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return@mapNotNull null
                value.takeIf { uri.scheme == "https" && uri.host == "images.csfour.com" &&
                    uri.path.contains("/productimage/$sku/") }
            }.map { it.replace(Regex("/mnresize/[0-9]+/[0-9]+/"), "/") }.distinct().take(4)
            for (image in images) {
                val code = runCatching {
                    val response = page.request().get(image, RequestOptions.create().setTimeout(8_000.0))
                    try { if (response.ok()) LabelBarcodeDecoder.decode(response.body()) else null }
                    finally { response.dispose() }
                }.getOrNull() ?: continue
                product = product.copy(barcode = code)
                break
            }
        }
        product
    }

    override fun productLinks(listing: Document): List<String> =
        listing.select("a[href*='-p-'], a[href$='/p']")
            .map { it.absUrl("href") }
            .filter { it.isNotBlank() }
            .distinct()
}
