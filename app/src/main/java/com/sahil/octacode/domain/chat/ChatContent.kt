package com.sahil.octacode.domain.chat

import com.sahil.octacode.core.provider.ChatMessage

// M4a pure chat helpers — no Android/Compose types, fully unit-testable.
// Splits assistant markdown into text vs fenced code blocks for the UI.
// Unclosed fences are treated honestly as code (not dropped).

sealed interface ChatSegment {
    data class Text(val text: String) : ChatSegment
    data class Code(val language: String, val code: String) : ChatSegment
}

fun splitChatSegments(markdown: String): List<ChatSegment> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = mutableListOf<ChatSegment>()
    val textBuf = mutableListOf<String>()
    val codeBuf = mutableListOf<String>()
    var inCode = false
    var lang = ""

    fun flushText() {
        if (textBuf.isNotEmpty()) {
            out += ChatSegment.Text(textBuf.joinToString("\n"))
            textBuf.clear()
        }
    }

    for (line in lines) {
        if (!inCode && line.trimStart().startsWith("```")) {
            flushText()
            inCode = true
            lang = line.trim().removePrefix("```").trim().take(24)
            codeBuf.clear()
        } else if (inCode && line.trim() == "```") {
            out += ChatSegment.Code(language = lang, code = codeBuf.joinToString("\n"))
            inCode = false
            lang = ""
        } else if (inCode) {
            codeBuf += line
        } else {
            textBuf += line
        }
    }
    if (inCode) {
        out += ChatSegment.Code(language = lang, code = codeBuf.joinToString("\n"))
    } else {
        flushText()
    }
    if (out.size > 1) {
        return out.filterNot { it is ChatSegment.Text && it.text.isBlank() }
    }
    return out
}

/** Bounded history window for requests — respects mobile RAM, keeps recency. */
fun historyWindow(messages: List<ChatMessage>, max: Int = 20): List<ChatMessage> =
    if (messages.size <= max) messages else messages.takeLast(max)
