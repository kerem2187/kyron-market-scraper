package com.kyron.scraper.sheets

/** Exact dictionary membership only. Never infers a category from a name, image or foreign label. */
class CatalogTaxonomy private constructor(private val pairs: Set<Pair<String, String>>) {
    private val categories = pairs.map { it.first }.toSet()
    val pairCount: Int get() = pairs.size

    fun acceptedCategory(source: String?, existingSubcategory: String = ""): String? {
        val category = source?.trim()?.takeIf { it in categories } ?: return null
        val subcategory = existingSubcategory.trim()
        return category.takeIf { subcategory.isEmpty() || (category to subcategory) in pairs }
    }

    companion object {
        /** Default deny when a caller has not supplied a reviewed dictionary. */
        val EMPTY = CatalogTaxonomy(emptySet())
        val HEADERS = listOf("KategoriID", "KategoriAdi", "AltKategoriAdi")

        fun fromSheetValues(values: List<List<String>>): CatalogTaxonomy {
            require(values.firstOrNull()?.take(3) == HEADERS) { "Unexpected category dictionary headers" }
            val ids = hashSetOf<String>()
            val pairs = values.drop(1).filter { row -> row.take(3).any { it.isNotBlank() } }.map { row ->
                val fields = (0..2).map { row.getOrElse(it) { "" }.trim() }
                require(fields.all { it.isNotEmpty() }) { "Incomplete category dictionary row" }
                require(ids.add(fields[0])) { "Duplicate category dictionary ID" }
                fields[1] to fields[2]
            }.toSet()
            require(pairs.isNotEmpty()) { "Category dictionary is empty" }
            return CatalogTaxonomy(pairs)
        }
    }
}
