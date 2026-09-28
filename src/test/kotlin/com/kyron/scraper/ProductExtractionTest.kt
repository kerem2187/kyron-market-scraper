package com.kyron.scraper

import com.kyron.scraper.util.ProductExtraction
import org.jsoup.Jsoup
import kotlin.test.*

class ProductExtractionTest {
    @Test fun extractsNestedBrandAndImageObject() {
        val html = """<script type="application/ld+json">{
          "@type":"Product","name":" Test  Product ","gtin13":"8690504084686",
          "brand":{"name":"Brand"},"image":{"url":"https://example.com/p.jpg"},"category":"Food"
        }</script>"""
        val p = ProductExtraction.fromJsonLd(Jsoup.parse(html), "https://example.com/p")
        assertNotNull(p)
        assertEquals("Test Product", p.name)
        assertEquals("Brand", p.brand)
        assertEquals("https://example.com/p.jpg", p.sourceImageUrl)
    }
}
