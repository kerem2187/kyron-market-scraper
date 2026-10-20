package com.kyron.scraper.macrocenter

class MacrocenterBudgetReached(message: String) : RuntimeException(message)

/** Monotonic deadline, checked even when every product fails before emit(). */
class MacrocenterRunBudget(
    private val maxAttempts: Int,
    maxDurationMs: Long,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    init { require(maxAttempts > 0); require(maxDurationMs > 0) }
    private val started = clockMs()
    private val duration = maxDurationMs
    var attempts = 0
        private set

    fun checkTime() {
        if (clockMs() - started >= duration) throw MacrocenterBudgetReached("time limit reached")
    }

    fun beforeProduct() {
        checkTime()
        if (attempts >= maxAttempts) throw MacrocenterBudgetReached("product attempt limit reached ($maxAttempts)")
        attempts++
    }

    fun timeoutMs(preferred: Int): Double {
        checkTime()
        return minOf(preferred.toLong(), duration - (clockMs() - started)).coerceAtLeast(1).toDouble()
    }
}
