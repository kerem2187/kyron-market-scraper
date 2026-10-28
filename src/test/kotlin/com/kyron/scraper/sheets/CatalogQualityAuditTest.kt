package com.kyron.scraper.sheets

import kotlin.test.*

class CatalogQualityAuditTest {
    private fun row(code: String = "8691381000486", name: String = "Name", category: String = "Category",
                    subcategory: String = "Sub", image: String = "https://example.test/image.jpg") =
        IndexedCatalogRow(17, CatalogRow(code, name, category, subcategory, image))

    @Test fun emptyCatalogHasZeroCounts() {
        assertEquals(CatalogQualityAudit(0,0,0,0,0,0,0,0,0), CatalogQualityAudit.inspect(emptyList()))
    }
    @Test fun completeRowHasNoStructuralFindings() {
        assertEquals(CatalogQualityAudit(1,0,0,0,0,0,0,0,0), CatalogQualityAudit.inspect(listOf(row())))
    }
    @Test fun existingIncompleteRowsAreAuditedEvenWithoutIncomingProducts() {
        val rows = listOf(row(name=" ", category="", subcategory="", image=""))
        val audit = CatalogQualityAudit.inspect(rows)
        assertEquals(1,audit.missingNames); assertEquals(1,audit.missingImages)
        assertEquals(1,audit.missingCategories); assertEquals(1,audit.missingSubcategories)
        assertEquals(0,audit.nonHttpsImageReferences); assertEquals(0,audit.orphanSubcategories)
    }
    @Test fun relativeAndHttpImageReferencesRequireReviewNotDeletion() {
        val rows = listOf(row(image="KATALOG_Images/item.jpg"), row(image="http://example.test/a.jpg"))
        assertEquals(2,CatalogQualityAudit.inspect(rows).nonHttpsImageReferences)
        assertEquals(0,CatalogQualityAudit.inspect(rows).missingImages)
        assertEquals("KATALOG_Images/item.jpg", rows.first().row.imageUrl)
    }
    @Test fun malformedAndBadChecksumCodesAreNotNormalizedIntoValidRows() {
        val audit=CatalogQualityAudit.inspect(listOf(row("sku-8691381000486"),row("8691381000487")))
        assertEquals(2,audit.invalidBarcodeFormatsOrChecksums)
        assertEquals(0,audit.duplicateCanonicalRows)
    }
    @Test fun upcAndEanEquivalentsAreCountedWithoutChangingTheirValues() {
        val rows=listOf(row("036000291452"),row("0036000291452"),row("0036000291452"))
        val audit=CatalogQualityAudit.inspect(rows)
        assertEquals(2,audit.duplicateCanonicalRows);assertEquals(0,audit.invalidBarcodeFormatsOrChecksums)
        assertEquals("036000291452",rows.first().row.barcode)
    }
    @Test fun orphanSubcategoryIsVisibleWithoutInventingCategory() {
        assertEquals(1,CatalogQualityAudit.inspect(listOf(row(category=""))).orphanSubcategories)
    }
    @Test fun auditDoesNotChangeRecordsOrSyncPlan() {
        val rows=listOf(row(name="",category="",image=""))
        val before=rows.toList()
        val planBefore=CatalogSyncPlan.build(emptyList(),rows)
        CatalogQualityAudit.inspect(rows)
        assertEquals(before,rows);assertEquals(planBefore,CatalogSyncPlan.build(emptyList(),rows))
    }
    @Test fun structuralCompletenessDoesNotValidateProductSemantics() {
        // A populated but wrong category is intentionally not guessed or overwritten.
        val input=row(name="Danone mix",category="Kuru Gıda",subcategory="Atıştırmalık Kuru Ürünler")
        assertEquals(0,CatalogQualityAudit.inspect(listOf(input)).missingCategories)
        assertEquals("Kuru Gıda",input.row.category)
    }
}
