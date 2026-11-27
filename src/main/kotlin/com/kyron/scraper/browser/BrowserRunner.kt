package com.kyron.scraper.browser

import com.kyron.scraper.config.Config
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.options.ServiceWorkerPolicy
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright

/**
 * Lazily-created headless Chromium for JS-rendered SPA sites.
 * Playwright APIs are blocking; call [withPage] from an IO dispatcher.
 * Not safe to share a single Page across threads; each call gets a fresh context.
 */
class BrowserRunner : AutoCloseable {
    private var playwright: Playwright? = null
    private var browser: Browser? = null

    @Synchronized
    private fun browser(): Browser {
        if (browser == null) {
            val pw = Playwright.create()
            playwright = pw
            browser = pw.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true),
            )
        }
        return browser!!
    }

    fun <T> withPage(block: (Page) -> T): T {
        val context = browser().newContext(
            Browser.NewContextOptions().setUserAgent(Config.userAgent),
        )
        context.use { ctx ->
            configureContext(ctx)
            val page = ctx.newPage()
            page.setDefaultTimeout(30_000.0)
            return block(page)
        }
    }

    /** Keep cookies/cache across a bounded scrape, without sharing a Page across threads. */
    suspend fun <T> withContext(block: suspend (BrowserContext) -> T): T {
        val context = browser().newContext(
            Browser.NewContextOptions().setUserAgent(Config.userAgent)
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK),
        )
        context.use { configureContext(it); return block(it) }
    }
    private fun configureContext(ctx: BrowserContext) {
        val blocked = listOf("google-analytics.com", "googletagmanager.com", "doubleclick.net", "facebook.net", "hotjar.com", "clarity.ms", "criteo.com", "useinsider.com")
        ctx.route("**/*") { route ->
            val req = route.request(); val type = req.resourceType(); val url = req.url()
            if (type == "image" || type == "media" || type == "font" || blocked.any { url.contains(it, ignoreCase = true) }) route.abort() else route.resume()
        }
    }
    
    override fun close() {
        runCatching { browser?.close() }
        runCatching { playwright?.close() }
    }
}
