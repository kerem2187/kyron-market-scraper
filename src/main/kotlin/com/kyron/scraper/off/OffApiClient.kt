package com.kyron.scraper.off

import com.kyron.scraper.config.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.net.HttpURLConnection
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

data class OffResponse(val status: Int, val body: String, val retryAfter: String? = null)

/** OFF search permits 10 requests/minute/IP. Pace every attempt, including retries. */
class OffApiClient(
    private val maxAttempts: Int = 4,
    private val request: (String) -> OffResponse = ::httpGet,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = 7_000,
) {
    private var previousAttempt: Long? = null
    init { require(maxAttempts > 0); require(intervalMs >= 0) }

    suspend fun get(url: String): String {
        var lastFailure = "not attempted"
        repeat(maxAttempts) { attempt ->
            previousAttempt?.let { last -> (intervalMs - (clock() - last)).takeIf { it > 0 }?.let { sleep(it) } }
            previousAttempt = clock()
            val response = try { request(url) } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastFailure = e.javaClass.simpleName
                log.warn { "[off] request attempt=${attempt + 1}/$maxAttempts transport=$lastFailure" }
                null
            }
            if (response != null) {
                if (response.status in 200..299) return response.body
                lastFailure = "HTTP ${response.status}"
                log.warn { "[off] request attempt=${attempt + 1}/$maxAttempts $lastFailure" }
                check(response.status == 429 || response.status in 500..599) { "[off] $lastFailure (not retryable)" }
            }
            if (attempt + 1 < maxAttempts) {
                val wait = retryDelayMs(response?.status, response?.retryAfter, attempt, clock())
                // Never cap Retry-After downward: the workflow budget may stop the run instead.
                log.warn { "[off] retry deferred ${wait}ms" }
                sleep(wait)
            }
        }
        error("[off] API fetch failed after $maxAttempts attempts: $lastFailure")
    }

    companion object {
        fun retryDelayMs(status: Int?, header: String?, attempt: Int, now: Long): Long {
            val declared = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
                ?.let { seconds -> if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000 }
                ?: header?.let { runCatching {
                    (ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now).coerceAtLeast(0)
                }.getOrNull() }
            val backoff = (10_000L * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(300_000)
            return maxOf(declared ?: 0, if (status == 429) 60_000L else backoff)
        }

        private fun httpGet(url: String): OffResponse {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("User-Agent", Config.userAgent)
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                val status = conn.responseCode
                val body = if (status in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else ""
                return OffResponse(status, body, conn.getHeaderField("Retry-After"))
            } finally { conn.disconnect() }
        }
    }
}
