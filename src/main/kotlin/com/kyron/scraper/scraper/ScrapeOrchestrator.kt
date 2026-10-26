package com.kyron.scraper.scraper

import com.kyron.scraper.config.Config
import com.kyron.scraper.model.ChainScrapeReport
import com.kyron.scraper.model.Product
import com.kyron.scraper.pipeline.MergePolicy
import com.kyron.scraper.store.FirestoreRepository
import com.kyron.scraper.store.UpsertOutcome
import com.kyron.scraper.util.Ean
import com.kyron.scraper.util.ProductQuality
import com.kyron.scraper.util.TextNormalizer
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private class BudgetReached(message: String) : RuntimeException(message)

class ScrapeOrchestrator(
    private val repo: FirestoreRepository,
    private val deadlineMs: Long = System.currentTimeMillis() + Config.maxRunMinutes * 60_000,
) {
    suspend fun run(scraper: ChainScraper): ChainScrapeReport {
        val start = System.currentTimeMillis()
        var discovered = 0; var withBarcode = 0; var inserted = 0; var updated = 0
        var unchanged = 0; var skipped = 0; var errors = 0; var stoppedByBudget = false
        val failures = mutableListOf<String>()
        val seenThisRun = HashSet<String>()

        try {
            scraper.scrape { raw ->
                if (System.currentTimeMillis() >= deadlineMs) throw BudgetReached("run-time budget reached")
                if (!repo.hasWriteCapacity()) throw BudgetReached("write budget reached")
                discovered++
                val complete = ProductQuality.normalized(raw)
                if (complete == null) { skipped++; return@scrape }
                val barcode = complete.barcode!!
                withBarcode++
                if (!seenThisRun.add(barcode)) { skipped++; return@scrape }
                try {
                    val product = Product(
                        barcode = barcode,
                        name = complete.name,
                        brand = raw.brand?.trim()?.takeIf { it.isNotEmpty() },
                        imageUrl = complete.sourceImageUrl,
                        category = raw.category?.trim()?.takeIf { it.isNotEmpty() },
                        unit = raw.unit?.trim()?.takeIf { it.isNotEmpty() },
                        source = scraper.slug,
                        sourceUrl = raw.sourceUrl?.trim()?.takeIf { it.startsWith("https://") },
                        confidence = MergePolicy.confidenceFor(scraper.slug, true),
                    )
                    when (repo.upsert(product).outcome) {
                        UpsertOutcome.INSERTED -> inserted++
                        UpsertOutcome.UPDATED -> updated++
                        UpsertOutcome.UNCHANGED -> unchanged++
                    }
                } catch (e: IllegalArgumentException) {
                    throw BudgetReached(e.message ?: "budget reached")
                } catch (e: Exception) {
                    errors++
                    if (failures.size < 20) failures.add("${raw.sourceUrl}: ${e.message}")
                    log.warn { "[${scraper.slug}] upsert failed: ${e.message}" }
                }
            }
        } catch (e: BudgetReached) {
            stoppedByBudget = true
            log.warn { "[${scraper.slug}] stopped safely: ${e.message}" }
        } catch (e: Exception) {
            errors++
            failures.add("fatal: ${e.message}")
            log.error(e) { "[${scraper.slug}] fatal scrape error" }
        }

        return ChainScrapeReport(
            chain = scraper.slug, discovered = discovered, withBarcode = withBarcode,
            inserted = inserted, updated = updated, unchanged = unchanged, skipped = skipped,
            errors = errors, stoppedByBudget = stoppedByBudget,
            durationMs = System.currentTimeMillis() - start, failures = failures,
        ).also { log.info { "[${scraper.slug}] done: $it" } }
    }
}
