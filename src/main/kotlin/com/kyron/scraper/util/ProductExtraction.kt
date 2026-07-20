package com.kyron.scraper.util

import com.kyron.scraper.model.RawProduct
import org.jsoup.nodes.Document

/** Shared extraction logic used by both HTTP and browser-based scrapers. */
object ProductExtraction {

    /** Preferred path: schema.org Product JSON-LD. */
    fun fromJsonLd(doc: Document, url: String): RawProduct? {
        val obj = JsonLd.extractProducts(doc).firstOrNull() ?: return null
        val name = JsonLd.string(obj, "name") ?: return null
        val barcode = Ean.normalize(JsonLd.string(obj, "gtin13", "gtin", "gtin12", "gtin8", "sku", "mpn"))
        return RawProduct(
            name = TextNormalizer.cleanName(name),
            brand = JsonLd.string(obj, "brand"),
            barcode = barcode,
            sourceImageUrl = JsonLd.string(obj, "image"),
            sourceUrl = url,
        )
    }

    /** Fallback path: OpenGraph / basic DOM. Usually has no barcode. */
    fun fromMeta(doc: Document, url: String): RawProduct? {
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: return null
        return RawProduct(
            name = TextNormalizer.cleanName(name),
            barcode = null,
            sourceImageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content"),
            sourceUrl = url,
        )
    }
}
