package com.kyron.scraper.sheets

import com.kyron.scraper.model.Product
import kotlin.test.*

class CatalogCategoryGuardTest {
    private val taxonomy=CatalogTaxonomy.fromSheetValues(listOf(CatalogTaxonomy.HEADERS,
        listOf("id-1","Parent A","Child A"),listOf("id-2","Parent B","Child B")))
    private fun product(category:String?)=Product("8691381000486","Product",imageUrl="https://example.test/image.jpg",category=category,source="off")
    private fun existing(category:String="",sub:String="",image:String="")=IndexedCatalogRow(1970,
        CatalogRow("8691381000486","Manual name",category,sub,image))
    @Test fun knownCategoryCanFillBlankCell() {
        val plan=CatalogSyncPlan.build(listOf(product("Parent A")),listOf(existing()),taxonomy)
        assertTrue(CatalogCellUpdate(1970,"C","Parent A") in plan.updates)
        assertEquals(0,plan.categorySuggestionsSuppressed)
    }
    @Test fun unknownCategoryDoesNotBlockNameOrImageEnrichment() {
        val blank=existing().let { it.copy(row=it.row.copy(name="")) }
        val plan=CatalogSyncPlan.build(listOf(product("en:raw-label")),listOf(blank),taxonomy)
        assertEquals(listOf(CatalogCellUpdate(1970,"B","Product"),CatalogCellUpdate(1970,"E","https://example.test/image.jpg")),plan.updates)
        assertEquals(1,plan.categorySuggestionsSuppressed)
    }
    @Test fun unknownCategoryIsBlankOnNewCompleteProduct() {
        val plan=CatalogSyncPlan.build(listOf(product("en:raw-label")),emptyList(),taxonomy)
        assertEquals(1,plan.inserted); assertEquals("",plan.additions.single().category)
        assertEquals("Product",plan.additions.single().name);assertEquals(1,plan.categorySuggestionsSuppressed)
    }
    @Test fun incompatibleParentIsNotFilledAboveManualSubcategory() {
        val plan=CatalogSyncPlan.build(listOf(product("Parent A")),listOf(existing(sub="Child B")),taxonomy)
        assertFalse(plan.updates.any{it.column=="C"||it.column=="D"});assertEquals(1,plan.categorySuggestionsSuppressed)
    }
    @Test fun compatibleParentCanFillAboveManualSubcategory() {
        val plan=CatalogSyncPlan.build(listOf(product("Parent B")),listOf(existing(sub="Child B")),taxonomy)
        assertTrue(CatalogCellUpdate(1970,"C","Parent B") in plan.updates)
        assertFalse(plan.updates.any{it.column=="D"})
    }
    @Test fun populatedManualValuesArePreservedEvenIfNotInDictionary() {
        val row=existing("Legacy manual category","Legacy manual sub","local/image.jpg")
        val plan=CatalogSyncPlan.build(listOf(product("Parent A")),listOf(row),taxonomy)
        assertTrue(plan.updates.isEmpty()); assertEquals(1,plan.unchanged)
        assertEquals("Legacy manual category",row.row.category);assertEquals(0,plan.categorySuggestionsSuppressed)
    }
    @Test fun omittedDictionaryDoesNotEnableUnreviewedCategoryWrites() {
        val plan=CatalogSyncPlan.build(listOf(product("Parent A")),emptyList())
        assertEquals("",plan.additions.single().category)
    }
    @Test fun repeatedSyncDoesNotCreateDuplicateOrNewCategoryGuess() {
        val first=CatalogSyncPlan.build(listOf(product("en:raw-label")),emptyList(),taxonomy)
        val next=CatalogSyncPlan.build(listOf(product("en:raw-label")),first.additions.map{IndexedCatalogRow(2,it)},taxonomy)
        assertEquals(0,next.inserted);assertTrue(next.updates.isEmpty());assertEquals(1,next.unchanged)
    }
}
