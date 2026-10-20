plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

group = "com.kyron"
version = "2.0.0"
repositories { mavenCentral() }

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    implementation("com.microsoft.playwright:playwright:1.47.0")
    implementation(platform("com.google.cloud:libraries-bom:26.44.0"))
    implementation("com.google.cloud:google-cloud-firestore")
    implementation("com.google.auth:google-auth-library-oauth2-http")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("ch.qos.logback:logback-classic:1.6.4")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application { mainClass.set("com.kyron.scraper.MainKt") }
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }

tasks.register<JavaExec>("installBrowsers") {
    group = "application"
    description = "Downloads Chromium used by Playwright."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps", "chromium")
}

// Standalone Firestore -> Google Sheets/AppSheet catalog synchronization.
tasks.register<JavaExec>("syncSheetsCatalog") {
    group = "application"
    description = "Synchronizes canonical Firestore products into the AppSheet KATALOG tab."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.kyron.scraper.sheets.SyncSheetsMainKt")
}

// Read-only Macrocenter smoke check; does not initialize Firestore/Sheets.
tasks.register<JavaExec>("macrocenterSmoke") {
    group = "verification"
    description = "Prints up to three Macrocenter products without database writes."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.kyron.scraper.macrocenter.MacrocenterSmokeMainKt")
}
