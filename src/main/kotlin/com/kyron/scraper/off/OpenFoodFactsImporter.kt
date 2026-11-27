package com.kyron.scraper.off

import com.kyron.scraper.model.RawProduct
import com.kyron.scraper.scraper.ChainScraper
import com.kyron.scraper.scraper.SourceStatus
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

class OpenFoodFactsImporter(
    private val country: String = "turkey",
    private val pageSize: Int = 100,
    private val maxPages: Int = 200,
    private val maxRetries: Int = 4,
    private val client: OffApiClient = OffApiClient(maxAttempts = maxRetries),
    private val onPage: (Int, Int) -> Unit = { _, _ -> },
    private val baseUrl: String = "https://world.openfoodfacts.org",
    override val slug: String = "off",
    override val displayName: String = "Open Food Facts",
    private val startPage: Int = 1,
) : ChainScraper {
    override val barcodeAvailable = true
    override val status = SourceStatus.VERIFIED
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val effectiveStartPage = if (startPage != 1) startPage else (System.getenv("OFF_RUNTIME_START_PAGE")?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
        var page = effectiveStartPage
        var done = false
        var consecutiveErrors = 0           
        if (slug == "off" && maxPages >= 50) {
               val priorityBrands = listOf("ulker", "eti", "pinar", "sutas", "icim", "sek", "torku", "polonez", "maret", "namet", "banvit", "dardanel", "barilla", "filiz", "yayla", "reis", "tat", "tukas", "yudum", "komili", "taris", "caykur", "dogus", "lipton", "nescafe", "jacobs", "knorr", "calve", "heinz", "dr-oetker", "pakmaya", "tadim", "peyman", "koska", "balparmak", "marmarabirlik", "nestle", "kinder", "ferrero", "milka", "lindt", "pringles", "doritos", "lays", "migros", "macrocenter", "danone", "activia", "eker", "bahcivan", "muratbey", "tahsildaroglu", "kiri", "president", "lavachequirit", "superfresh", "feast", "uno", "wasa", "nutella", "oreo", "haribo", "toblerone", "kraft", "hellmanns", "kuhne", "de-cecco", "monini", "apikoglu",, "cumhuriyet", "cumhuriyet-sucuklari", "sahin", "besler", "danet", "erpilic", "senpilic", "beypilic", "keskinoglu", "gedik", "lezita", "aytac", "ikbal", "coskun", "cem", "berrak", "serel", "yenigun", "seyidoglu", "tamek", "burcu", "oncu", "penguen", "melis", "kenton", "bagdat", "arifoglu", "dost", "birsah", "mis", "tarim-kredi", "kebir", "yorukoglu", "teksut", "altinkilic", "gunesoglu", "ciftlik", "akbel", "puck", "philadelphia", "babybel", "galbani", "parmalat", "arla", "lurpak", "kerrygold", "leerdammer", "castello", "alpro", "oatly", "nuhun-ankara", "pastavilla", "oba", "mutlu", "selva", "arbella", "piyale", "soke", "sinangil", "duru", "hasata", "kirlangic", "biryag", "orkide", "zer", "kristal", "karali", "ofcay", "beta-tea", "ahmad-tea", "twinings", "tchibo", "lavazza", "illy", "starbucks", "mehmet-efendi", "kurukahveci-mehmet-efendi", "kahve-dunyasi", "ritter-sport", "solen", "biscolata", "patiswiss", "bifa", "saray", "maggi", "tabasco", "maille", "bonduelle", "rio-mare", "calvo", "mutti", "cirio", "divella", "rummo", "garofalo", "la-molisana", "quaker", "kelloggs", "fellas", "zuber", "bertolli")
                       for (brandTag in priorityBrands.shuffled()) { for (brandPage in 1..3) {
                                val brandUrl = "$baseUrl/api/v2/search?brands_tags=$brandTag&fields=code,product_name,product_name_tr,brands,image_url,image_front_url,image_small_url,categories&page=$brandPage&page_size=100"
                  val brandProducts = runCatching { json.parseToJsonElement(client.get(brandUrl)).jsonObject["products"]?.jsonArray }.onFailure { log.warn { "[$slug] brand=$brandTag page $brandPage failed: ${it.message}" } }.getOrNull() ?: continue
                  if (brandProducts.isEmpty()) break
  for (element in brandProducts) { val o = element.jsonObject; val barcode = o["code"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue; val name = o["product_name_tr"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: o["product_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue; val imageUrl = o["image_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: o["image_front_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: o["image_small_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }; emit(RawProduct(name = name, brand = o["brands"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }, barcode = barcode, sourceImageUrl = imageUrl, category = o["categories"]?.jsonPrimitive?.contentOrNull?.split(",")?.firstOrNull()?.trim(), sourceUrl = "$baseUrl/product/$barcode")) }
  if (brandProducts.size < 100) break } } }
        while (page < effectiveStartPage + maxPages) {
            val url = "$baseUrl/api/v2/search" +
                "?countries_tags_en=$country&fields=code,product_name,product_name_tr,brands,image_url,image_front_url,image_small_url,categories" +
                "&page=$page&page_size=$pageSize"
          val products = runCatching {
                val body = client.get(url)
                json.parseToJsonElement(body).jsonObject["products"]?.jsonArray
                    ?: error("[$slug] Invalid API response on page $page")
            }.onFailure {
                consecutiveErrors++
                log.warn { "[$slug] page $page failed ($consecutiveErrors/3): ${it.message}" }
                if (consecutiveErrors >= 3) throw it
            }.getOrNull()
            if (products == null) {
                page++
                continue
            }
            consecutiveErrors = 0
            onPage(page, products.size)
            if (products.isEmpty()) { done = true; break }
            for (element in products) {
                val o = element.jsonObject
                val barcode = o["code"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val name = o["product_name_tr"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                    ?: o["product_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val imageUrl = o["image_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                    ?: o["image_front_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                    ?: o["image_small_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                emit(
                    RawProduct(
                        name = name,
                        brand = o["brands"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                        barcode = barcode,
                        sourceImageUrl = imageUrl,
                        category = o["categories"]?.jsonPrimitive?.contentOrNull?.split(",")?.firstOrNull()?.trim(),
                        sourceUrl = "$baseUrl/product/$barcode",
                    )
                )
            }
            log.info { "[$slug] page $page -> ${products.size} products" }
            if (products.size < pageSize) { done = true; break }
            page++ // OffApiClient paces every request, including retry attempts.
        }
        println("${slug.uppercase()}_COVERAGE pages=${minOf(page, maxPages)} exhausted=$done fullCatalogGuaranteed=false")

                       }
}
