package com.sahil.octacode.data.providers

import com.sahil.octacode.core.capability.ProviderStatus
import com.sahil.octacode.core.provider.ChatChunk
import com.sahil.octacode.core.provider.ChatMessage
import com.sahil.octacode.core.provider.ChatRequest
import com.sahil.octacode.core.provider.ChatRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val SSE_ANTHROPIC = """
    event: message_start
    data: {"type":"message_start","message":{"id":"m_1"}}

    event: content_block_delta
    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hel"}}

    event: content_block_delta
    data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hidden reasoning"}}

    event: content_block_delta
    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"lo"}}

    event: message_stop
    data: {"type":"message_stop"}
""".trimIndent()

private val SSE_GEMINI = """
    data: {"candidates":[{"content":{"parts":[{"text":"Hi"}]},"finishReason":"STOP"}]}
    data: {"candidates":[{"content":{"parts":[{"thoughtSignature":"sig-only"}]}}]}
    data: {"candidates":[{"content":{"parts":[{"text":" there"}]}}]}
""".trimIndent()

/**
 * What makes Claude and Gemini more than two more names on an existing
 * adapter: each disagrees with the OpenAI protocol on a fact that a wrong
 * guess fails *identically* to a bad key. The path and auth header are wrong
 * or they are not, the roles are `model` or they are `assistant`, and none of
 * it is visible from outside once a request has gone out.
 *
 * Every body is produced through a client configured exactly like
 * `KtorHttpFactory`'s. A test client with a different `Json` would serialize a
 * different body than the app sends, and the rules below are about that body.
 */
class ProtocolAdaptersTest {

    private class Sent {
        var url: String? = null
        var headers: Headers = Headers.Empty
        var body: String? = null
        var chunks: List<ChatChunk> = emptyList()
        fun json(): JsonObject = Json.parseToJsonElement(body!!).jsonObject
    }

    private fun testClient(
        reply: String,
        sent: Sent
    ): HttpClient = HttpClient(MockEngine { request ->
        sent.url = request.url.toString()
        sent.headers = request.headers
        // TextContent is what ContentNegotiation emits; falling back to the
        // object's own toString makes a missing plugin fail an assertion
        // rather than pass one.
        sent.body = when (val b = request.body) {
            is TextContent -> b.text
            else -> b.toString()
        }
        respond(reply)
    }) {
        // The same Json KtorHttpFactory installs. `encodeDefaults` is left at
        // its default of false on purpose — that is the app's configuration,
        // and the `stream` assertions below exist because of it.
        install(ContentNegotiation) {
            @OptIn(ExperimentalSerializationApi::class)
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }

    private val conversation = listOf(
        ChatMessage(ChatRole.SYSTEM, "Be terse."),
        ChatMessage(ChatRole.USER, "hi"),
        ChatMessage(ChatRole.ASSISTANT, "hello"),
        ChatMessage(ChatRole.USER, "and now?"),
    )

    private fun sendAnthropic(
        messages: List<ChatMessage> = conversation,
        temperature: Double = 0.2,
        maxTokens: Int = 1024
    ): Sent {
        val sent = Sent()
        val http = testClient(SSE_ANTHROPIC, sent)
        runBlocking {
            AnthropicAdapter(http) { "sk-ant_secret" }
                .chatStream(
                    ChatRequest(
                        messages = messages,
                        model = "claude-opus-5",
                        maxTokens = maxTokens,
                        temperature = temperature,
                    )
                )
                .toList()
                .also { sent.chunks = it }
        }
        return sent
    }

    private fun sendGemini(
        messages: List<ChatMessage> = conversation,
        temperature: Double = 0.2,
        maxTokens: Int = 1024
    ): Sent {
        val sent = Sent()
        val http = testClient(SSE_GEMINI, sent)
        runBlocking {
            GeminiAdapter(http) { "AIza_secret" }
                .chatStream(
                    ChatRequest(
                        messages = messages,
                        model = "gemini-3.7-flash",
                        maxTokens = maxTokens,
                        temperature = temperature,
                    )
                )
                .toList()
                .also { sent.chunks = it }
        }
        return sent
    }

    /**
     * `validate()` is suspend by contract, but every case below is decided
     * before any request — so running it on this thread is both correct and
     * the point: the rule under test is the offline one.
     */
    private fun anthropicStatus(key: String?): ProviderStatus =
        runBlocking { AnthropicAdapter(testClient("", Sent())) { key }.validate() }

    private fun geminiStatus(key: String?): ProviderStatus =
        runBlocking { GeminiAdapter(testClient("", Sent())) { key }.validate() }

    // --- Anthropic ---------------------------------------------------------

    @Test
    fun `system turns are lifted out of the message list instead of sent inline`() {
        // Anthropic's `messages` accept user and assistant only. A system turn
        // sent inline is a 400 on every request the first time an agent runs.
        val body = sendAnthropic().json()

        assertEquals("Be terse.", body["system"]?.jsonPrimitive?.content)
        val turns = body["messages"]!!.jsonArray
        assertEquals(3, turns.size)
        assertEquals("user", turns[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("assistant", turns[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", turns[2].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `without a system turn there is no system field to send`() {
        val body = sendAnthropic(messages = conversation.filter { it.role != ChatRole.SYSTEM }).json()

        assertNull("an empty instruction must not be sent as one", body["system"])
        assertEquals(3, body["messages"]!!.jsonArray.size)
    }

    @Test
    fun `the body asks for a stream, which has no default it could be left at`() {
        // The client's Json does not encode defaults, so a defaulted `stream`
        // would leave the field out — and Anthropic's own default for an
        // absent stream is a single message object, which this parser would
        // read as one non-event line and answer with nothing.
        val body = sendAnthropic().json()

        assertTrue("stream was: ${body["stream"]}", body["stream"]!!.jsonPrimitive.boolean)
        assertEquals("claude-opus-5", body["model"]!!.jsonPrimitive.content)
        assertEquals(1024, body["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `the key travels as x-api-key beside a pinned api version`() {
        val sent = sendAnthropic()

        assertEquals("sk-ant_secret", sent.headers["x-api-key"])
        assertEquals(
            "2023-06-01",
            sent.headers["anthropic-version"],
        )
        assertNull("anthropic does not take a Bearer token", sent.headers["Authorization"])
        assertEquals("$ANTHROPIC_BASE_URL/v1/messages", sent.url)
        assertFalse("the key must not be in the url: ${sent.url}", sent.url!!.contains("sk-ant"))
    }

    @Test
    fun `a temperature chosen on openai's scale is clamped to anthropic's`() {
        // OpenAI's scale reaches 2.0; Anthropic's stops at 1.0. Sending the
        // setting unconverted earns a 400 the user would read as a bad request.
        val body = sendAnthropic(temperature = 1.75).json()

        assertEquals(1.0, body["temperature"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `max tokens is at least one even if a caller asks for none`() {
        // Anthropic requires this field and rejects zero. A zero that reached
        // it would be a 400 with nothing in it the user could correct.
        val body = sendAnthropic(maxTokens = 0).json()

        assertEquals(1, body["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `anthropic's stream yields prose only, never its reasoning`() {
        val chunks = sendAnthropic().chunks

        assertEquals(listOf("Hel", "lo", ""), chunks.map { it.delta })
        assertTrue("the terminal chunk must be marked done", chunks.last().done)
        assertTrue(
            "a thinking line leaked into the answer",
            chunks.none { it.delta.contains("hidden reasoning") },
        )
    }

    @Test
    fun `sending without a key fails before any request leaves the device`() {
        val sent = Sent()
        val http = testClient(SSE_ANTHROPIC, sent)
        val adapter = AnthropicAdapter(http) { null }

        // Thrown eagerly rather than inside the flow: the user sees "key not
        // configured" instead of a request that went out with no auth header
        // and came back as a bare 401 with nothing to read.
        val thrown = assertThrows(IllegalStateException::class.java) {
            adapter.chatStream(ChatRequest(emptyList(), "claude-opus-5"))
        }

        assertTrue("message was: ${thrown.message}", thrown.message!!.contains("Claude"))
        assertTrue("should say where to fix it", thrown.message!!.contains("Settings"))
        assertNull("no request may have been made", sent.url)
    }

    @Test
    fun `an unset key reports the provider as missing rather than broken`() {
        assertTrue(anthropicStatus(null) is ProviderStatus.MissingKey)
    }

    @Test
    fun `a key from another provider names that provider instead of claiming a format error`() {
        // Gemini's keys start with `AIza`, which no Anthropic key ever would.
        // An earlier wording for this branch claimed Claude "is not
        // implemented in this build" — the one thing that must no longer be
        // said now that it has an adapter.
        val s = anthropicStatus("AIza_not_anthropic_key")

        assertTrue("was: $s", s is ProviderStatus.Misconfigured)
        assertTrue("must name Gemini: ${s.reason}", s.reason.contains("Gemini"))
        assertFalse("must not claim it is unimplemented: ${s.reason}", s.reason.contains("not implemented"))
    }

    @Test
    fun `an anthropic key is ready without a network probe`() {
        val s = anthropicStatus("sk-ant_realkey")

        assertTrue("was: $s", s is ProviderStatus.Ready)
        assertTrue("must not overstate what was checked: ${s.reason}", s.reason.contains("not network-verified"))
    }

    // --- Gemini ------------------------------------------------------------
    // `validate()` is suspend by contract, but every case here is decided
    // before any request, so running it on this thread is both correct and
    // the point: the rule under test is the offline one.

    @Test
    fun `a system turn becomes an instruction with no role of its own`() {
        // Gemini's roles are `user` and `model` — there is no `system` role to
        // use, so an instruction sent as a turn is a 400 on the first send.
        val body = sendGemini().json()
        val instruction = body["systemInstruction"]!!.jsonObject

        assertEquals(
            "Be terse.",
            instruction["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertNull("a systemInstruction has no role", instruction["role"])
        assertEquals(3, body["contents"]!!.jsonArray.size)
    }

    @Test
    fun `assistant turns are called model, not assistant`() {
        val contents = sendGemini().json()["contents"]!!.jsonArray

        assertEquals(
            listOf("user", "model", "user"),
            contents.map { it.jsonObject["role"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `the url is a method on the model and the key travels in its own header`() {
        val sent = sendGemini()

        assertEquals("AIza_secret", sent.headers["x-goog-api-key"])
        assertNull("gemini does not take a Bearer token", sent.headers["Authorization"])
        assertEquals(
            "$GEMINI_BASE_URL/v1beta/models/gemini-3.7-flash:streamGenerateContent?alt=sse",
            sent.url,
        )
        assertFalse("the key must not be in the url: ${sent.url}", sent.url!!.contains("AIza"))
    }

    @Test
    fun `the body asks for a stream with a usable token budget`() {
        val body = sendGemini().json()

        assertEquals(1024, body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.int)
        assertEquals(0.2, body["generationConfig"]!!.jsonObject["temperature"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `a temperature chosen on openai's scale is clamped to gemini's`() {
        val body = sendGemini(temperature = 3.0).json()

        assertEquals(2.0, body["generationConfig"]!!.jsonObject["temperature"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `gemini's stream yields text and never a signature alone`() {
        val chunks = sendGemini().chunks

        assertEquals(listOf("Hi", " there", ""), chunks.map { it.delta })
        assertTrue(chunks.last().done)
        assertTrue(
            "a thought signature was printed as prose",
            chunks.none { it.delta.contains("sig-only") || it.delta.contains("null") },
        )
    }

    @Test
    fun `sending to gemini without a key fails before any request leaves the device`() {
        val sent = Sent()
        val adapter = GeminiAdapter(testClient(SSE_GEMINI, sent)) { null }

        val thrown = assertThrows(IllegalStateException::class.java) {
            adapter.chatStream(ChatRequest(emptyList(), "gemini-3.7-flash"))
        }

        assertTrue("message was: ${thrown.message}", thrown.message!!.contains("Gemini"))
        assertTrue("should say where to fix it", thrown.message!!.contains("Settings"))
        assertNull("no request may have been made", sent.url)
    }

    @Test
    fun `gemini reports a present key as ready without probing the network`() {
        val s = geminiStatus("AIza_realkey")

        assertTrue("was: $s", s is ProviderStatus.Ready)
        assertTrue("must name the provider: ${s.reason}", s.reason.contains("Gemini"))
    }
}
