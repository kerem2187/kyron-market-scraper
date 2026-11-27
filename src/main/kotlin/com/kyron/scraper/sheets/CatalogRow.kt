package com.kyron.scraper.sheets

import com.kyron.scraper.model.Product
import com.kyron.scraper.pipeline.MergePolicy
import com.kyron.scraper.util.ProductQuality
import java.net.URI

data class CatalogRow(
    val barcode: String,
    val name: String,
    val category: String = "",
    val subcategory: String = "",
    val imageUrl: String = "",
) {
    fun mergeMissingFrom(product: Product): CatalogRow = copy(
        name = name.ifBlank { product.name },
        category = category.ifBlank { product.category.orEmpty() },
        imageUrl = imageUrl.ifBlank { product.imageUrl.orEmpty() },
    )

    fun mergeUpdatedFrom(product: Product): CatalogRow {
        val incomingName = product.name.trim()
        val isOfficialSource = product.source in setOf("migros", "macrocenter", "marketfiyati", "sok")
        val keepIncomingName = incomingName.isNotBlank() &&
            ProductQuality.isMeaningfulName(incomingName) &&
            (name.isBlank() || !ProductQuality.isMeaningfulName(name) ||
                product.source in setOf("migros", "macrocenter") ||
                MergePolicy.nameQualityScore(incomingName) >= MergePolicy.nameQualityScore(name))

        val incomingImg = product.imageUrl?.trim().orEmpty()
        val keepIncomingImage = incomingImg.isNotBlank() &&
            (!isOfficialStoreImage(imageUrl) || isOfficialStoreImage(incomingImg))

        val incomingCat = product.category?.trim().orEmpty()
        val keepIncomingCategory = incomingCat.isNotBlank() &&
            (category.isBlank() || isOfficialSource || !isOfficialStoreImage(imageUrl))

        return copy(
            name = if (keepIncomingName) incomingName else name,
            category = if (keepIncomingCategory) incomingCat else category,
            imageUrl = if (keepIncomingImage) incomingImg else imageUrl,
        )
    }

    companion object {
        private val officialImageHosts = setOf(
            "images.migrosone.com",
            "www.macrocenter.com.tr",
            "images.ceptesok.com",
            "cdn.marketfiyati.org.tr",
        )

        fun isOfficialStoreImage(url: String): Boolean = runCatching {
            URI(url.trim()).host?.lowercase() in officialImageHosts
        }.getOrDefault(false)

        fun fromProduct(product: Product): CatalogRow = CatalogRow(
            barcode = product.barcode,
            name = product.name,
            category = product.category.orEmpty(),
            imageUrl = product.imageUrl.orEmpty(),
        )
    }
}

data class CatalogSyncReport(
    val firestoreProducts: Int,
    val existingRows: Int,
    val inserted: Int,
    val enriched: Int,
    val unchanged: Int,
    val duplicatesRemoved: Int,
    val finalRows: Int,
    val incompleteSkipped: Int = 0,
    val duplicateRowsObserved: Int = 0,
)
