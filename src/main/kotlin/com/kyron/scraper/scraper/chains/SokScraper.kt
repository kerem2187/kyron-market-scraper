package com.kyron.scraper.scraper.chains

import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.AbstractCatalogScraper
import org.jsoup.nodes.Document
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.macrocenter.LabelBarcodeDecoder

/** SOK Market (server-rendered catalog -> HTTP). */
class SokScraper(fetcher: PoliteFetcher) : AbstractCatalogScraper(fetcher) {
    override val slug = "sok"
    override val displayName = "\u015eOK Market"
    override val barcodeAvailable = true

    private val base = "https://www.sokmarket.com.tr"

    override fun seedUrls(): List<String> = listOf("$base/kahvaltilik-c-890", base)

    override suspend fun catalogSeeds(): List<String> {
        val discovered = try {
            com.kyron.scraper.util.CatalogUrls.links(fetcher.getHtml(base), "www.sokmarket.com.tr", "-c-")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("CATALOG_COVERAGE source=$slug categoryDiscoveryFailed=true fallback=true fullCatalogGuaranteed=false")
            emptyList()
        }
        println("CATALOG_COVERAGE source=$slug categories=${discovered.size} fullCatalogGuaranteed=false")
        return (seedUrls() + discovered).distinct()
    }

    override fun productLinks(listing: Document): List<String> =
        com.kyron.scraper.util.CatalogUrls.links(listing, "www.sokmarket.com.tr", "-p-")

    override suspend fun scrapeProduct(url: String): RawProduct? {
        val doc = fetcher.getHtml(url)
        val data = SokProductParser.detail(doc, url) ?: error("Sok: no matching current-product data")
        var product = SokProductParser.product(data, url) ?: return null
        if (product.barcode == null) {
            for (image in SokProductParser.images(data).take(4)) {
                val code = runCatching { LabelBarcodeDecoder.decode(fetcher.getBytes(image, 10_000, 1)) }
                    .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; null } ?: continue
                product = product.copy(barcode = code)
                break
            }
        }
        return product
    }

    override fun nextPage(listing: Document, current: String): String? {
        val next = listing.selectFirst("a[rel=next], a.next, a[aria-label=Sonraki]")?.absUrl("href")
        return next?.takeIf { it.isNotBlank() && it != current }
    }
}
