package com.kyron.scraper.config

/** Central runtime configuration read from environment variables. */
object Config {
    /** GCP/Firebase project id (same project your Kyron app uses). */
    val projectId: String get() = env("GCP_PROJECT_ID")

    /**
     * Firestore database id. Leave as "(default)" unless your Kyron app uses a
     * NAMED database. If Kyron reads from a named DB but this writes to
     * "(default)", you will see green runs but no products in the app.
     */
    val databaseId: String get() = envOr("FIRESTORE_DATABASE", "(default)")

    val firestoreCollection: String get() = envOr("FIRESTORE_COLLECTION", "products")

    /** Politeness / throttling. */
    val requestDelayMs: Long get() = envOr("REQUEST_DELAY_MS", "800").toLong()
    val userAgent: String get() = envOr("USER_AGENT", "KyronBot/1.0 (+https://kyron.app/bot)")

    /** Comma-separated chain slugs, or "all". */
    val enabledChains: List<String>
        get() = envOr("ENABLED_CHAINS", "all")
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun env(key: String): String =
        System.getenv(key) ?: error("Missing required env var: $key")

    private fun envOr(key: String, default: String): String =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: default
}
