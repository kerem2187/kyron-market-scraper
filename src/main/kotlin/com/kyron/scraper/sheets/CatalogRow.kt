package com.kyron.scraper.sheets

import com.kyron.scraper.model.Product

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

    companion object {
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
