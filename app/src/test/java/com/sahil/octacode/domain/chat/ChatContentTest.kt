package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.provider.ChatMessage
import com.sahil.octacode.core.provider.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// M4a pure-logic tests for chat segmentation (no Compose needed).
class ChatContentTest {

    @Test
    fun `plain text stays single segment`() {
        val out = splitChatSegments("hello world")
        assertEquals(1, out.size)
        assertTrue(out.single() is ChatSegment.Text)
    }

    @Test
    fun `fenced block splits with language`() {
        val out = splitChatSegments("intro\n```kotlin\nval x = 1\n```\noutro")
        assertEquals(3, out.size)
        val code = out[1] as ChatSegment.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("val x = 1"))
    }

    @Test
    fun `unclosed fence treated as code honestly`() {
        val out = splitChatSegments("```python\nprint(1)")
        assertEquals(1, out.size)
        val code = out.single() as ChatSegment.Code
        assertEquals("python", code.language)
    }

    @Test
    fun `blank text around code is dropped`() {
        val out = splitChatSegments("\n```\ncode\n```\n")
        assertEquals(1, out.size)
        assertTrue(out.single() is ChatSegment.Code)
    }

    @Test
    fun `history window keeps recency`() {
        val msgs = (1..30).map { ChatMessage(ChatRole.USER, "m$it") }
        val win = historyWindow(msgs, max = 20)
        assertEquals(20, win.size)
        assertEquals("m11", win.first().content)
        assertEquals("m30", win.last().content)
    }
}
