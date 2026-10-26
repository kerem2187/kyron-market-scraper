package com.kyron.scraper.scraper.chains

import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.JsonLd
import com.kyron.scraper.util.NextFlight
import kotlinx.serialization.json.*
import org.jsoup.nodes.Document
import java.net.URI

object SokProductParser {
    fun detail(doc: Document, sourceUrl: String): JsonObject? {
        val data = NextFlight.objectProperty(NextFlight.payload(doc), "productData") ?: return null
        val product = data["product"] as? JsonObject ?: return null
        val expected = URI(sourceUrl).path.trim('/')
        return product.takeIf { JsonLd.string(it, "path")?.substringBefore('?') == expected }
    }

    fun images(product: JsonObject): List<String> = (product["images"] as? JsonArray).orEmpty()
        .mapNotNull { image ->
            val obj = image as? JsonObject ?: return@mapNotNull null
            val host = JsonLd.string(obj, "host") ?: return@mapNotNull null
            val path = JsonLd.string(obj, "path") ?: return@mapNotNull null
            if (host != "https://images.ceptesok.com" || path.startsWith('/') || path.contains("..")) return@mapNotNull null
            "$host/$path"
        }.distinct()

    fun product(data: JsonObject, sourceUrl: String): RawProduct? {
        val name = JsonLd.string(data, "name") ?: return null
        return RawProduct(name = name, brand = JsonLd.string(data, "brand"),
            barcode = MacrocenterProductParser.barcode(data), sourceImageUrl = images(data).firstOrNull(), sourceUrl = sourceUrl)
    }
}
