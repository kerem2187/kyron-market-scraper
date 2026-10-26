package com.kyron.scraper.scraper.chains

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.scraper.SourceStatus
import com.kyron.scraper.scraper.AbstractBrowserCatalogScraper
import com.kyron.scraper.model.RawProduct
import com.microsoft.playwright.Page
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Current A101 Kapıda uses hydrated _p- links and explicit barcodes in productDetail. */
class A101Scraper(browser: BrowserRunner) : AbstractBrowserCatalogScraper(browser) {
    override val slug = "a101"
    override val displayName = "A101"
    override val barcodeAvailable = true
    // Excluded by user: live GitHub Actions access returns HTTP 403; no authorized API.
    override val status = SourceStatus.DISABLED
    override val waitSelector = "a[href*='_p-']"

    override fun seedUrls(): List<String> = listOf(
        "https://www.a101.com.tr/kapida/sut-urunleri-kahvaltilik/sut",
        "https://www.a101.com.tr/kapida/atistirmalik",
        "https://www.a101.com.tr/kapida/temel-gida",
    )

    override fun productLinks(listing: Document): List<String> = listing.select("a[href*='_p-']")
        .map { it.absUrl("href").substringBefore('?') }
        .filter { it.startsWith("https://www.a101.com.tr/kapida/") }.distinct()

    override fun scrapeProduct(url: String): RawProduct? = browser.withPage { page ->
        page.navigate(url)
        page.waitForSelector("h1", Page.WaitForSelectorOptions().setTimeout(15_000.0))
        // Server-streamed productDetail may arrive after the first HTML paint.
        page.waitForFunction("() => Array.from(document.scripts).some(s => s.textContent.includes('productDetail') && s.textContent.includes('barcodes'))",
            null, Page.WaitForFunctionOptions().setTimeout(15_000.0))
        A101ProductParser.product(Jsoup.parse(page.content(), url), url)
            ?: error("A101: no matching productDetail data")
    }
}
