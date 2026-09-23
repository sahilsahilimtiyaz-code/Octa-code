package com.sahil.octacode.data.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 4, baseDelayMs = 800, maxDelayMs = 10_000)

    @Test
    fun `retries 429 and 5xx until max attempts`() {
        assertTrue(policy.shouldRetry(429, attempt = 1))
        assertTrue(policy.shouldRetry(500, attempt = 1))
        assertTrue(policy.shouldRetry(503, attempt = 3))
        assertFalse(policy.shouldRetry(429, attempt = 4))
        assertFalse(policy.shouldRetry(500, attempt = 4))
    }

    @Test
    fun `does not retry 4xx client errors or success`() {
        assertFalse(policy.shouldRetry(400, attempt = 1))
        assertFalse(policy.shouldRetry(401, attempt = 1))
        assertFalse(policy.shouldRetry(404, attempt = 1))
        assertFalse(policy.shouldRetry(200, attempt = 1))
    }

    @Test
    fun `delay grows and is capped`() {
        val d1 = policy.delayForAttempt(1)
        val d3 = policy.delayForAttempt(3)
        assertTrue(d1 in 800..1049)
        assertTrue(d3 > d1)
        assertTrue(policy.delayForAttempt(10) <= 10_000)
    }
}

class SseParserTest {

    @Test
    fun `parses content delta`() {
        val line = """data: {"choices":[{"delta":{"content":"Hello"}}]}"""
        assertTrue(SseParser.parseDelta(line) == "Hello")
    }

    @Test
    fun `ignores done keepalive and blanks`() {
        assertNull(SseParser.parseDelta("data: [DONE]"))
        assertNull(SseParser.parseDelta(": keep-alive"))
        assertNull(SseParser.parseDelta(""))
        assertTrue(SseParser.isDone("data: [DONE]"))
        assertFalse(SseParser.isDone("data: {}"))
    }

    @Test
    fun `skips malformed json without throwing`() {
        assertNull(SseParser.parseDelta("data: {not-json"))
        assertNull(SseParser.parseDelta("event: ping"))
    }

    @Test
    fun `delta without content key returns null not fake text`() {
        assertNull(SseParser.parseDelta("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
    }
}
