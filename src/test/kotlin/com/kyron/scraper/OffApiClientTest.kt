package com.kyron.scraper

import com.kyron.scraper.off.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OffApiClientTest {
    @Test fun searchRequestsAreAtLeastSevenSecondsApart() = runBlocking<Unit> {
        var now = 0L; val attempts = mutableListOf<Long>()
        val client = OffApiClient(request = { attempts.add(now); OffResponse(200, "ok") }, sleep = { now += it }, clock = { now })
        repeat(3) { assertEquals("ok", client.get("https://example.test")) }
        assertEquals(listOf(0L, 7000L, 14000L), attempts)
    }
    @Test fun rateLimitWaitsThenRetries() = runBlocking<Unit> {
        var now = 0L; var attempts = 0
        val client = OffApiClient(request = { if (attempts++ == 0) OffResponse(429, "", "120") else OffResponse(200, "ok") },
            sleep = { now += it }, clock = { now })
        assertEquals("ok", client.get("https://example.test")); assertEquals(120000L, now)
    }
    @Test fun exhaustedRetriesRemainFailures() = runBlocking<Unit> {
        var attempts = 0
        val client = OffApiClient(maxAttempts = 2, request = { attempts++; OffResponse(503, "") }, sleep = {}, intervalMs = 0)
        assertFailsWith<IllegalStateException> { client.get("https://example.test") }
        assertEquals(2, attempts)
    }
    @Test fun permanentErrorsFailWithoutRetry() = runBlocking<Unit> {
        var attempts = 0
        val client = OffApiClient(request = { attempts++; OffResponse(403, "") }, sleep = {}, intervalMs = 0)
        assertFailsWith<IllegalStateException> { client.get("https://example.test") }
        assertEquals(1, attempts)
    }
    @Test fun retryAfterHttpDateIsRespected() {
        assertEquals(120000L, OffApiClient.retryDelayMs(429, "Thu, 01 Jan 1970 00:02:00 GMT", 0, 0))
    }
    @Test fun missingRetryAfterUsesConservativeRateLimitDelay() {
        assertEquals(60000L, OffApiClient.retryDelayMs(429, null, 0, 0))
        assertEquals(10000L, OffApiClient.retryDelayMs(503, "invalid", 0, 0))
    }
}
