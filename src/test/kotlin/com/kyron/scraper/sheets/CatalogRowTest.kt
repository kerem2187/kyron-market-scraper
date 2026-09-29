package com.kyron.scraper.sheets

import com.kyron.scraper.model.Product
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogRowTest {
    private val product = Product(
        barcode = "0123456789012", name = "Firestore Name", category = "Food",
        imageUrl = "https://example.com/image.jpg", source = "off",
    )

    @Test fun preservesManualValues() {
        val row = CatalogRow(product.barcode, "Manual Name", "Manual Category", "Sub", "local/image.jpg")
        assertEquals(row, row.mergeMissingFrom(product))
    }

    @Test fun fillsBlankValues() {
        val row = CatalogRow(product.barcode, "", "", "Manual Sub", "")
        assertEquals(
            CatalogRow(product.barcode, "Firestore Name", "Food", "Manual Sub", "https://example.com/image.jpg"),
            row.mergeMissingFrom(product),
        )
    }
}
