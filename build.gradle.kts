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
    implementation("com.microsoft.playwright:playwright:1.47.0")
    implementation(platform("com.google.cloud:libraries-bom:26.44.0"))
    implementation("com.google.cloud:google-cloud-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("ch.qos.logback:logback-classic:1.5.6")
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
