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

    private val priorityBrands = listOf(
        "pinar", "sutas", "icim", "sek", "torku", "polonez", "maret", "namet", "banvit", "bolca",
        "bahcivan", "muratbey", "tahsildaroglu", "ekici", "yorsan", "kebir", "kaanlar", "apikoglu",
        "cumhuriyet-sucuklari", "ikbal", "gedik", "danone", "activia", "eker", "actimel",
        "president", "kiri", "la-vache-qui-rit", "philadelphia", "galbani", "castello", "arla", "kerrygold",
        "barilla", "filiz", "pastavilla", "ankara-makarna", "mutlu-makarna", "arbella", "veronelli", "de-cecco",
        "yayla", "reis", "duru-bulgur", "tat", "tukas", "penguen", "burcu", "oncu", "sera", "berrak", "tamek",
        "dardanel", "superfresh", "feast", "yudum", "komili", "taris", "kristal", "kirlangic", "orkide", "monini",
        "bertolli", "knorr", "calve", "heinz", "hellmanns", "kuhne", "kemal-kukrer", "fersan", "bolbol", "develey",
        "tabasco", "kikkoman", "amoy", "dr-oetker", "pakmaya", "kent-boringer", "bagdat-baharat", "arifoglu",
        "marmarabirlik", "fora", "cem-zeytin", "balparmak", "anavarza", "koska", "seyidoglu", "fiskobirlik",
        "sarelle", "nutella", "kelloggs", "nestle", "eti-lifalif", "wefood", "fropie", "zuber", "nora", "sinangil",
        "soke", "uno", "wasa",
        "caykur", "dogus", "lipton", "beta-tea", "ahmad-tea", "twinings", "nescafe", "jacobs", "tchibo",
        "kurukahveci-mehmet-efendi", "kahve-dunyasi", "starbucks", "lavazza", "illy", "ulker", "eti",
        "tadim", "peyman", "simsek", "saray", "solen", "kinder", "ferrero", "milka", "lindt", "toblerone",
        "godiva", "haribo", "oreo", "pringles", "doritos", "ruffles", "lays", "cheetos", "cerezos",
        "erikli", "beypazari", "uludag", "sirma", "hayat", "saka", "dimes", "cappy", "fuse-tea",
        "algida", "golf",
        "la-molisana", "rummo", "mutti", "maille", "lee-kum-kee", "loacker", "bahlsen", "walkers", "st-dalfour", "bonne-maman",
        "maldon", "san-pellegrino", "perrier", "evian", "acqua-panna", "fever-tree", "finn-crisp", "schar", "alpro", "oatly",
        "migros", "m-life", "macrocenter", "anadolu-lezzetleri", "uzman-kasap", "aptamil", "bebelac", "hero-baby",
        "hipp", "ari", "selpak", "solo", "familia", "sofia", "papia", "maylo", "sleepy", "prima", "molfix",
        "colgate", "sensodyne", "signal", "oral-b", "pantene", "elidor", "head-shoulders", "clear", "gliss",
        "dove", "nivea", "garnier", "gillette", "duru", "activex", "dalin", "fairy", "pril", "finish",
        "ariel", "omo", "persil", "perwoll", "yumos", "vernel", "bingo", "domestos", "cif", "asperox",
        "vanish", "koroplast", "parex", "whiskas", "felix", "purina", "pro-plan",
    )

    private suspend fun emitProductsFromArray(products: JsonArray, emit: suspend (RawProduct) -> Unit) {
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
    }

    override suspend fun scrape(emit: suspend (RawProduct) -> Unit) {
        val effectiveStartPage = if (startPage != 1) startPage else (System.getenv("OFF_RUNTIME_START_PAGE")?.toIntOrNull()?.coerceAtLeast(1) ?: 1)

        if (slug == "off" && maxPages >= 50) {
            for (brand in priorityBrands.shuffled()) {
                for (brandPage in 1..3) {
                    val brandUrl = "$baseUrl/api/v2/search" +
                        "?brands_tags=$brand&fields=code,product_name,product_name_tr,brands,image_url,image_front_url,image_small_url,categories" +
                        "&page=$brandPage&page_size=$pageSize"
                    val products = runCatching {
                        val body = client.get(brandUrl)
                        json.parseToJsonElement(body).jsonObject["products"]?.jsonArray
                            ?: error("[$slug] Invalid API response for brand $brand on page $brandPage")
                    }.onFailure {
                        log.warn { "[$slug] brand $brand page $brandPage failed: ${it.message}" }
                    }.getOrNull() ?: break
                    if (products.isEmpty()) break
                    emitProductsFromArray(products, emit)
                    log.info { "[$slug] brand=$brand page=$brandPage -> ${products.size} products" }
                    if (products.size < pageSize) break
                }
            }
        }

        var page = effectiveStartPage
        var done = false
        var consecutiveErrors = 0
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
                log.warn { "[$slug] page $page failed (consecutiveErrors=$consecutiveErrors): ${it.message}" }
                if (consecutiveErrors >= 3) throw it
            }.getOrNull()
            if (products == null) {
                page++
                continue
            }
            consecutiveErrors = 0
            onPage(page, products.size)
            if (products.isEmpty()) { done = true; break }
            emitProductsFromArray(products, emit)
            log.info { "[$slug] page $page -> ${products.size} products" }
            if (products.size < pageSize) { done = true; break }
            page++
        }
        println("${slug.uppercase()}_COVERAGE pages=${minOf(page - effectiveStartPage + 1, maxPages)} exhausted=$done fullCatalogGuaranteed=false")
    }
}
