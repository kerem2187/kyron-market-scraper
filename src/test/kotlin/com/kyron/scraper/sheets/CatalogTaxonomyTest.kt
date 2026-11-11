package com.kyron.scraper.sheets

import kotlin.test.*

class CatalogTaxonomyTest {
    private val header = CatalogTaxonomy.HEADERS
    private fun dictionary() = CatalogTaxonomy.fromSheetValues(listOf(header,
        listOf("id-1", "Parent A", "Child A"), listOf("id-2", "Parent B", "Child B")))
    @Test fun exactKnownCategoryIsAccepted() { assertEquals("Parent A", dictionary().acceptedCategory("Parent A")) }
    @Test fun surroundingWhitespaceIsTrimmed() { assertEquals("Parent A",dictionary().acceptedCategory(" Parent A "," Child A ")) }
    @Test fun foreignOrCaseVariantLabelsAreNotGuessed() {
        assertNull(dictionary().acceptedCategory("en:parent-a"));assertNull(dictionary().acceptedCategory("parent a"))
    }
    @Test fun categoryMustMatchExistingSubcategory() {
        assertNull(dictionary().acceptedCategory("Parent A","Child B"))
        assertEquals("Parent B",dictionary().acceptedCategory("Parent B","Child B"))
    }
    @Test fun missingDictionaryDefaultsToDeny() { assertNull(CatalogTaxonomy.EMPTY.acceptedCategory("Parent A")) }
    @Test fun missingOrWrongHeadersAreRejected() {
        assertFailsWith<IllegalArgumentException>{CatalogTaxonomy.fromSheetValues(emptyList())}
        assertFailsWith<IllegalArgumentException>{CatalogTaxonomy.fromSheetValues(listOf(listOf("ID","Category","Sub")))}
    }
    @Test fun emptyDictionaryIsRejected() {
        assertFailsWith<IllegalArgumentException>{CatalogTaxonomy.fromSheetValues(listOf(header,listOf("","","")))}
    }
    @Test fun partialDictionaryRowIsRejected() {
        assertFailsWith<IllegalArgumentException>{CatalogTaxonomy.fromSheetValues(listOf(header,listOf("id","Parent A")))}
    }
    @Test fun duplicateIdsAreRejected() {
        assertFailsWith<IllegalArgumentException>{CatalogTaxonomy.fromSheetValues(listOf(header,
            listOf("same","Parent A","Child A"),listOf("same","Parent B","Child B")))}
    }
    @Test fun trailingBlankRowsAreIgnoredAndExtraColumnsAllowed() {
        val taxonomy=CatalogTaxonomy.fromSheetValues(listOf(header+"Description",listOf("id","Parent A","Child A","Info"), emptyList()))
        assertEquals(1,taxonomy.pairCount)
    }
}
