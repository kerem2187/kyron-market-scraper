package com.kyron.scraper
import com.kyron.scraper.off.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
class OffPaginationTest {
    private val product="""{"code":"8691381000486","product_name":"Water","image_url":"https://example.test/p.jpg"}"""
    @Test fun readsSecondPageAndStopsAtConfiguredLimit() = runBlocking<Unit> {
        val pages=mutableListOf<Int>();var emitted=0
        val client=OffApiClient(request={ OffResponse(200,"""{"products":[$product,$product]}""") },sleep={},intervalMs=0)
        OpenFoodFactsImporter(pageSize=2,maxPages=2,client=client,onPage={ p,_->pages.add(p) }).scrape { emitted++ }
        assertEquals(listOf(1,2),pages);assertEquals(4,emitted)
    }
    @Test fun secondPageErrorIsNotHiddenByFirstPageSuccess() = runBlocking<Unit> {
        var calls=0
        val client=OffApiClient(maxAttempts=1,request={ if(calls++==0) OffResponse(200,"""{"products":[$product]}""") else OffResponse(503,"") },sleep={},intervalMs=0)
        assertFailsWith<IllegalStateException> { OpenFoodFactsImporter(pageSize=1,maxPages=2,client=client).scrape {} }
        assertEquals(2,calls)
    }
    @Test fun malformedResponseDoesNotMeanEmptyCatalog() = runBlocking<Unit> {
        val client=OffApiClient(request={ OffResponse(200,"""{"error":"failure"}""") },sleep={},intervalMs=0)
        assertFailsWith<IllegalStateException> { OpenFoodFactsImporter(client=client).scrape {} }
    }
}
