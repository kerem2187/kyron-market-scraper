package com.kyron.scraper.sheets
import com.kyron.scraper.model.Product
import kotlin.test.*
class CatalogSyncPlanTest {
    private fun product(code: String = "8691381000486") = Product(code, "Name", imageUrl="https://example.test/image.jpg", source="off")
    @Test fun appendsOnlyCompleteProducts() {
        val plan = CatalogSyncPlan.build(listOf(product(), product("invalid"), product("8690504017301").copy(imageUrl=null)), emptyList())
        assertEquals(1, plan.inserted); assertEquals(2, plan.incompleteSkipped)
    }
    @Test fun preservesManualValuesAndOriginalRowNumbers() {
        val row = IndexedCatalogRow(17, CatalogRow("8691381000486", "Manual", "Category", "Sub", ""))
        val plan = CatalogSyncPlan.build(listOf(product()), listOf(row))
        assertEquals(listOf(CatalogCellUpdate(17,"E","https://example.test/image.jpg")), plan.updates)
        assertTrue(plan.additions.isEmpty())
    }
    @Test fun duplicateRowsAreReportedNotDeletedOrCompacted() {
        val a=IndexedCatalogRow(2,CatalogRow("8691381000486","Manual",imageUrl="local/image.jpg"))
        val b=IndexedCatalogRow(9,a.row)
        val plan=CatalogSyncPlan.build(listOf(product()),listOf(a,b))
        assertEquals(1,plan.duplicateRows);assertTrue(plan.updates.isEmpty());assertTrue(plan.additions.isEmpty())
    }
    @Test fun repeatedSyncDoesNotAppendDuplicates() {
        val first=CatalogSyncPlan.build(listOf(product()),emptyList())
        val next=CatalogSyncPlan.build(listOf(product()),first.additions.mapIndexed { i,r->IndexedCatalogRow(i+2,r) })
        assertEquals(0,next.inserted);assertEquals(1,next.unchanged)
    }
    @Test fun normalizedUpcAndEanDoNotProduceDuplicateAppends() {
        val plan=CatalogSyncPlan.build(listOf(product("036000291452"),product("0036000291452")),emptyList())
        assertEquals(1,plan.inserted)
        assertEquals("0036000291452",plan.additions.single().barcode)
    }
}
