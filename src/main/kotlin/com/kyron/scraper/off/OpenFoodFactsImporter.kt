package com.kyron.scraper.off

import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import com.kyron.scraper.scraper.SourceStatus
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

class OpenFoodFactsImporter(
    private val country: String = "turkey",
    private val pageSize: Int = 100,
    private val maxPages: Int = 200,
    private val maxRetries: Int = 4,
    private val client: OffApiClient = OffApiClient(maxAttempts = maxRetries),
    private val onPage: (Int, Int) -> Unit = { _, _ -> },
    private val baseUrl: String = "https://world.openfoodfacts.org",
    override val slug: String = "off",
    override val displayName: String = "Open Food Facts",
    private val startPage: Int = (System.getenv("OFF_START_PAGE")?.toIntOrNull()?.coerceAtLeast(1) ?: 1),
) : ChainScraper {
    override val barcodeAvailable = true
    override val status = SourceStatus.VERIFIED
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        var page = startPage
        var done = false
        var consecutiveErrors = 0
        while (page < startPage + maxPages) {
            val url = "$baseUrl/api/v2/search" +
                "?countries_tags_en=$country&fields=code,product_name,product_name_tr,brands,image_url,image_front_url,image_small_url,categories" +
                "&page=$page&page_size=$pageSize"
          val products = runCatching {
                val body = client.get(url)
                json.parseToJsonElement(body).jsonObject["products"]?.jsonArray
                    ?: error("[$slug] Invalid API response on page $page")
            }.onFailure {
                consecutiveErrors++
                log.warn { "[$slug] page $page failed ($consecutiveErrors/3): ${it.message}" }
                if (consecutiveErrors >= 3) throw it
            }.getOrNull()
            if (products == null) {
                page++
                continue
            }
            consecutiveErrors = 0
            onPage(page, products.size)
            if (products.isEmpty()) { done = true; break }
            for (element in products) {
                val o = element.jsonObject
                val barcode = o["code"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val name = o["product_name_tr"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                    ?: o["product_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val imageUrl = o["image_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                    ?: o["image_front_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                    ?: o["image_small_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                emit(
                    RawProduct(
                        name = name,
                        brand = o["brands"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                        barcode = barcode,
                        sourceImageUrl = imageUrl,
                        category = o["categories"]?.jsonPrimitive?.contentOrNull?.split(",")?.firstOrNull()?.trim(),
                        sourceUrl = "$baseUrl/product/$barcode",
                    )
                )
            }
            log.info { "[$slug] page $page -> ${products.size} products" }
            if (products.size < pageSize) { done = true; break }
            page++ // OffApiClient paces every request, including retry attempts.
        }
        println("${slug.uppercase()}_COVERAGE pages=${minOf(page, maxPages)} exhausted=$done fullCatalogGuaranteed=false")
    }
}
