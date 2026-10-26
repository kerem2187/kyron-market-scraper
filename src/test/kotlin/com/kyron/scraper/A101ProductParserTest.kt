package com.kyron.scraper

import com.kyron.scraper.scraper.chains.A101ProductParser
import com.kyron.scraper.util.Ean
import org.jsoup.Jsoup
import kotlin.test.*

class A101ProductParserTest {
    private val source = "https://www.a101.com.tr/kapida/sut-urunleri-kahvaltilik/birsah-tam-yagli-sut-1-l_p-12000244"
    private val fixture = javaClass.getResource("/a101/product.html")!!.readText()
    @Test fun parsesExplicitBarcodesAndProductImageFromCurrentPage() {
        val p = assertNotNull(A101ProductParser.product(Jsoup.parse(fixture, source), source))
        assertEquals("Birşah Tam Yağlı Süt 1 L", p.name)
        assertEquals("8699118068617", p.barcode)
        assertNotNull(Ean.normalize(p.barcode))
        assertTrue(p.sourceImageUrl!!.contains("dNfRDxuKUY_1024x1024.png"))
    }
    @Test fun doesNotUseAnotherProductOrItsSku() {
        assertNull(A101ProductParser.product(Jsoup.parse(fixture), source.replace("12000244", "999")))
        val noCodes = fixture.replace(Regex("8699118[0-9]+"), "invalid")
        assertNull(A101ProductParser.product(Jsoup.parse(noCodes), source)?.barcode)
    }
}
