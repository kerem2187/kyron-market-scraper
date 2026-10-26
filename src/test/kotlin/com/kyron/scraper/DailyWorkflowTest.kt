package com.kyron.scraper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
class DailyWorkflowTest {
    private fun read(path:String)=Files.readString(Path.of(path))
    @Test fun sourcesAreIndependentAndOffRemainsEnabled() {
        val yml=read(".github/workflows/scrape.yml")
        assertTrue(yml.contains("fail-fast: false"));assertTrue(yml.contains("default: \"macrocenter,migros,sok,off\""))
        assertTrue(yml.contains("needs: [prepare, sources]"));assertTrue(yml.contains("always() && !cancelled()"))
    }
    @Test fun allSheetWritersShareTheSameLock() {
        for(p in listOf(".github/workflows/scrape.yml",".github/workflows/sheets-sync.yml"))
            assertTrue(read(p).contains("group: kyron-sheets-catalog-writer"))
    }
    @Test fun dailyMacrocenterNoLongerUsesOnlyMacropicks() {
        val yml=read(".github/workflows/scrape.yml")
        assertFalse(yml.contains("macropicks"));assertTrue(yml.contains("cron: \"0 0 * * *\""))
        assertTrue(yml.contains("MACROCENTER_MAX_LISTING_PAGES: \"10\""))
    }
}
