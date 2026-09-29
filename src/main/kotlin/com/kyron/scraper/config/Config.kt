package com.kyron.scraper.config

/** Central runtime configuration read from environment variables. */
object Config {
    val projectId: String get() = env("GCP_PROJECT_ID")
    val databaseId: String get() = envOr("FIRESTORE_DATABASE", "(default)")
    val firestoreCollection: String get() = envOr("FIRESTORE_COLLECTION", "products")
    val observationsCollection: String get() = envOr("FIRESTORE_OBSERVATIONS_COLLECTION", "product_observations")

    val requestDelayMs: Long get() = positiveLong("REQUEST_DELAY_MS", 1_000)
    val userAgent: String get() = envOr("USER_AGENT", "KyronProductIndexer/2.0 (+https://kyron.app/bot)")

    val sheetsSyncEnabled: Boolean get() = envOr("SHEETS_SYNC_ENABLED", "false").equals("true", ignoreCase = true)
    val sheetsSpreadsheetId: String get() = envOr("SHEETS_SPREADSHEET_ID", "")
    val sheetsCatalogTab: String get() = envOr("SHEETS_CATALOG_TAB", "KATALOG")

    /** Hard safety limits. A canonical update can consume up to two writes. */
    val maxWritesPerRun: Int get() = positiveInt("MAX_WRITES_PER_RUN", 5_000)
    val maxRunMinutes: Long get() = positiveLong("MAX_RUN_MINUTES", 240)

    /** "all" means all VERIFIED sources, not experimental or disabled sources. */
    val enabledChains: List<String>
        get() = envOr("ENABLED_CHAINS", "off")
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun positiveInt(key: String, default: Int): Int =
        envOr(key, default.toString()).toIntOrNull()?.takeIf { it > 0 }
            ?: error("$key must be a positive integer")

    private fun positiveLong(key: String, default: Long): Long =
        envOr(key, default.toString()).toLongOrNull()?.takeIf { it > 0 }
            ?: error("$key must be a positive integer")

    private fun env(key: String): String = System.getenv(key) ?: error("Missing required env var: $key")
    private fun envOr(key: String, default: String): String =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: default
}
