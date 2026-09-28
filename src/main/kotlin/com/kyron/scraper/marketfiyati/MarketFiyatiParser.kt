package com.kyron.scraper.marketfiyati

import com.kyron.scraper.util.Ean
import kotlinx.serialization.json.*

/** Strict parser: only documented barcode-like fields are considered. */
internal object MarketFiyatiParser {
    private val barcodeKeys = listOf("barcode", "gtin", "ean")

    fun extractBarcode(o: JsonObject): String? {
        barcodeKeys.firstNotNullOfOrNull { key -> primitive(o[key])?.let(Ean::normalize) }?.let { return it }
        for (container in listOf("productDepotInfoList", "depotInfoList", "offers")) {
            val items = o[container] as? JsonArray ?: continue
            for (item in items) {
                val obj = item as? JsonObject ?: continue
                barcodeKeys.firstNotNullOfOrNull { key -> primitive(obj[key])?.let(Ean::normalize) }?.let { return it }
            }
        }
        return null
    }

    private fun primitive(el: JsonElement?): String? = (el as? JsonPrimitive)?.contentOrNull
}
