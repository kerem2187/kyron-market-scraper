package com.kyron.scraper.util

import com.kyron.scraper.model.RawProduct
import org.jsoup.nodes.Document

object ProductExtraction {
    fun fromJsonLd(doc: Document, url: String): RawProduct? {
        val all = JsonLd.extractProducts(doc)
        fun canonical(value: String) = value.substringBefore('?').substringBefore('#').trimEnd('/')
        val matched = all.filter { item ->
            val link = JsonLd.string(item, "url")
            link != null && canonical(doc.baseUri().let { java.net.URI(it).resolve(link).toString() }) == canonical(url)
        }
        // A recommendation with a GTIN must never be mistaken for the requested product.
        val candidates = if (matched.isNotEmpty()) matched else all.filter {
            JsonLd.string(it, "url") == null
        }.takeIf { it.size == 1 }.orEmpty()
        val obj = candidates.firstOrNull { Ean.normalize(JsonLd.string(it, "gtin13", "gtin", "gtin12", "gtin8")) != null }
            ?: candidates.firstOrNull() ?: return null
        val name = JsonLd.string(obj, "name") ?: return null
        return RawProduct(
            name = TextNormalizer.cleanName(name),
            brand = JsonLd.string(obj, "brand"),
            barcode = Ean.normalize(JsonLd.string(obj, "gtin13", "gtin", "gtin12", "gtin8")),
            sourceImageUrl = JsonLd.string(obj, "image"),
            category = JsonLd.string(obj, "category"),
            unit = JsonLd.string(obj, "size", "weight"),
            sourceUrl = url,
        )
    }

    fun fromMeta(doc: Document, url: String): RawProduct? {
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text() ?: return null
        return RawProduct(
            name = TextNormalizer.cleanName(name), barcode = null,
            sourceImageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content"), sourceUrl = url,
        )
    }
}
