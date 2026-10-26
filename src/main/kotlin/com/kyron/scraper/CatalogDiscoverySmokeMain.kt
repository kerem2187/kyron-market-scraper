package com.kyron.scraper
import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.util.CatalogUrls
import com.microsoft.playwright.Page
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup

/** Read-only category-discovery check, separate from the curated three-product fixture path. */
fun main(args: Array<String>) = runBlocking {
    val source=args.firstOrNull() ?: error("Specify source")
    val host=when(source) { "macrocenter"->"www.macrocenter.com.tr"; "migros"->"www.migros.com.tr"; "sok"->"www.sokmarket.com.tr";else->error("Unsupported source") }
    val base="https://$host"
    val doc=if(source=="sok") PoliteFetcher().getHtml(base) else BrowserRunner().use { browser -> browser.withPage { page ->
        val response=page.navigate(base)
        check(response?.ok()==true) { "Category discovery HTTP ${response?.status()}" }
        page.waitForSelector("a[href*='-c-']",Page.WaitForSelectorOptions().setTimeout(20_000.0))
        Jsoup.parse(page.content(),base)
    } }
    val categories=CatalogUrls.links(doc,host,"-c-")
    check(categories.isNotEmpty()) { "$source: no category links discovered" }
    println("CATALOG_DISCOVERY_SMOKE_OK source=$source categories=${categories.size} fullCatalogGuaranteed=false")
}
