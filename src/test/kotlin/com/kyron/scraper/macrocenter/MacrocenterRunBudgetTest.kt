package com.kyron.scraper.macrocenter

import com.kyron.scraper.scraper.chains.MacrocenterScraper
import kotlin.test.*

class MacrocenterRunBudgetTest {
    @Test fun failedProductsStillConsumeAttemptBudget() {
        val budget = MacrocenterRunBudget(2, 1000) { 0 }
        budget.beforeProduct() // imagine a request failure, with no emit
        budget.beforeProduct()
        assertFailsWith<MacrocenterBudgetReached> { budget.beforeProduct() }
        assertEquals(2, budget.attempts)
    }

    @Test fun deadlineStopsEvenBeforeAnyProductsAreEmitted() {
        var now = 50L
        val budget = MacrocenterRunBudget(10, 1000) { now }
        now += 1000
        assertFailsWith<MacrocenterBudgetReached> { budget.checkTime() }
        assertFailsWith<MacrocenterBudgetReached> { budget.beforeProduct() }
        assertEquals(0, budget.attempts)
    }

    @Test fun requestTimeoutNeverExceedsRemainingBudget() {
        var now = 0L
        val budget = MacrocenterRunBudget(10, 1000) { now }
        assertEquals(500.0, budget.timeoutMs(500))
        now = 900
        assertEquals(100.0, budget.timeoutMs(12000))
    }

    @Test fun routeMatchesOnlyMacrocenterProductDetails() {
        val pattern = MacrocenterScraper.DETAIL_URL
        assertTrue(pattern.matcher("https://www.macrocenter.com.tr/rest/products/11550863?reid=123").matches())
        assertFalse(pattern.matcher("https://www.macrocenter.com.tr/rest/products/search?category-id=12").matches())
        assertFalse(pattern.matcher("https://www.macrocenter.com.tr/rest/recommendations/products").matches())
        assertFalse(pattern.matcher("https://evil.test/rest/products/11550863").matches())
    }
}
