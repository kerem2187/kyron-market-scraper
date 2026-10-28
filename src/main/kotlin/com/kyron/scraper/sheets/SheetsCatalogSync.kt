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
 * Existing non-empty manual values win; only blank cells are filled and new barcodes appended.
 * Never rewrites/compacts the whole table. Every change is read back before success.
 */
class SheetsCatalogSync(private val db: Firestore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val credentials = GoogleCredentials.getApplicationDefault()
        .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))

    fun sync(): CatalogSyncReport {
        require(Config.sheetsSpreadsheetId.isNotBlank()) { "SHEETS_SPREADSHEET_ID is required" }
        val products = loadProducts()
        val existing = readCatalog()
        log.info { "SHEETS_QUALITY phase=before ${CatalogQualityAudit.inspect(existing)} semanticValidation=false imageFetchValidation=false" }
        val plan = CatalogSyncPlan.build(products, existing)
        if (existing.isEmpty()) {
            // Only initializes/rewrites the known header, never clears existing product rows.
            request("PUT", valuesUrl("A1:E1") + "?valueInputOption=RAW", buildJsonObject {
                put("values", buildJsonArray { add(row(*HEADERS.toTypedArray())) })
            }.toString())
        }
        for (chunk in plan.updates.chunked(500)) {
            val body = buildJsonObject {
                put("valueInputOption", "RAW")
                put("data", buildJsonArray {
                    chunk.forEach { change -> add(buildJsonObject {
                        put("range", "'${Config.sheetsCatalogTab}'!${change.column}${change.rowNumber}")
                        put("values", buildJsonArray { add(row(change.value)) })
                    }) }
                })
            }.toString()
            request("POST", "https://sheets.googleapis.com/v4/spreadsheets/${Config.sheetsSpreadsheetId}/values:batchUpdate", body)
        }
        for (chunk in plan.additions.chunked(500)) {
            val body = buildJsonObject {
                put("values", buildJsonArray { chunk.forEach { add(row(it.barcode, it.name, it.category, it.subcategory, it.imageUrl)) } })
            }.toString()
            // Do not blindly retry append after an ambiguous network failure. A new run re-reads keys.
            request("POST", valuesUrl("A:E") + ":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS", body)
        }
        val verified = readCatalog()
        log.info { "SHEETS_QUALITY phase=after ${CatalogQualityAudit.inspect(verified)} semanticValidation=false imageFetchValidation=false" }
        val byBarcode = verified.groupBy { it.row.barcode }
        plan.additions.forEach { expected ->
            check(byBarcode[expected.barcode].orEmpty().any { it.row == expected }) {
                "KATALOG read-back failed for appended barcode ${expected.barcode}"
            }
        }
        plan.updates.forEach { expected ->
            val actual = verified.firstOrNull { it.rowNumber == expected.rowNumber }?.row
            val value = when (expected.column) { "B" -> actual?.name; "C" -> actual?.category; "E" -> actual?.imageUrl; else -> null }
            check(value == expected.value) { "KATALOG read-back failed at ${expected.column}${expected.rowNumber}" }
        }
        return CatalogSyncReport(products.size, existing.size, plan.inserted, plan.enriched, plan.unchanged,
            duplicatesRemoved = 0, finalRows = verified.size, incompleteSkipped = plan.incompleteSkipped,
            duplicateRowsObserved = plan.duplicateRows)
            .also { log.info { "SHEETS_SYNC $it verified=true verificationScope=plannedWritesOnly catalogQualityGuaranteed=false" } }
    }

    private fun loadProducts(): List<Product> = db.collection(Config.firestoreCollection).get().get().documents
        .mapNotNull { it.toProductOrNull() }

    private fun readCatalog(): List<IndexedCatalogRow> {
        val response = request("GET", valuesUrl("A1:E"))
        val values = json.parseToJsonElement(response).jsonObject["values"]?.jsonArray ?: JsonArray(emptyList())
        if (values.isEmpty()) return emptyList()
        val header = values.first().jsonArray.map { it.jsonPrimitive.content }
        val expected = HEADERS
        require(header.take(expected.size) == expected) {
            "Unexpected KATALOG headers. Expected=$expected actual=${header.take(expected.size)}"
        }
        return values.drop(1).mapIndexedNotNull { index, element ->
            val row = element.jsonArray.map { it.jsonPrimitive.content }
            val barcode = row.getOrElse(0) { "" }.trim()
            if (barcode.isBlank()) null else IndexedCatalogRow(index + 2, CatalogRow(
                barcode = barcode,
                name = row.getOrElse(1) { "" }.trim(),
                category = row.getOrElse(2) { "" }.trim(),
                subcategory = row.getOrElse(3) { "" }.trim(),
                imageUrl = row.getOrElse(4) { "" }.trim(),
            ))
        }
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
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "Sheets API HTTP $code" }
            return response
        } finally { connection.disconnect() }
    }

    companion object {
        val HEADERS = listOf("BARKOD", "URUNADI", "KategoriAdi", "AltKategoriAdi", "UrunGorseli")
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
