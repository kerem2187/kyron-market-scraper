plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

group = "com.kyron"
version = "1.0.0"

repositories { mavenCentral() }

dependencies {
    // HTML parsing
    implementation("org.jsoup:jsoup:1.18.1")

    // Headless browser for JS-rendered sites
    implementation("com.microsoft.playwright:playwright:1.47.0")

    // Firestore only (no Cloud Storage — free Spark plan friendly)
    implementation(platform("com.google.cloud:libraries-bom:26.44.0"))
    implementation("com.google.cloud:google-cloud-firestore")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Serialization (Open Food Facts JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(kotlin("test"))
}

// CLI app: runs one scrape pass then exits (perfect for a GitHub Actions cron job).
application {
    mainClass.set("com.kyron.scraper.MainKt")
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(21) }

// Downloads Chromium (+ OS deps) that Playwright needs. Used in CI and locally.
tasks.register<JavaExec>("installBrowsers") {
    group = "application"
    description = "Downloads the Chromium browser used by Playwright."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps", "chromium")
}
