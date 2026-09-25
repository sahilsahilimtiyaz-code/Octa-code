package com.sahil.octacode.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/**
 * Multiplier for text that is meant to be read as code: diffs, phase labels,
 * the event stream.
 *
 * Separate from the UI font scale because they answer different questions.
 * Shrinking the interface to fit more rows on screen does not mean the code
 * should shrink with it, and enlarging the terminal for legibility has not
 * asked for larger buttons.
 */
val LocalCodeFontScale = compositionLocalOf { 1.0f }

/**
 * Scales a typography style by [LocalCodeFontScale].
 *
 * The monospace text in this app sizes itself through `MaterialTheme.typography`
 * and only overrides the family, so there is no `fontSize` at those call sites
 * for a slider to point at. Scaling the style here instead of replacing the nine
 * call sites with literals keeps the shared type scale authoritative.
 *
 * [base]'s own size wins when the scale is 1.0, so untouched installs render
 * byte-for-byte what they render today.
 */
@Composable
fun codeTextStyle(base: TextStyle): TextStyle {
    val scale = LocalCodeFontScale.current
    if (scale == 1.0f) return base
    val fontSize = base.fontSize
    val lineHeight = base.lineHeight
    return base.copy(
        fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )
}
