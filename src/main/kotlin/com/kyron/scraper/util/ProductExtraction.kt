package com.kyron.scraper.util

import com.kyron.scraper.model.RawProduct
import org.jsoup.nodes.Document

object ProductExtraction {
    private val titleSuffixPattern = Regex("\\s*(?:\\||-|–)\\s*(?:Migros|Macrocenter|ŞOK Market|Cepte Şok).*$", RegexOption.IGNORE_CASE)

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
            name = cleanTitle(name),
            brand = JsonLd.string(obj, "brand"),
            barcode = Ean.normalize(JsonLd.string(obj, "gtin13", "gtin", "gtin12", "gtin8")) ?: extractDomBarcode(doc),
            sourceImageUrl = JsonLd.string(obj, "image") ?: extractMetaImage(doc),
            category = JsonLd.string(obj, "category") ?: extractBreadcrumbCategory(doc),
            unit = JsonLd.string(obj, "size", "weight"),
            sourceUrl = url,
        )
    }

    /** Scrapling-inspired adaptive DOM + Meta fallback when primary JSON-LD or selector layout shifts. */
    fun fromMeta(doc: Document, url: String): RawProduct? {
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[name=twitter:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("[data-testid*=product-title], [data-testid*=product-name], h1[itemprop=name], h1")?.text()?.takeIf { it.isNotBlank() }
            ?: return null
        return RawProduct(
            name = cleanTitle(name),
            brand = doc.selectFirst("[itemprop=brand], [data-testid*=brand]")?.text()?.trim()?.takeIf { it.isNotBlank() },
            barcode = extractDomBarcode(doc),
            sourceImageUrl = extractMetaImage(doc),
            category = extractBreadcrumbCategory(doc),
            sourceUrl = url,
        )
    }

    private fun cleanTitle(raw: String): String =
        TextNormalizer.cleanName(raw.replace(titleSuffixPattern, ""))

    private fun extractDomBarcode(doc: Document): String? {
        val selectors = listOf(
            "[itemprop=gtin13]", "[itemprop=gtin]", "[itemprop=gtin12]", "[itemprop=gtin8]", "[itemprop=ean]",
            "[data-gtin]", "[data-ean]", "[data-barcode]",
        )
        for (selector in selectors) {
            for (el in doc.select(selector)) {
                val candidates = listOf(
                    el.attr("content"), el.attr("data-gtin"), el.attr("data-ean"),
                    el.attr("data-barcode"), el.text(),
                )
                for (raw in candidates) {
                    val trimmed = raw.trim()
                    if (trimmed.matches(Regex("[0-9]{8}|[0-9]{12,13}"))) {
                        Ean.normalize(trimmed)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun extractMetaImage(doc: Document): String? =
        doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img[itemprop=image]")?.attr("src")?.trim()?.takeIf { it.isNotBlank() }

    private fun extractBreadcrumbCategory(doc: Document): String? =
        doc.select("nav[aria-label*=breadcrumb] a, [itemprop=itemListElement] [itemprop=name]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("Ana Sayfa", ignoreCase = true) && !it.equals("Anasayfa", ignoreCase = true) }
            .lastOrNull()
}
