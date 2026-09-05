package com.senniapp.brickwares.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Groups a **digits-only** money field into thousands as the user types — "1000000" shows as
 * "1.000.000" — while the field's stored state stays raw digits, so parsing (and the digit cap) are
 * unchanged. Used for the ₫ inputs; USD keeps its own decimal formatting. The [OffsetMapping] keeps the
 * caret correct across the inserted separators.
 */
class ThousandsSeparatorTransformation(private val separator: Char = '.') : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val grouped = groupThousands(digits, separator)
        val mapping = object : OffsetMapping {
            // Walk the grouped string counting digits until we've passed [offset] of them.
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var seen = 0
                for (i in grouped.indices) {
                    if (grouped[i] != separator) seen++
                    if (seen == offset) return i + 1
                }
                return grouped.length
            }

            // Subtract the separators that appear before the transformed [offset].
            override fun transformedToOriginal(offset: Int): Int {
                val end = offset.coerceIn(0, grouped.length)
                var seps = 0
                for (i in 0 until end) if (grouped[i] == separator) seps++
                return end - seps
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/** Inserts [sep] every three digits from the right: "1000000" → "1.000.000". */
private fun groupThousands(digits: String, sep: Char): String {
    if (digits.length <= 3) return digits
    val sb = StringBuilder()
    val head = digits.length % 3
    if (head > 0) sb.append(digits, 0, head)
    var i = head
    while (i < digits.length) {
        if (sb.isNotEmpty()) sb.append(sep)
        sb.append(digits, i, i + 3)
        i += 3
    }
    return sb.toString()
}
