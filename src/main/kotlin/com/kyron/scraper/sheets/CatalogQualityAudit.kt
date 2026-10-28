package com.kyron.scraper.sheets

import com.kyron.scraper.util.Ean
import com.kyron.scraper.util.ProductQuality

/** Read-only structural audit. No classification guesses, image requests or row mutations. */
data class CatalogQualityAudit(
    val rows: Int,
    val missingNames: Int,
    val missingImages: Int,
    val nonHttpsImageReferences: Int,
    val missingCategories: Int,
    val missingSubcategories: Int,
    val orphanSubcategories: Int,
    val invalidBarcodeFormatsOrChecksums: Int,
    val duplicateCanonicalRows: Int,
) {
    companion object {
        fun inspect(rows: List<IndexedCatalogRow>): CatalogQualityAudit {
            fun canonical(code: String): String? = code
                .takeIf { it.matches(Regex("[0-9]{8}|[0-9]{12,13}")) }
                ?.let(Ean::normalize)
            val records = rows.map { it.row }
            val keys = records.mapNotNull { canonical(it.barcode) }
            return CatalogQualityAudit(
                rows = records.size,
                missingNames = records.count { it.name.isBlank() },
                missingImages = records.count { it.imageUrl.isBlank() },
                // AppSheet may support relative image paths: this is a review count, not an error.
                nonHttpsImageReferences = records.count {
                    it.imageUrl.isNotBlank() && !ProductQuality.isHttpsUrl(it.imageUrl)
                },
                missingCategories = records.count { it.category.isBlank() },
                missingSubcategories = records.count { it.subcategory.isBlank() },
                orphanSubcategories = records.count { it.category.isBlank() && it.subcategory.isNotBlank() },
                invalidBarcodeFormatsOrChecksums = records.count { canonical(it.barcode) == null },
                duplicateCanonicalRows = keys.size - keys.toSet().size,
            )
        }
    }
}
