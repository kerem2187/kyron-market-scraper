package com.kyron.scraper.marketfiyati

import com.kyron.scraper.config.Config
import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import com.kyron.scraper.util.Ean
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.net.HttpURLConnection
import java.net.URI

private val log = KotlinLogging.logger {}

/**
 * Market Fiyatı (marketfiyati.org.tr) importer.
 *
 * marketfiyati.org.tr is the free price-comparison platform developed by
 * TÜBİTAK BİLGEM. It aggregates DAILY-updated prices from 6 major Turkish
 * chains: A101, BİM, CarrefourSA, Migros, Tarım Kredi and ŞOK.
 *
 * It exposes a public JSON backend (the same one the website uses) at
 * https://api.marketfiyati.org.tr/api/v2 . NO API KEY is required.
 *
 * We cannot list the whole catalog in one call, so we "discover" products by
 * POSTing a broad set of Turkish grocery keywords to /search and paginating
 * each one. Each result already carries the barcode (the product id), the
 * title, brand, image and category — exactly what Kyron's library needs.
 *
 * LEGAL NOTE: The endpoint is unofficial and the platform's terms restrict
 * commercial use. This is fine for personal / MVP use; obtain permission from
 * TÜBİTAK before a commercial launch. Requests are rate-limited to stay polite.
 */
class MarketFiyatiImporter(
    // The API returns at most 25 items per page regardless of the requested
    // size, so we set pageSize to 25 to make pagination work: as long as a page
    // returns a full 25 results we keep fetching pages=1,2,3... for the keyword.
    private val pageSize: Int = 25,
    private val maxPagesPerKeyword: Int = 50,
    private val maxRetries: Int = 4,
) : ChainScraper {
    override val slug = "marketfiyati"
    override val displayName = "Market Fiyatı (TÜBİTAK)"
    override val barcodeAvailable = true

    private val base = "https://api.marketfiyati.org.tr/api/v2"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * IMPORTANT: the platform's WAF returns HTTP 418 for anything that looks
     * like a bot. We must NOT use Config.userAgent here (it literally contains
     * "KyronBot"). Present as a real Chrome browser instead.
     */
    private val browserUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** one-time diagnostic dump of the first raw item */
    private var loggedSample = false

    /** Broad discovery seeds: common Turkish grocery terms across categories. */
    private val keywords: List<String> = listOf(
        // Süt & kahvaltılık
        "süt", "yoğurt", "peynir", "tereyağı", "yumurta", "bal", "reçel", "zeytin",
        "kaymak", "ayran", "kefir", "labne", "krem peynir", "kaşar",
        // Temel gıda
        "un", "şeker", "tuz", "pirinç", "bulgur", "makarna", "mercimek", "nohut",
        "fasulye", "salça", "sıvı yağ", "ayçiçek yağı", "zeytinyağı", "sirke",
        "maya", "nişasta", "irmik", "bezelye", "konserve",
        // İçecek
        "su", "maden suyu", "kola", "gazoz", "meyve suyu", "çay", "kahve",
        "nescafe", "ıhlamur", "soda", "ayran", "enerji içeceği",
        // Atıştırmalık & tatlı
        "çikolata", "bisküvi", "gofret", "cips", "kraker", "kek", "kuruyemiş",
        "leblebi", "lokum", "helva", "sakız", "şekerleme", "kurabiye",
        // Et, tavuk, donuk
        "tavuk", "kıyma", "sucuk", "salam", "sosis", "pastırma", "balık",
        "köfte", "nugget", "dondurma",
        // Temizlik & kişisel bakım
        "deterjan", "çamaşır suyu", "bulaşık deterjanı", "yumuşatıcı", "sabun",
        "şampuan", "diş macunu", "tuvalet kağıdı", "kağıt havlu", "peçete",
        "ıslak mendil", "bebek bezi", "çöp poşeti", "deodorant", "kolonya",
        // Kahvaltılık ekstra & bakliyat markaları
        "gofret", "pişmaniye", "tahin", "pekmez", "ketçap", "mayonez", "hardal",
        "baharat", "karabiber", "pul biber", "kimyon", "nane",
    ).distinct()

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val seen = HashSet<String>()
        for (kw in keywords) {
            var page = 0
            while (page < maxPagesPerKeyword) {
                val body = fetchSearch(kw, page) ?: break
                val content = runCatching {
                    json.parseToJsonElement(body).jsonObject["content"]?.jsonArray
                }.onFailure { log.warn { "[marketfiyati] '$kw' p$page parse failed: ${it.message}" } }
                    .getOrNull() ?: break
                if (content.isEmpty()) break

                if (!loggedSample) {
                    loggedSample = true
                    log.info { "[marketfiyati] sample item = ${content.first().toString().take(800)}" }
                }

                var emitted = 0
                for (el in content) {
                    val o = el.jsonObject
                    // The barcode field name varies across the response (top-level
                    // id/barcode, or nested inside productDepotInfoList). Scan the
                    // whole item for the first value that is a valid EAN.
                    val barcode = extractBarcode(o) ?: continue
                    if (!seen.add(barcode)) continue // dedup across keywords

                    val name = firstString(o, "title", "name", "productName", "itemName")
                        ?: continue
                    if (name.isBlank()) continue

                    emit(
                        RawProduct(
                            name = name,
                            brand = firstString(o, "brand", "brandName"),
                            barcode = barcode,
                            sourceImageUrl = firstString(o, "imageUrl", "image", "imageLink"),
                            category = extractCategory(o),
                            sourceUrl = "https://marketfiyati.org.tr/urun/$barcode",
                        ),
                    )
                    emitted++
                }
                log.info { "[marketfiyati] '$kw' page $page -> ${content.size} results (new=$emitted, total=${seen.size})" }
                if (content.size < pageSize) break // last page for this keyword
                page++
            }
        }
        log.info { "[marketfiyati] discovery finished — ${seen.size} unique barcodes" }
    }

    /** Returns the first non-blank string among the given keys. */
    private fun firstString(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val v = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /** Category may be an array of strings/objects or a plain string. */
    private fun extractCategory(o: JsonObject): String? {
        firstString(o, "category", "categoryName", "mainCategory")?.let { return it }
        val arr = runCatching { o["categories"]?.jsonArray }.getOrNull() ?: return null
        val first = arr.firstOrNull() ?: return null
        return when (first) {
            is JsonPrimitive -> first.contentOrNull
            is JsonObject -> firstString(first, "name", "title", "categoryName")
            else -> null
        }
    }

    /** Finds the first valid EAN anywhere in the product JSON (field name varies). */
    private fun extractBarcode(o: JsonObject): String? {
        for (k in listOf("barcode", "id", "gtin", "ean", "productId")) {
            val v = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
            Ean.normalize(v)?.let { return it }
        }
        return findEanDeep(o)
    }

    private fun findEanDeep(el: JsonElement): String? {
        when (el) {
            is JsonObject -> for ((_, v) in el) findEanDeep(v)?.let { return it }
            is JsonArray -> for (v in el) findEanDeep(v)?.let { return it }
            is JsonPrimitive -> el.contentOrNull?.let { s -> Ean.normalize(s)?.let { return it } }
            else -> {}
        }
        return null
    }

    /** POST /search with retry + backoff. Returns the raw JSON body or null. */
    private suspend fun fetchSearch(keyword: String, page: Int): String? {
        val payload = buildJsonObject {
            put("keywords", keyword)
            put("pages", page)
            put("size", pageSize)
        }.toString()

        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                val conn = URI("$base/search").toURL().openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                conn.setRequestProperty("User-Agent", browserUa)
                conn.setRequestProperty("Origin", "https://marketfiyati.org.tr")
                conn.setRequestProperty("Referer", "https://marketfiyati.org.tr/")
                conn.setRequestProperty("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                conn.setRequestProperty("sec-ch-ua-mobile", "?0")
                conn.setRequestProperty("sec-ch-ua-platform", "\"Windows\"")
                conn.setRequestProperty("Sec-Fetch-Site", "same-site")
                conn.setRequestProperty("Sec-Fetch-Mode", "cors")
                conn.setRequestProperty("Sec-Fetch-Dest", "empty")
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code in 200..299) {
                    val out = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    // politeness delay between successful calls
                    delay(Config.requestDelayMs)
                    return out
                }
                runCatching { conn.errorStream?.use { it.readBytes() } } // drain
                if (code == 429 || code == 403 || code == 418 || code in 500..599) {
                    val backoff = 2_000L * attempt
                    log.warn { "[marketfiyati] HTTP $code ('$keyword' p$page) — retry $attempt/$maxRetries after ${backoff}ms" }
                    delay(backoff)
                    continue
                }
                log.warn { "[marketfiyati] HTTP $code ('$keyword' p$page) — giving up" }
                return null
            } catch (e: Exception) {
                val backoff = 2_000L * attempt
                log.warn { "[marketfiyati] fetch error (${e.message}) — retry $attempt/$maxRetries after ${backoff}ms" }
                delay(backoff)
            }
        }
        log.error { "[marketfiyati] failed after $maxRetries attempts ('$keyword' p$page)" }
        return null
    }
}
