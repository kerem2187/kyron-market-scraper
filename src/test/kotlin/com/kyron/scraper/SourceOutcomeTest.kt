package com.kyron.scraper

import com.kyron.scraper.model.ChainScrapeReport
import kotlin.test.*

class SourceOutcomeTest {
    private fun report(complete: Int, errors: Int, unchanged: Int = 0) = ChainScrapeReport(
        chain = "test", discovered = 10, withBarcode = complete, inserted = 0, updated = 0,
        unchanged = unchanged, skipped = 0, errors = errors, stoppedByBudget = false, durationMs = 1)

    @Test fun partialProductsDoNotHideFatalErrors() {
        assertFalse(report(7, 1).successful)
    }
    @Test fun emptySourcesNeverPass() { assertFalse(report(0, 0).successful) }
    @Test fun unchangedCompleteRecordsAreSuccessful() { assertTrue(report(3, 0, 3).successful) }
}
