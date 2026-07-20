package com.kyron.scraper

import com.google.cloud.firestore.FirestoreOptions
import com.kyron.scraper.browser.BrowserRunner
import com.kyron.scraper.config.Config
import com.kyron.scraper.http.PoliteFetcher
import com.kyron.scraper.scraper.ScrapeOrchestrator
import com.kyron.scraper.scraper.ScraperRegistry
import com.kyron.scraper.store.FirestoreRepository
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * Entry point. Runs a single scrape pass and exits — designed to be triggered
 * by a GitHub Actions cron job (free), not to run as an always-on server.
 *
 * Exit codes:
 *   0  -> products were written
 *   1  -> ran fine but wrote 0 products (check per-chain SUMMARY in the log)
 *   2  -> Firestore health check failed (wrong project/database/permissions)
 */
fun main(args: Array<String>) = runBlocking {
    val chains = args.firstOrNull()
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
        ?: Config.enabledChains

    log.info { "== Kyron Market Scraper ==" }
    log.info { "project=${Config.projectId}  database=${Config.databaseId}  collection=${Config.firestoreCollection}" }
    log.info { "GOOGLE_APPLICATION_CREDENTIALS set: ${System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null}" }
    log.info { "chains=${chains.joinToString(",")}" }

    val firestore = FirestoreOptions.newBuilder()
        .setProjectId(Config.projectId)
        .setDatabaseId(Config.databaseId)
        .build()
        .service

    firestore.use { fs ->
        val repo = FirestoreRepository(fs)

        // Canary: prove we can write+read the EXACT project/database Kyron uses.
        // After a run, look for a 'healthcheck' doc in the '_meta' collection:
        //  - present  -> DB path is correct; any "no products" issue is extraction.
        //  - absent   -> you are looking at a different project/database than we write to.
        // NOTE: the canary lives in '_meta', NOT 'products', so the library stays clean.
        try {
            repo.healthCheck()
            log.info { "Firestore health check OK -> wrote '_meta/healthcheck' (products collection stays clean)." }
        } catch (e: Exception) {
            log.error(e) {
                "Firestore health check FAILED. Verify GCP_PROJECT_ID, FIRESTORE_DATABASE, " +
                    "and that the service account has roles/datastore.user."
            }
            exitProcess(2)
        }

        val orchestrator = ScrapeOrchestrator(repo)
        BrowserRunner().use { browser ->
            val fetcher = PoliteFetcher()
            val registry = ScraperRegistry(fetcher, browser)
            val scrapers = registry.resolve(chains)
            log.info { "Starting scrape for: ${scrapers.joinToString { it.slug }}" }
            val reports = scrapers.map { orchestrator.run(it) }

            log.info { "===== SUMMARY =====" }
            reports.forEach {
                log.info {
                    "  ${it.chain}: discovered=${it.discovered} withBarcode=${it.withBarcode} " +
                        "upserted=${it.upserted} skipped=${it.skipped} errors=${it.errors}"
                }
            }
            val totalUpserted = reports.sumOf { it.upserted }
            log.info { "TOTAL upserted=$totalUpserted" }

            if (totalUpserted == 0) {
                log.error {
                    "No products were written. Read the SUMMARY above:\n" +
                        "  - discovered=0            -> listing selectors/seed URLs are wrong (sites changed).\n" +
                        "  - discovered>0, withBarcode=0 -> pages don't expose barcodes via JSON-LD (extraction needs work; start with 'off').\n" +
                        "  - withBarcode>0, errors>0 -> Firestore writes are failing."
                }
                exitProcess(1)
            }
        }
    }
}
