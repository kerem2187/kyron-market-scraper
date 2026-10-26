package com.kyron.scraper.macrocenter

import com.kyron.scraper.scraper.chains.MacrocenterProductParser as Parser
import com.kyron.scraper.scraper.chains.MacrocenterScraper
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import kotlin.test.*

class MacrocenterProductParserTest {
    private val url = "https://www.macrocenter.com.tr/uluova-kefir-chia-tohumlu-1-l-p-b0408f"
    private val fixture = javaClass.getResource("/macrocenter/product-detail.json")!!.readText()

    @Test fun parsesCapturedPublicDetailWithoutMistakingSkuForBarcode() {
        val data = assertNotNull(Parser.detail(fixture, url))
        val product = assertNotNull(Parser.product(data, url))
        assertEquals("Uluova Kefir Chia Tohumlu 1 L", product.name)
        assertEquals("Uluova", product.brand)
        assertEquals("Kefir", product.category)
        assertNull(product.barcode) // The live API did NOT expose a GTIN.
        assertTrue(product.sourceImageUrl!!.endsWith("-1650x1650.jpg"))
        assertEquals(1, Parser.labelImages(data).size)
        assertTrue(Parser.labelImages(data).single().contains("/product-legal/11550863/"))
    }

    @Test fun rejectsDifferentProductAndUnsuccessfulResponses() {
        assertNull(Parser.detail(fixture, "https://www.macrocenter.com.tr/another-product-p-abc"))
        assertNull(Parser.detail(fixture.replace("\"successful\": true", "\"successful\": false"), url))
        assertNull(Parser.detail("not json", url))
    }

    @Test fun acceptsOnlyExplicitChecksumValidBarcodeFields() {
        assertEquals("8690504084686", Parser.barcode(buildJsonObject { put("barcode", "8690504084686") }))
        assertNull(Parser.barcode(buildJsonObject { put("barcode", "8690504084687") }))
        assertNull(Parser.barcode(buildJsonObject { put("sku", "8690504084686"); put("id", "8690504084686") }))
        assertNull(Parser.barcode(buildJsonObject { put("barcode", "EAN: 8690504084686") }))
        assertEquals("8690504084686", Parser.barcode(buildJsonObject {
            putJsonArray("barcodes") { add("invalid"); add("8690504084686") }
        }))
    }

    @Test fun rejectsUnsafeImageHosts() {
        assertNull(Parser.safeImageUrl("http://images.migrosone.com/a.jpg"))
        assertNull(Parser.safeImageUrl("https://images.migrosone.com.evil.test/a.jpg"))
        assertNull(Parser.safeImageUrl("https://127.0.0.1/a.jpg"))
        assertNull(Parser.safeImageUrl("https://user@images.migrosone.com/a.jpg"))
    }

    @Test fun discoversCurrentMenuAndDeduplicatesSameOriginProductLinks() {
        val doc = Jsoup.parse("""
            <mc-nav-tree-large-item><a href="/sut-c-116eb">Süt</a>
              <fe-category-flyout-simple><a href="/alt-c-123">Alt</a></fe-category-flyout-simple>
            </mc-nav-tree-large-item>
            <a href="/sut-p-abcd?campaign=1">Süt</a><a href="/sut-p-abcd">Süt</a>
            <a href="https://evil.test/sut-p-abcd">External</a>
        """, MacrocenterScraper.BASE)
        assertEquals(listOf("${MacrocenterScraper.BASE}/sut-c-116eb"), MacrocenterScraper.categoryLinks(doc))
        assertEquals(listOf("${MacrocenterScraper.BASE}/sut-p-abcd"), MacrocenterScraper.productLinks(doc))
    }
    @Test fun acceptsOnlySameOriginCategoryAndCuratedListSeeds() {
        assertTrue(MacrocenterScraper.isSeedUrl("https://www.macrocenter.com.tr/macropicks-l-8d03038"))
        assertTrue(MacrocenterScraper.isSeedUrl("https://www.macrocenter.com.tr/sut-c-116eb"))
        assertFalse(MacrocenterScraper.isSeedUrl("https://evil.test/macropicks-l-8d03038"))
        assertFalse(MacrocenterScraper.isSeedUrl("http://www.macrocenter.com.tr/macropicks-l-8d03038"))
        assertFalse(MacrocenterScraper.isSeedUrl("https://www.macrocenter.com.tr/sut-p-123"))
    }

    @Test fun parsesMigrosScreenWrapperWithoutInventingProduceBarcode() {
        val body = javaClass.getResource("/migros/product-screen.json")!!.readText()
        val source = "https://www.migros.com.tr/patates-dokme-kg-p-1afabd0"
        val data = assertNotNull(Parser.detail(body, source))
        val product = assertNotNull(Parser.product(data, source))
        assertEquals("Patates Dökme Kg", product.name)
        assertNull(product.barcode) // sku/id is not a barcode.
        assertTrue(product.sourceImageUrl!!.contains("/sanalmarket/product/28290000/"))
        assertNull(Parser.detail(body, "https://www.migros.com.tr/different-p-123"))
    }
}
