package com.kyron.scraper.store

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.kyron.scraper.config.Config
import com.kyron.scraper.model.Product
import com.kyron.scraper.pipeline.MergePolicy
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

enum class UpsertOutcome { INSERTED, UPDATED, UNCHANGED }
data class UpsertResult(val outcome: UpsertOutcome, val writes: Int)

class FirestoreRepository(private val db: Firestore) {
    private val collection = Config.firestoreCollection
    private val observations = Config.observationsCollection
    private val metaCollection = "_meta"
    private val writeCounter = AtomicInteger(0)

    val writesUsed: Int get() = writeCounter.get()
    fun hasWriteCapacity(): Boolean = writesUsed <= Config.maxWritesPerRun - 2

    fun healthCheck() {
        val ref = db.collection(metaCollection).document("healthcheck")
        ref.set(mapOf("ts" to System.currentTimeMillis(), "note" to "kyron scraper canary"), SetOptions.merge()).get()
        writeCounter.incrementAndGet()
        if (!ref.get().get().exists()) error("Canary doc not found after write")
    }

    /** Stores a per-source observation and deterministically materializes the canonical product. */
    fun upsert(product: Product): UpsertResult {
        require(hasWriteCapacity()) { "Run write budget exhausted" }
        val canonicalRef = db.collection(collection).document(product.barcode)
        val observationRef = db.collection(observations).document("${product.barcode}_${product.source}")

        val result = db.runTransaction { tx ->
            val currentSnap = tx.get(canonicalRef).get()
            val observationSnap = tx.get(observationRef).get()
            val observationData = product.toMap(includeUpdatedAt = false)
            val observationHash = stableHash(observationData)
            var writes = 0

            if (!observationSnap.exists() || observationSnap.getString("contentHash") != observationHash) {
                tx.set(observationRef, observationData + mapOf(
                    "contentHash" to observationHash,
                    "observedAt" to product.updatedAt,
                ))
                writes++
            }

            val current = currentSnap.toProductOrNull()
            val merged = MergePolicy.merge(current, product)
            val canonicalData = merged.toMap(includeUpdatedAt = false)
            val canonicalHash = stableHash(canonicalData)
            val oldHash = currentSnap.getString("contentHash")
            val outcome = when {
                !currentSnap.exists() -> UpsertOutcome.INSERTED
                oldHash != canonicalHash -> UpsertOutcome.UPDATED
                else -> UpsertOutcome.UNCHANGED
            }
            if (outcome != UpsertOutcome.UNCHANGED) {
                tx.set(canonicalRef, canonicalData + mapOf(
                    "contentHash" to canonicalHash,
                    "updatedAt" to product.updatedAt,
                ))
                writes++
            }
            UpsertResult(outcome, writes)
        }.get()
        writeCounter.addAndGet(result.writes)
        return result
    }

    private fun Product.toMap(includeUpdatedAt: Boolean): Map<String, Any> = buildMap {
        put("barcode", barcode); put("name", name); put("source", source); put("confidence", confidence)
        brand?.let { put("brand", it) }; imageUrl?.let { put("imageUrl", it) }
        category?.let { put("category", it) }; unit?.let { put("unit", it) }
        sourceUrl?.let { put("sourceUrl", it) }
        if (includeUpdatedAt) put("updatedAt", updatedAt)
    }

    private fun DocumentSnapshot.toProductOrNull(): Product? {
        if (!exists()) return null
        val barcode = getString("barcode") ?: return null
        val name = getString("name") ?: return null
        val source = getString("source") ?: "unknown"
        return Product(
            barcode = barcode, name = name, brand = getString("brand"), imageUrl = getString("imageUrl"),
            category = getString("category"), unit = getString("unit"), source = source,
            sourceUrl = getString("sourceUrl"), confidence = getDouble("confidence") ?: 0.0,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }

    private fun stableHash(data: Map<String, Any>): String {
        val canonical = data.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
