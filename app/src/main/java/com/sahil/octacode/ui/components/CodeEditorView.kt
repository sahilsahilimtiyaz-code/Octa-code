package com.sahil.octacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahil.octacode.ui.theme.*

@Composable
fun CodeEditorView(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(vertical = 8.dp)
    ) {
        val lines = code.count { it == '\n' } + 1
        
        Column(
            modifier = Modifier
                .width(48.dp)
                .verticalScroll(scrollState)
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            for (i in 1..lines) {
                Text(
                    text = i.toString(),
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(8.dp),
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(NeonBlue),
            visualTransformation = SyntaxHighlightingTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
    }
}

class SyntaxHighlightingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            highlightSyntax(text.text),
            OffsetMapping.Identity
        )
    }

    private fun highlightSyntax(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            
            val keywordRegex = "\\b(val|var|fun|class|interface|object|if|else|when|return|true|false|null|for|while|import|package)\\b".toRegex()
            val stringRegex = "\"[^\"]*\"".toRegex()
            val commentRegex = "//.*".toRegex()
            val numberRegex = "\\b\\d+\\b".toRegex()

            keywordRegex.findAll(code).forEach { match ->
                addStyle(SpanStyle(color = NeonBlue), match.range.first, match.range.last + 1)
            }

            stringRegex.findAll(code).forEach { match ->
                addStyle(SpanStyle(color = NeonGreen), match.range.first, match.range.last + 1)
            }

            numberRegex.findAll(code).forEach { match ->
                addStyle(SpanStyle(color = NeonRed), match.range.first, match.range.last + 1)
            }

            commentRegex.findAll(code).forEach { match ->
                addStyle(SpanStyle(color = ElectricPurple), match.range.first, match.range.last + 1)
            }
        }
    }
}
