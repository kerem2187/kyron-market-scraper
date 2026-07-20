package com.kyron.scraper.store

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.kyron.scraper.config.Config
import com.kyron.scraper.model.Product

/**
 * Firestore-backed product library. Document id = barcode.
 *
 * Write-only (merge) upsert: we intentionally do NOT read-before-write so the
 * job stays inside the Firestore free (Spark) daily quota.
 */
class FirestoreRepository(private val db: Firestore) {
    private val collection = Config.firestoreCollection

    /** Separate metadata collection so the canary never pollutes 'products'. */
    private val metaCollection = "_meta"

    /**
     * Writes and reads back a canary doc to prove connectivity + permissions.
     *
     * The canary lives in a dedicated '_meta' collection (doc 'healthcheck'),
     * NOT in 'products', so the product library stays clean. It still proves
     * the exact project + database + service-account permissions are correct.
     */
    fun healthCheck() {
        val id = "healthcheck"
        db.collection(metaCollection).document(id)
            .set(
                mapOf(
                    "ts" to System.currentTimeMillis(),
                    "note" to "kyron scraper canary",
                ),
                SetOptions.merge(),
            ).get()
        val exists = db.collection(metaCollection).document(id).get().get().exists()
        if (!exists) error("Canary doc not found after write")
    }

    fun upsert(product: Product) {
        val data = mapOf(
            "barcode" to product.barcode,
            "name" to product.name,
            "brand" to product.brand,
            "imageUrl" to product.imageUrl,
            "category" to product.category,
            "unit" to product.unit,
            "source" to product.source,
            "sourceUrl" to product.sourceUrl,
            "confidence" to product.confidence,
            "updatedAt" to product.updatedAt,
        )
        db.collection(collection).document(product.barcode)
            .set(data, SetOptions.merge()).get()
    }
}
