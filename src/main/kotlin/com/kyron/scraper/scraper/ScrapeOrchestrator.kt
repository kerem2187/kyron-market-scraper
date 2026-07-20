package com.kyron.scraper.scraper

import com.kyron.scraper.model.ChainScrapeReport
import com.kyron.scraper.model.Product
import com.kyron.scraper.pipeline.MergePolicy
import com.kyron.scraper.store.FirestoreRepository
import com.kyron.scraper.util.Ean
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/** Runs a chain scraper end-to-end: extract -> normalize -> upsert (write-only). */
class ScrapeOrchestrator(
    private val repo: FirestoreRepository,
) {
    suspend fun run(scraper: ChainScraper): ChainScrapeReport {
        val start = System.currentTimeMillis()
        var discovered = 0
        var withBarcode = 0
        var upserted = 0
        var skipped = 0
        var errors = 0
        val failures = mutableListOf<String>()
        val seenThisRun = HashSet<String>()

        try {
            scraper.scrape { raw ->
                discovered++
                val barcode = Ean.normalize(raw.barcode)
                if (barcode == null) {
                    skipped++
                    return@scrape
                }
                withBarcode++
                // De-dup within a single run to avoid redundant Firestore writes (free-tier).
                if (!seenThisRun.add(barcode)) return@scrape
                try {
                    val product = Product(
                        barcode = barcode,
                        name = raw.name,
                        brand = raw.brand,
                        imageUrl = raw.sourceImageUrl,
                        category = raw.category,
                        unit = raw.unit,
                        source = scraper.slug,
                        sourceUrl = raw.sourceUrl,
                        confidence = MergePolicy.confidenceFor(scraper.slug, true),
                    )
                    repo.upsert(product)
                    upserted++
                } catch (e: Exception) {
                    errors++
                    if (failures.size < 20) failures.add("${raw.sourceUrl}: ${e.message}")
                    log.warn { "[${scraper.slug}] upsert failed: ${e.message}" }
                }
            }
        } catch (e: Exception) {
            errors++
            failures.add("fatal: ${e.message}")
            log.error(e) { "[${scraper.slug}] fatal scrape error" }
        }

        val report = ChainScrapeReport(
            chain = scraper.slug,
            discovered = discovered,
            withBarcode = withBarcode,
            upserted = upserted,
            skipped = skipped,
            errors = errors,
            durationMs = System.currentTimeMillis() - start,
            failures = failures,
        )
        log.info { "[${scraper.slug}] done: $report" }
        return report
    }
}
