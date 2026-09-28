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

fun main(args: Array<String>) = runBlocking {
    val chains = args.firstOrNull()?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
        ?: Config.enabledChains
    log.info { "== Kyron Market Scraper ==" }
    log.info { "project=${Config.projectId} database=${Config.databaseId} collection=${Config.firestoreCollection}" }
    log.info { "chains=${chains.joinToString()} maxWrites=${Config.maxWritesPerRun} maxMinutes=${Config.maxRunMinutes}" }

    val firestore = FirestoreOptions.newBuilder().setProjectId(Config.projectId)
        .setDatabaseId(Config.databaseId).build().service
    firestore.use { fs ->
        val repo = FirestoreRepository(fs)
        try { repo.healthCheck() } catch (e: Exception) {
            log.error(e) { "Firestore health check FAILED" }; exitProcess(2)
        }
        val orchestrator = ScrapeOrchestrator(repo)
        BrowserRunner().use { browser ->
            val managed = try { ScraperRegistry(PoliteFetcher(), browser).resolve(chains) } catch (e: Exception) {
                log.error { e.message }; exitProcess(3)
            }
            if (managed.isEmpty()) { log.error { "No eligible sources selected" }; exitProcess(3) }
            val reports = managed.map { orchestrator.run(it) }
            reports.forEach { log.info { "SUMMARY ${it.chain}: written=${it.written} unchanged=${it.unchanged} errors=${it.errors} budget=${it.stoppedByBudget}" } }
            val written = reports.sumOf { it.written }
            val fatalSources = reports.count { it.errors > 0 && it.discovered == 0 }
            log.info { "TOTAL written=$written firestoreWrites=${repo.writesUsed}" }
            if (fatalSources > 0 || reports.all { it.withBarcode == 0 }) exitProcess(1)
        }
    }
}
