package com.kyron.scraper.scraper.chains

import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.JsonLd
import com.kyron.scraper.util.NextFlight
import kotlinx.serialization.json.*
import org.jsoup.nodes.Document
import java.net.URI

object A101ProductParser {
    fun product(doc: Document, sourceUrl: String): RawProduct? {
        val data = NextFlight.objectProperty(NextFlight.payload(doc), "productDetail") ?: return null
        val attributes = data["attributes"] as? JsonObject ?: return null
        val expected = URI(sourceUrl)
        val actual = runCatching { URI(JsonLd.string(attributes, "seoUrl") ?: return null) }.getOrNull() ?: return null
        if (actual.host != expected.host || actual.path != expected.path) return null
        val name = JsonLd.string(attributes, "name") ?: return null
        val image = (data["images"] as? JsonArray).orEmpty().firstNotNullOfOrNull { element ->
            val obj = element as? JsonObject ?: return@firstNotNullOfOrNull null
            if (JsonLd.string(obj, "imageType") != "product") return@firstNotNullOfOrNull null
            val value = JsonLd.string(obj, "url") ?: return@firstNotNullOfOrNull null
            val uri = runCatching { URI(value) }.getOrNull() ?: return@firstNotNullOfOrNull null
            value.takeIf { uri.scheme == "https" && uri.host in setOf("cdn2.a101.com.tr", "cdn3.a101.com.tr") && uri.userInfo == null }
        }
        return RawProduct(name = name, barcode = MacrocenterProductParser.barcode(attributes),
            brand = JsonLd.string(attributes, "brand"), sourceImageUrl = image, sourceUrl = sourceUrl)
    }
}
