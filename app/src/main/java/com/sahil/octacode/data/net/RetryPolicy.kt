package com.sahil.octacode.data.net

import kotlin.math.pow
import kotlin.random.Random

// Retry policy for 429/5xx: exponential backoff with jitter, capped.
// Pure Kotlin (unit-testable without Android).
data class RetryPolicy(
    val maxAttempts: Int = 4,
    val baseDelayMs: Long = 800,
    val maxDelayMs: Long = 10_000
) {
    fun delayForAttempt(attempt: Int): Long {
        val exp = baseDelayMs * 2.0.pow((attempt - 1).coerceAtLeast(0).toDouble())
        val jitter = Random.nextLong(0, 250)
        return (exp.toLong() + jitter).coerceAtMost(maxDelayMs)
    }

    fun shouldRetry(httpStatus: Int, attempt: Int): Boolean {
        if (attempt >= maxAttempts) return false
        return httpStatus == 429 || httpStatus in 500..599
    }
}
