package com.sahil.octacode.data.net

import org.junit.Assert.assertEquals
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

    // --- Anthropic ---------------------------------------------------------

    @Test
    fun `anthropic prose comes out of a text delta`() {
        val line =
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""
        assertEquals("Hello", SseParser.parseAnthropicDelta(line))
    }

    @Test
    fun `a model's private reasoning is never printed as its answer`() {
        // thinking_delta, signature_delta and input_json_delta arrive in the
        // same field shape as text_delta — same event, same `delta.text`-like
        // position. Reading any of them as prose would put a model's reasoning
        // or its tool arguments into the conversation as if it had said them.
        val thinking =
            """data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"not for you"}}"""
        val signature =
            """data: {"type":"content_block_delta","delta":{"type":"signature_delta","signature":"sig"}}"""
        val toolJson =
            """data: {"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{"}}"""

        assertNull(SseParser.parseAnthropicDelta(thinking))
        assertNull(SseParser.parseAnthropicDelta(signature))
        assertNull(SseParser.parseAnthropicDelta(toolJson))
    }

    @Test
    fun `anthropic lifecycle events carry nothing to say`() {
        // message_start, content_block_stop and ping arrive around every block.
        // None is prose, and treating them as such would print a message id or
        // the word "ping" into the answer.
        assertNull(
            SseParser.parseAnthropicDelta(
                """data: {"type":"message_start","message":{"id":"msg_1"}}"""
            )
        )
        assertNull(SseParser.parseAnthropicDelta("""data: {"type":"ping"}"""))
        assertNull(SseParser.parseAnthropicDelta("""data: {"type":"content_block_stop","index":0}"""))
        assertNull(SseParser.parseAnthropicDelta(": keep-alive"))
        assertNull(SseParser.parseAnthropicDelta("data: {not-json"))
    }

    // --- Gemini ------------------------------------------------------------
    //
    // Gemini marks no terminator, so every line reaching this parser is one the
    // adapter must decide about: a null here is a line correctly recognised as
    // having no text in it, not a failure to read.

    @Test
    fun `gemini prose comes out of a candidate's parts`() {
        val line = """data: {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}"""
        assertEquals("Hello", SseParser.parseGeminiDelta(line))
    }

    @Test
    fun `every text part of a gemini chunk is kept`() {
        // A chunk can carry more than one, and keeping only the first would
        // silently lose words mid-sentence.
        val line =
            """data: {"candidates":[{"content":{"parts":[{"text":"one "},{"text":"two"}]}}]}"""
        assertEquals("one two", SseParser.parseGeminiDelta(line))
    }

    @Test
    fun `a part with only a thought signature is skipped, not read as null`() {
        // The closing chunk of a thinking model often carries nothing else.
        // Coercing a missing `text` would print the four letters "null" as the
        // model's reply.
        val line = """data: {"candidates":[{"content":{"parts":[{"thoughtSignature":"xyz"}]}}]}"""
        assertNull(SseParser.parseGeminiDelta(line))
    }

    @Test
    fun `a gemini line with no candidates is not invented into text`() {
        assertNull(
            SseParser.parseGeminiDelta("""data: {"usageMetadata":{"promptTokenCount":10}}""")
        )
        assertNull(SseParser.parseDelta("""data: {"usageMetadata":{"promptTokenCount":10}}"""))
    }
}
