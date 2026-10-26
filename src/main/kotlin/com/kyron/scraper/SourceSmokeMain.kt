package com.kyron.scraper

import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.config.Config
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ScraperRegistry
import com.kyron.scraper.util.ProductQuality
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO

/** No Firestore initialization, credentials, or writes. */
fun main(args: Array<String>) = runBlocking {
    val slug = args.firstOrNull() ?: error("Specify one source")
    var complete = 0
    val seen = hashSetOf<String>()
    BrowserRunner().use { browser ->
        val scraper = ScraperRegistry(PoliteFetcher(), browser).resolve(listOf(slug)).single()
        flow<RawProduct> {
            scraper.scrape { raw ->
                val product = ProductQuality.normalized(raw)
                if (product != null && seen.add(product.barcode!!)) emit(product)
            }
        }.take(3).collect { product ->
            val conn = URI(product.sourceImageUrl!!).toURL().openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("User-Agent", Config.userAgent)
                conn.connectTimeout = 10_000; conn.readTimeout = 10_000
                check(conn.responseCode in 200..299) { "Image HTTP ${conn.responseCode}" }
                check(conn.contentType?.startsWith("image/") == true) { "Not an image response" }
                val bytes = conn.inputStream.use { it.readNBytes(8 * 1024 * 1024 + 1) }
                check(bytes.size <= 8 * 1024 * 1024) { "Image exceeds 8 MiB" }
                val image = ImageIO.read(bytes.inputStream()) ?: error("Image cannot be decoded")
                check(image.width > 0 && image.height > 0)
            } finally { conn.disconnect() }
            complete++
            println("COMPLETE_PRODUCT source=$slug name=${product.name} barcode=${product.barcode} image=${product.sourceImageUrl} url=${product.sourceUrl}")
        }
    }
    check(complete >= 3) { "$slug: only $complete complete products (need 3 unique valid barcodes and downloadable images)" }
    println("SOURCE_SMOKE_OK source=$slug complete=$complete")
}
