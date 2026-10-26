package com.kyron.scraper.macrocenter

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.chains.MacrocenterScraper
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import com.kyron.scraper.util.ProductQuality
import kotlinx.coroutines.runBlocking

/** Read-only smoke check: no Firestore or Google Sheets credentials/writes. */
fun main() = runBlocking {
    var foundWithBarcodeAndImage = 0
    BrowserRunner().use { browser ->
        flow<RawProduct> {
            MacrocenterScraper(browser, PoliteFetcher()).scrape { raw ->
                ProductQuality.normalized(raw)?.let { emit(it) }
            }
        }.take(3).collect { product ->
            if (product.barcode != null && product.sourceImageUrl != null) foundWithBarcodeAndImage++
            println("name=${product.name} barcode=${product.barcode ?: "NOT_FOUND"} image=${product.sourceImageUrl ?: "NOT_FOUND"} url=${product.sourceUrl}")
        }
    }
    check(foundWithBarcodeAndImage >= 3) { "Smoke failed: no product with both barcode and image" }
    println("MACROCENTER_SMOKE_OK productsWithBarcodeAndImage=$foundWithBarcodeAndImage")
}
