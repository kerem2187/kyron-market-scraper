package com.kyron.scraper

import com.kyron.scraper.scraper.chains.SokProductParser as Parser
import com.kyron.scraper.util.NextFlight
import org.jsoup.Jsoup
import kotlin.test.*

class SokProductParserTest {
    private val source = "https://www.sokmarket.com.tr/caykur-rize-turist-cay-1000-g-p-7545"
    private val doc = Jsoup.parse(javaClass.getResource("/sok/product.html")!!.readText(), source)
    @Test fun parsesCurrentProductAndOriginalImages() {
        val data = assertNotNull(Parser.detail(doc, source))
        val p = assertNotNull(Parser.product(data, source))
        assertEquals("Çaykur Rize Turist Çay 1000 g", p.name)
        assertNull(p.barcode) // 7545 and external SKU numbers are NOT barcodes.
        assertEquals(2, Parser.images(data).size)
        assertTrue(p.sourceImageUrl!!.startsWith("https://images.ceptesok.com/product-assets/"))
    }
    @Test fun rejectsOtherProductIdentity() {
        assertNull(Parser.detail(doc, "https://www.sokmarket.com.tr/other-p-1"))
    }
    @Test fun parsesBalancedStringsWithoutEvaluatingJavascript() {
        assertEquals("a } b", NextFlight.objectProperty("\"productData\":{\"name\":\"a } b\"}", "productData")?.get("name")?.toString()?.trim('"'))
        assertNull(NextFlight.objectProperty("\"productData\":\"$" + "21\"", "productData"))
    }
}
