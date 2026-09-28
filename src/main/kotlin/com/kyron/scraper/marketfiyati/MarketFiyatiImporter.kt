package com.kyron.scraper.marketfiyati

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

/** Disabled until commercial-use permission and a stable response contract are confirmed. */
class MarketFiyatiImporter(
    private val pageSize: Int = 25,
    private val maxPagesPerKeyword: Int = 20,
) : ChainScraper {
    override val slug = "marketfiyati"
    override val displayName = "Market Fiyatı"
    override val barcodeAvailable = true
    override val status = SourceStatus.DISABLED
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val keywords = listOf("süt", "yoğurt", "peynir", "makarna", "pirinç", "çay", "kahve")

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val seen = HashSet<String>()
        for (keyword in keywords) for (page in 0 until maxPagesPerKeyword) {
            val body = search(keyword, page) ?: break
            val content = runCatching { json.parseToJsonElement(body).jsonObject["content"]?.jsonArray }.getOrNull() ?: break
            if (content.isEmpty()) break
            for (item in content) {
                val o = item as? JsonObject ?: continue
                val barcode = MarketFiyatiParser.extractBarcode(o) ?: continue
                if (!seen.add(barcode)) continue
                val name = firstString(o, "title", "name", "productName", "itemName") ?: continue
                emit(RawProduct(name = name, brand = firstString(o, "brand", "brandName"), barcode = barcode,
                    sourceImageUrl = firstString(o, "imageUrl", "image", "imageLink"),
                    category = firstString(o, "category", "categoryName", "mainCategory"),
                    sourceUrl = "https://marketfiyati.org.tr/urun/$barcode"))
            }
            if (content.size < pageSize) break
            delay(Config.requestDelayMs)
        }
    }

    private fun firstString(o: JsonObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (o[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun search(keyword: String, page: Int): String? = runCatching {
        val payload = buildJsonObject { put("keywords", keyword); put("pages", page); put("size", pageSize) }.toString()
        val conn = URI("https://api.marketfiyati.org.tr/api/v2/search").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", Config.userAgent); conn.connectTimeout = 20_000; conn.readTimeout = 60_000
        conn.outputStream.use { it.write(payload.toByteArray()) }
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.bufferedReader().use { it.readText() }
    }.onFailure { log.warn { "[marketfiyati] ${it.message}" } }.getOrNull()
}
