package com.kyron.scraper.off

import com.kyron.scraper.config.Config
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.net.HttpURLConnection
import java.net.URI

private val log = KotlinLogging.logger {}

/**
 * Imports Turkey products from Open Food Facts (ODbL licensed) — the recommended
 * MVP base layer and the best first test: it does not depend on fragile HTML
 * selectors.
 *
 * Uses the documented Search API v2 (api/v2/search). The old facet .json endpoint
 * (/country/turkey.json) is heavily throttled and frequently returns HTTP 503,
 * so we avoid it and add retry + backoff here.
 *
 * NOTE: For a full bulk load prefer the official data dump (see README).
 */
class OpenFoodFactsImporter(
    private val country: String = "turkey",
    private val pageSize: Int = 100,
    private val maxPages: Int = 200,
    private val maxRetries: Int = 4,
) : ChainScraper {
    override val slug = "off"
    override val displayName = "Open Food Facts"
    override val barcodeAvailable = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        var page = 1
        while (page <= maxPages) {
            val url = "https://world.openfoodfacts.org/api/v2/search" +
                "?countries_tags_en=$country" +
                "&fields=code,product_name,product_name_tr,brands,image_url,categories" +
                "&page=$page&page_size=$pageSize"

            val body = fetchWithRetry(url) ?: break
            val products = runCatching {
                json.parseToJsonElement(body).jsonObject["products"]?.jsonArray
            }.onFailure { log.warn { "[off] page $page parse failed: ${it.message}" } }.getOrNull() ?: break
            if (products.isEmpty()) break

            var emitted = 0
            for (p in products) {
                val o = p.jsonObject
                val barcode = o["code"]?.jsonPrimitive?.contentOrNull ?: continue
                val name = o["product_name_tr"]?.jsonPrimitive?.contentOrNull
                    ?: o["product_name"]?.jsonPrimitive?.contentOrNull ?: continue
                if (name.isBlank()) continue
                emit(
                    RawProduct(
                        name = name,
                        brand = o["brands"]?.jsonPrimitive?.contentOrNull,
                        barcode = barcode,
                        sourceImageUrl = o["image_url"]?.jsonPrimitive?.contentOrNull,
                        category = o["categories"]?.jsonPrimitive?.contentOrNull
                            ?.split(",")?.firstOrNull()?.trim(),
                        sourceUrl = "https://world.openfoodfacts.org/product/$barcode",
                    ),
                )
                emitted++
            }
            log.info { "[off] page $page -> ${products.size} products (emitted=$emitted)" }
            if (products.size < pageSize) break // last page
            page++
        }
    }

    /** GET with exponential-ish backoff. Retries on network errors and 429/5xx. */
    private suspend fun fetchWithRetry(url: String): String? {
        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", Config.userAgent)
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code in 200..299) {
                    return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                runCatching { conn.errorStream?.use { it.readBytes() } } // drain
                if (code == 429 || code in 500..599) {
                    val backoff = 2_000L * attempt
                    log.warn { "[off] HTTP $code for $url — retry $attempt/$maxRetries after ${backoff}ms" }
                    delay(backoff)
                    continue
                }
                log.warn { "[off] HTTP $code for $url — giving up" }
                return null
            } catch (e: Exception) {
                val backoff = 2_000L * attempt
                log.warn { "[off] fetch error (${e.message}) — retry $attempt/$maxRetries after ${backoff}ms" }
                delay(backoff)
            }
        }
        log.error { "[off] failed after $maxRetries attempts: $url" }
        return null
    }
}
