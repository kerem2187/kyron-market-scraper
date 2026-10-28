package com.kyron.scraper.sheets

import com.google.cloud.firestore.FirestoreOptions
import com.kyron.scraper.config.Config
import mu.KotlinLogging
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/** Standalone Firestore -> Sheets sync; does not scrape websites or write Firestore. */
fun main() {
    require(Config.sheetsSyncEnabled) { "SHEETS_SYNC_ENABLED must be true" }
    val firestore = FirestoreOptions.newBuilder()
        .setProjectId(Config.projectId)
        .setDatabaseId(Config.databaseId)
        .build()
        .service
    firestore.use { fs ->
        try {
            val report = SheetsCatalogSync(fs).sync()
            log.info { "SHEETS_TOTAL inserted=${report.inserted} enriched=${report.enriched} unchanged=${report.unchanged} finalRows=${report.finalRows} skippedIncomplete=${report.incompleteSkipped} duplicatesObserved=${report.duplicateRowsObserved} verified=true verificationScope=plannedWritesOnly catalogQualityGuaranteed=false" }
        } catch (e: Exception) {
            log.error(e) { "Standalone Google Sheets catalog sync FAILED" }
            exitProcess(4)
        }
    }
}
