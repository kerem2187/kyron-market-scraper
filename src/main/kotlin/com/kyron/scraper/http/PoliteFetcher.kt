package com.kyron.scraper.http

import com.kyron.scraper.config.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Rate-limited, retrying HTML fetcher for server-rendered sites.
 * Keep a single instance per run so the throttle is shared.
 */
class PoliteFetcher(
    private val delayMs: Long = Config.requestDelayMs,
    private val userAgent: String = Config.userAgent,
    private val maxRetries: Int = 3,
) {
    private val mutex = Mutex()
    private var lastRequest = 0L

    suspend fun getHtml(url: String): Document {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxRetries) {
            throttle()
            try {
                return Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(20_000)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .get()
            } catch (e: Exception) {
                lastError = e
                attempt++
                delay(delayMs * (attempt + 1))
            }
        }
        throw RuntimeException("Failed to fetch $url after $maxRetries attempts", lastError)
    }

    private suspend fun throttle() = mutex.withLock {
        val now = System.currentTimeMillis()
        val wait = delayMs - (now - lastRequest)
        if (wait > 0) delay(wait)
        lastRequest = System.currentTimeMillis()
    }
}
