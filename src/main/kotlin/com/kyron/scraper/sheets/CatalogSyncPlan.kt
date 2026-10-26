package com.kyron.scraper.sheets

import com.kyron.scraper.model.Product
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.util.ProductQuality

data class IndexedCatalogRow(val rowNumber: Int, val row: CatalogRow)
data class CatalogCellUpdate(val rowNumber: Int, val column: String, val value: String)
data class CatalogSyncPlan(
    val updates: List<CatalogCellUpdate>, val additions: List<CatalogRow>,
    val inserted: Int, val enriched: Int, val unchanged: Int, val incompleteSkipped: Int,
    val duplicateRows: Int,
) {
    companion object {
        /** Never compact/delete rows, rewrite manual values, or touch columns outside A:E. */
        fun build(products: List<Product>, rows: List<IndexedCatalogRow>): CatalogSyncPlan {
            val first = rows.groupBy { it.row.barcode }.mapValues { it.value.first() }
            val updates = mutableListOf<CatalogCellUpdate>(); val additions = mutableListOf<CatalogRow>()
            val processed = hashSetOf<String>()
            var enriched = 0; var unchanged = 0; var incomplete = 0
            for (product in products.distinctBy { it.barcode }.sortedBy { it.barcode }) {
                val complete = ProductQuality.normalized(RawProduct(product.name, barcode = product.barcode,
                    sourceImageUrl = product.imageUrl))
                if (complete == null) { incomplete++; continue }
                val normalized = product.copy(barcode = complete.barcode!!, name = complete.name, imageUrl = complete.sourceImageUrl)
                if (!processed.add(normalized.barcode)) continue
                val current = first[normalized.barcode]
                if (current == null) { additions.add(CatalogRow.fromProduct(normalized)); continue }
                val merged = current.row.mergeMissingFrom(normalized)
                val fields = listOf("B" to (current.row.name to merged.name), "C" to (current.row.category to merged.category),
                    "E" to (current.row.imageUrl to merged.imageUrl))
                val changes = fields.filter { it.second.first != it.second.second }
                changes.forEach { (col, values) -> updates.add(CatalogCellUpdate(current.rowNumber, col, values.second)) }
                if (changes.isEmpty()) unchanged++ else enriched++
            }
            return CatalogSyncPlan(updates, additions, additions.size, enriched, unchanged, incomplete,
                rows.size - first.size)
        }
    }
}
