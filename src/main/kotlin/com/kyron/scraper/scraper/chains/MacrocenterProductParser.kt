package com.kyron.scraper.scraper.chains

import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.Ean
import com.kyron.scraper.util.JsonLd
import com.kyron.scraper.util.TextNormalizer
import kotlinx.serialization.json.*
import java.net.URI

/** Only a product-detail response is accepted; recommendations must never supply its barcode. */
object MacrocenterProductParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun detail(body: String, sourceUrl: String): JsonObject? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        if (root["successful"]?.jsonPrimitive?.booleanOrNull != true) return null
        val data = root["data"] as? JsonObject ?: return null
        val expected = URI(sourceUrl).path.trim('/').substringBefore('?')
        data.takeIf { JsonLd.string(it, "prettyName") == expected }
    }.getOrNull()

    fun product(data: JsonObject, sourceUrl: String): RawProduct? {
        val name = JsonLd.string(data, "name")?.takeIf { it.isNotBlank() } ?: return null
        return RawProduct(
            name = TextNormalizer.cleanName(name),
            brand = JsonLd.string(data, "brand"),
            barcode = barcode(data),
            sourceImageUrl = images(data).firstOrNull(),
            category = JsonLd.string(data, "category"),
            sourceUrl = sourceUrl,
        )
    }

    /** Explicit GTIN fields only. sku/id/prettyName and arbitrary numbers are NOT barcodes. */
    fun barcode(data: JsonObject): String? {
        for (key in listOf("gtin13", "gtin", "ean", "barcode", "barcodes")) {
            val value = data[key]
            val values = if (value is JsonArray) value.toList() else listOfNotNull(value)
            for (item in values) {
                val code = (item as? JsonPrimitive)?.contentOrNull ?: continue
                if (!code.matches(Regex("[0-9]{8}|[0-9]{12,13}"))) continue
                Ean.normalize(code)?.let { return it }
            }
        }
        return null
    }

    fun images(data: JsonObject): List<String> = imageUrls(data["images"], listOf("PRODUCT_HD", "PRODUCT_DETAIL"))

    fun labelImages(data: JsonObject): List<String> {
        val safety = data["productSafetyInfo"] as? JsonObject
        val info = safety?.get("productSafetyImageInfo") as? JsonObject
        return imageUrls(info?.get("images"), listOf("LEGAL"))
    }

    private fun imageUrls(element: JsonElement?, sizes: List<String>): List<String> =
        (element as? JsonArray).orEmpty().mapNotNull { image ->
            val urls = (image as? JsonObject)?.get("urls") as? JsonObject ?: return@mapNotNull null
            sizes.firstNotNullOfOrNull { size -> JsonLd.string(urls, size)?.let(::safeImageUrl) }
        }.distinct()

    fun safeImageUrl(value: String): String? = runCatching {
        val uri = URI(value)
        value.takeIf {
            uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                uri.host in setOf("images.migrosone.com", "www.macrocenter.com.tr")
        }
    }.getOrNull()
}
