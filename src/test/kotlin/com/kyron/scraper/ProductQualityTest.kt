package com.kyron.scraper

import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductQuality
import kotlin.test.*

class ProductQualityTest {
    private val valid = RawProduct(name = " Test  Product ", barcode = "8690504084686",
        sourceImageUrl = "https://images.example.com/product.jpg")

    @Test fun acceptsAndNormalizesCompleteProduct() {
        val p = assertNotNull(ProductQuality.normalized(valid))
        assertEquals("Test Product", p.name)
        assertEquals("8690504084686", p.barcode)
        assertEquals(valid.sourceImageUrl, p.sourceImageUrl)
    }
    @Test fun rejectsMissingFieldsAndInvalidChecksum() {
        assertNull(ProductQuality.normalized(valid.copy(name = "  ")))
        assertNull(ProductQuality.normalized(valid.copy(barcode = null)))
        assertNull(ProductQuality.normalized(valid.copy(barcode = "8690504084687")))
        assertNull(ProductQuality.normalized(valid.copy(sourceImageUrl = null)))
        assertNull(ProductQuality.normalized(valid.copy(sourceImageUrl = "")))
    }
    @Test fun rejectsNumericGuessingAndMalformedImageUrls() {
        assertNull(ProductQuality.normalized(valid.copy(barcode = "sku:8690504084686")))
        for (url in listOf("http://images.example.com/a.jpg", "https:///a.jpg", "https://u@images.example.com/a.jpg")) {
            assertNull(ProductQuality.normalized(valid.copy(sourceImageUrl = url)))
        }
    }
    @Test fun normalizesUpcWithoutInventingBarcode() {
        assertEquals("0036000291452", ProductQuality.normalized(valid.copy(barcode = "036000291452"))?.barcode)
    }
}
