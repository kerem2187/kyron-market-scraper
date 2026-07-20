package com.kyron.scraper.model

import kotlinx.serialization.Serializable

/** Canonical product record stored in Firestore (document id = barcode). */
@Serializable
data class Product(
    val barcode: String,            // EAN-13, primary key
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,   // source image URL (not rehosted — keeps it free)
    val category: String? = null,
    val unit: String? = null,       // e.g. "500 g", "1 L"
    val source: String,             // chain slug, e.g. "migros", or "off"
    val sourceUrl: String? = null,
    val confidence: Double = 0.5,
    val updatedAt: Long = System.currentTimeMillis()
)
