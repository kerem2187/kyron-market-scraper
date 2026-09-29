package com.kyron.scraper.sheets

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.kyron.scraper.config.Config
import com.kyron.scraper.model.Product
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val log = KotlinLogging.logger {}

/**
 * Upserts the canonical Firestore product catalog into the existing AppSheet KATALOG tab.
 * Existing non-empty manual values win; Firestore only fills blanks and appends new barcodes.
 */
class SheetsCatalogSync(private val db: Firestore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val credentials = GoogleCredentials.getApplicationDefault()
        .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))

    fun sync(): CatalogSyncReport {
        require(Config.sheetsSpreadsheetId.isNotBlank()) { "SHEETS_SPREADSHEET_ID is required" }
        val products = loadProducts()
        val existing = readCatalog()
        val ordered = LinkedHashMap<String, CatalogRow>()
        var duplicates = 0
        existing.forEach { row ->
            if (row.barcode.isBlank()) return@forEach
            if (ordered.putIfAbsent(row.barcode, row) != null) duplicates++
        }

        var inserted = 0
        var enriched = 0
        var unchanged = 0
        for (product in products.sortedBy { it.barcode }) {
            val current = ordered[product.barcode]
            if (current == null) {
                ordered[product.barcode] = CatalogRow.fromProduct(product)
                inserted++
            } else {
                val merged = current.mergeMissingFrom(product)
                ordered[product.barcode] = merged
                if (merged == current) unchanged++ else enriched++
            }
        }
        writeCatalog(ordered.values.toList())
        return CatalogSyncReport(
            firestoreProducts = products.size,
            existingRows = existing.size,
            inserted = inserted,
            enriched = enriched,
            unchanged = unchanged,
            duplicatesRemoved = duplicates,
            finalRows = ordered.size,
        ).also { log.info { "SHEETS_SYNC $it" } }
    }

    private fun loadProducts(): List<Product> = db.collection(Config.firestoreCollection).get().get().documents
        .mapNotNull { it.toProductOrNull() }

    private fun readCatalog(): List<CatalogRow> {
        val response = request("GET", valuesUrl("A1:E"))
        val values = json.parseToJsonElement(response).jsonObject["values"]?.jsonArray ?: JsonArray(emptyList())
        if (values.isEmpty()) return emptyList()
        val header = values.first().jsonArray.map { it.jsonPrimitive.content }
        val expected = listOf("BARKOD", "URUNADI", "KategoriAdi", "AltKategoriAdi", "UrunGorseli")
        require(header.take(expected.size) == expected) {
            "Unexpected KATALOG headers. Expected=$expected actual=${header.take(expected.size)}"
        }
        return values.drop(1).mapNotNull { element ->
            val row = element.jsonArray.map { it.jsonPrimitive.content }
            val barcode = row.getOrElse(0) { "" }.trim()
            if (barcode.isBlank()) null else CatalogRow(
                barcode = barcode,
                name = row.getOrElse(1) { "" }.trim(),
                category = row.getOrElse(2) { "" }.trim(),
                subcategory = row.getOrElse(3) { "" }.trim(),
                imageUrl = row.getOrElse(4) { "" }.trim(),
            )
        }
    }

    private fun writeCatalog(rows: List<CatalogRow>) {
        val allRows = buildJsonArray {
            add(row("BARKOD", "URUNADI", "KategoriAdi", "AltKategoriAdi", "UrunGorseli"))
            rows.forEach { add(row(it.barcode, it.name, it.category, it.subcategory, it.imageUrl)) }
        }
        val body = buildJsonObject {
            put("range", "'${Config.sheetsCatalogTab}'!A1:E${rows.size + 1}")
            put("majorDimension", "ROWS")
            put("values", allRows)
        }.toString()
        request("PUT", valuesUrl("A1:E${rows.size + 1}") + "?valueInputOption=RAW", body)
    }

    private fun row(vararg values: String): JsonArray = buildJsonArray { values.forEach(::add) }

    private fun valuesUrl(range: String): String {
        val a1 = "'${Config.sheetsCatalogTab}'!$range"
        val encoded = URLEncoder.encode(a1, StandardCharsets.UTF_8).replace("+", "%20")
        return "https://sheets.googleapis.com/v4/spreadsheets/${Config.sheetsSpreadsheetId}/values/$encoded"
    }

    private fun request(method: String, url: String, body: String? = null): String {
        credentials.refreshIfExpired()
        val token = credentials.accessToken?.tokenValue ?: credentials.refreshAccessToken().tokenValue
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Sheets API HTTP $code: ${response.take(500)}")
        return response
    }

    private fun DocumentSnapshot.toProductOrNull(): Product? {
        val barcode = getString("barcode")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = getString("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Product(
            barcode = barcode,
            name = name,
            brand = getString("brand"),
            imageUrl = getString("imageUrl"),
            category = getString("category"),
            unit = getString("unit"),
            source = getString("source") ?: "unknown",
            sourceUrl = getString("sourceUrl"),
            confidence = getDouble("confidence") ?: 0.0,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }
}
