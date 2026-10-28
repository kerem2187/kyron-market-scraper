package com.kyron.scraper.preflight

import com.google.auth.oauth2.GoogleCredentials
import com.kyron.scraper.config.Config
import com.kyron.scraper.sheets.CatalogTaxonomy
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Diagnostic only: two Sheets GETs with a read-only scoped token. No Firestore or sync initialization. */
object CatalogReadOnlyPreflight {
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty()) { "No arguments accepted" }
        val id = Config.sheetsSpreadsheetId
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Valid SHEETS_SPREADSHEET_ID is required" }
        val credentials = GoogleCredentials.getApplicationDefault()
            .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets.readonly"))
        credentials.refreshIfExpired()
        val token = credentials.accessToken?.tokenValue ?: credentials.refreshAccessToken().tokenValue
        fun read(tab: String, range: String): List<List<String>> {
            val a1 = "'${tab.replace("'", "''")}'!$range"
            val encoded = URLEncoder.encode(a1, StandardCharsets.UTF_8).replace("+", "%20")
            val conn = URI("https://sheets.googleapis.com/v4/spreadsheets/$id/values/$encoded")
                .toURL().openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                check(conn.responseCode == 200) { "Read-only Sheets preflight HTTP ${conn.responseCode}" }
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return Json.parseToJsonElement(response).jsonObject["values"]?.jsonArray
                    ?.map { row -> row.jsonArray.map { it.jsonPrimitive.content } }.orEmpty()
            } finally { conn.disconnect() }
        }
        val headers = read(Config.sheetsCatalogTab, "A1:E1").firstOrNull()
        check(headers == listOf("BARKOD", "URUNADI", "KategoriAdi", "AltKategoriAdi", "UrunGorseli")) {
            "Unexpected catalog headers"
        }
        val taxonomy = CatalogTaxonomy.fromSheetValues(read(Config.sheetsCategoryTab, "A1:C"))
        println("CATALOG_READONLY_OK headers=true taxonomyPairs=${taxonomy.pairCount} scope=spreadsheets.readonly requests=2 writes=0 firestoreInitialized=false")
    }
}
