package com.kyron.scraper.off
import com.kyron.scraper.util.ProductQuality
import com.kyron.scraper.util.ProductImageVerifier
import kotlinx.coroutines.runBlocking

/** Read-only test of the page-2 production failure; never initializes Firestore or Sheets. */
fun main() = runBlocking {
    val pages = mutableSetOf<Int>(); var emitted = 0; var complete = 0; var images = 0
    val verifier = ProductImageVerifier()
    OpenFoodFactsImporter(maxPages = 2, onPage = { page, count ->
        println("OFF_PAGE_READ page=$page products=$count")
        if (count > 0) pages.add(page)
    }).scrape { raw ->
        emitted++
        val product = ProductQuality.normalized(raw)
        if (product != null) {
            complete++
            if (images < 3 && verifier.verify(product.sourceImageUrl!!)) images++
        }
    }
    check(pages.containsAll(listOf(1,2))) { "OFF did not read two non-empty pages" }
    check(complete > 0 && images >= 3) { "OFF pagination lacked complete products/three valid sample images" }
    println("OFF_PAGINATION_SMOKE_OK pages=${pages.sorted()} emitted=$emitted complete=$complete sampleImages=$images")
}
