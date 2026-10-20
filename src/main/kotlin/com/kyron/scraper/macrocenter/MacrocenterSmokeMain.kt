package com.kyron.scraper.macrocenter

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.chains.MacrocenterScraper
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking

/** Read-only smoke check: no Firestore or Google Sheets credentials/writes. */
fun main() = runBlocking {
    BrowserRunner().use { browser ->
        flow<RawProduct> {
            MacrocenterScraper(browser, PoliteFetcher()).scrape { emit(it) }
        }.take(3).collect { product ->
            println("name=${product.name} barcode=${product.barcode ?: "NOT_FOUND"} image=${product.sourceImageUrl ?: "NOT_FOUND"} url=${product.sourceUrl}")
        }
    }
}
