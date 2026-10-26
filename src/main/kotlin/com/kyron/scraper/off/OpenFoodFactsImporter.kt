package com.kyron.scraper.off

import com.kyron.scraper.config.Config
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import com.kyron.scraper.scraper.SourceStatus
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.net.HttpURLConnection
import java.net.URI

private val log = KotlinLogging.logger {}

class OpenFoodFactsImporter(
    private val country: String = "turkey",
    private val pageSize: Int = 100,
    private val maxPages: Int = 200,
    private val maxRetries: Int = 4,
    private val client: OffApiClient = OffApiClient(maxAttempts = maxRetries),
    private val onPage: (Int, Int) -> Unit = { _, _ -> },
) : ChainScraper {
    override val slug = "off"
    override val displayName = "Open Food Facts"
    override val barcodeAvailable = true
    override val status = SourceStatus.VERIFIED
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        var page = 1
        var done = false
        while (page <= maxPages) {
            val url = "https://world.openfoodfacts.org/api/v2/search" +
                "?countries_tags_en=$country&fields=code,product_name,product_name_tr,brands,image_url,categories" +
                "&page=$page&page_size=$pageSize"
            val body = client.get(url)
            val products = runCatching { json.parseToJsonElement(body).jsonObject["products"]?.jsonArray }
                .onFailure { log.warn { "[off] page $page parse failed: ${it.message}" } }.getOrNull() ?: error("[off] Invalid API response on page $page")
            onPage(page, products.size)
            if (products.isEmpty()) { done = true; break }
            for (element in products) {
                val o = element.jsonObject
                val barcode = o["code"]?.jsonPrimitive?.contentOrNull ?: continue
                val name = o["product_name_tr"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: o["product_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                emit(RawProduct(name = name, brand = o["brands"]?.jsonPrimitive?.contentOrNull, barcode = barcode,
                    sourceImageUrl = o["image_url"]?.jsonPrimitive?.contentOrNull,
                    category = o["categories"]?.jsonPrimitive?.contentOrNull?.split(",")?.firstOrNull()?.trim(),
                    sourceUrl = "https://world.openfoodfacts.org/product/$barcode"))
            }
            log.info { "[off] page $page -> ${products.size} products" }
            if (products.size < pageSize) { done = true; break }
            page++ // OffApiClient paces every request, including retry attempts.
        }
        println("OFF_COVERAGE pages=${minOf(page,maxPages)} exhausted=$done fullCatalogGuaranteed=false")
    }

}
