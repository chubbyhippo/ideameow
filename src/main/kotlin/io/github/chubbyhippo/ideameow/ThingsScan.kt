// Copyright (C) 2026 Chubby Hippo
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along
// with this program. If not, see <https://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.chubbyhippo.ideameow

private const val TRIPLE_QUOTE_LEN = 3

internal fun string(
    text: CharSequence,
    offset: Int,
    inner: Boolean,
): Things.Bounds? {
    var i = 0
    while (i < text.length) {
        val quote = text[i]
        if (!isQuote(quote)) {
            i++
            continue
        }
        val triple = i + 2 < text.length && text[i + 1] == quote && text[i + 2] == quote
        val quoteLen = if (triple) TRIPLE_QUOTE_LEN else 1
        val open = i
        val closeEnd = stringEnd(text, i + quoteLen, quote, triple)
        if (closeEnd >= 0 && offset in open until closeEnd) {
            return if (inner) Things.Bounds(open + quoteLen, closeEnd - quoteLen) else Things.Bounds(open, closeEnd)
        }
        i = if (closeEnd < 0) open + quoteLen else closeEnd
    }
    return null
}

private fun stringEnd(
    text: CharSequence,
    contentStart: Int,
    quote: Char,
    triple: Boolean,
): Int {
    var j = contentStart
    while (j < text.length && (triple || text[j] != '\n')) {
        val char = text[j]
        if (char == '\\') {
            j += 2
            continue
        }
        val closed = !triple || (j + 2 < text.length && text[j + 1] == quote && text[j + 2] == quote)
        if (char == quote && closed) return j + if (triple) TRIPLE_QUOTE_LEN else 1
        j++
    }
    return -1
}

internal fun isWordChar(char: Char) = Character.isLetterOrDigit(char)

internal fun isSymbolChar(char: Char) = isWordChar(char) || char == '_' || char == '$'

internal fun symbol(
    text: CharSequence,
    offset: Int,
): Things.Bounds? {
    var index = offset
    if (index >= text.length || !isSymbolChar(text[index])) {
        if (index > 0 && isSymbolChar(text[index - 1])) index-- else return null
    }
    val (start, end) = Words.spanAt(text, index, ::isSymbolChar)
    return Things.Bounds(start, end)
}

private data class OpenTag(
    val name: String,
    val openStart: Int,
    val contentStart: Int,
)

private data class TagPair(
    val openStart: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val closeEnd: Int,
)

internal fun tag(
    text: CharSequence,
    offset: Int,
    inner: Boolean,
): Things.Bounds? {
    val pairs = mutableListOf<TagPair>()
    val stack = mutableListOf<OpenTag>()
    var i = 0
    while (i < text.length) {
        if (text[i] != '<') {
            i++
            continue
        }

        if (startsWith(text, i, "<!--")) {
            val endComment = indexOf(text, "-->", i + 4)
            i = if (endComment >= 0) endComment + 3 else text.length
            continue
        }

        if (startsWith(text, i, "<![CDATA[")) {
            val endCData = indexOf(text, "]]>", i + 9)
            i = if (endCData >= 0) endCData + 3 else text.length
            continue
        }

        if (startsWith(text, i, "<!")) {
            val endDecl = skipUntilChar(text, i + 2, '>')
            i = if (endDecl >= 0) endDecl + 1 else text.length
            continue
        }

        if (startsWith(text, i, "<?")) {
            val endPi = indexOf(text, "?>", i + 2)
            i = if (endPi >= 0) endPi + 2 else text.length
            continue
        }

        if (i + 1 < text.length && text[i + 1] == '/') {
            val nameStart = i + 2
            val nameEnd = scanTagName(text, nameStart)
            if (nameEnd > nameStart) {
                val tagName = text.subSequence(nameStart, nameEnd).toString()
                val closeTagEnd = skipTagAttributes(text, nameEnd)
                if (closeTagEnd >= 0) {
                    val contentEnd = i
                    val fullCloseEnd = closeTagEnd + 1
                    val openIndex = findMatchingOpen(stack, tagName)
                    if (openIndex >= 0) {
                        val openTag = stack[openIndex]
                        pairs.add(TagPair(openTag.openStart, openTag.contentStart, contentEnd, fullCloseEnd))
                        while (stack.size > openIndex) {
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                    i = fullCloseEnd
                    continue
                }
            }
            i++
            continue
        }

        val nameStart = i + 1
        val nameEnd = scanTagName(text, nameStart)
        if (nameEnd > nameStart) {
            val tagName = text.subSequence(nameStart, nameEnd).toString()
            val (tagEnd, selfClosing) = scanOpeningTagEnd(text, nameEnd)
            if (tagEnd >= 0) {
                val fullOpenEnd = tagEnd + 1
                if (!selfClosing) {
                    stack.add(OpenTag(tagName, i, fullOpenEnd))
                }
                i = fullOpenEnd
                continue
            }
        }

        i++
    }

    var bestPair: TagPair? = null
    var bestSpan = Int.MAX_VALUE
    for (pair in pairs) {
        if (offset in pair.openStart..pair.closeEnd) {
            val span = pair.closeEnd - pair.openStart
            if (span < bestSpan) {
                bestSpan = span
                bestPair = pair
            }
        }
    }

    val matched = bestPair ?: return null
    return if (inner) {
        Things.Bounds(matched.contentStart, matched.contentEnd)
    } else {
        Things.Bounds(matched.openStart, matched.closeEnd)
    }
}

private fun isTagNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'

private fun isTagNamePart(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == ':'

private fun scanTagName(
    text: CharSequence,
    start: Int,
): Int {
    if (start >= text.length || !isTagNameStart(text[start])) return start
    var i = start + 1
    while (i < text.length && isTagNamePart(text[i])) {
        i++
    }
    return i
}

private data class TagEndResult(
    val end: Int,
    val selfClosing: Boolean,
)

private fun scanOpeningTagEnd(
    text: CharSequence,
    start: Int,
): TagEndResult {
    var j = start
    while (j < text.length) {
        when (val c = text[j]) {
            '"', '\'' -> {
                j = skipQuote(text, j + 1, c)
            }
            '>' -> {
                var k = j - 1
                while (k >= start && text[k].isWhitespace()) k--
                val isSelfClosing = k >= start && text[k] == '/'
                return TagEndResult(j, isSelfClosing)
            }
            '<' -> {
                return TagEndResult(-1, false)
            }
            else -> j++
        }
    }
    return TagEndResult(-1, false)
}

private fun skipQuote(
    text: CharSequence,
    start: Int,
    quote: Char,
): Int {
    var j = start
    while (j < text.length) {
        if (text[j] == '\\') {
            j += 2
            continue
        }
        if (text[j] == quote) return j + 1
        j++
    }
    return j
}

private fun skipTagAttributes(
    text: CharSequence,
    start: Int,
): Int {
    var j = start
    while (j < text.length) {
        when (text[j]) {
            '"', '\'' -> {
                j = skipQuote(text, j + 1, text[j])
            }
            '>' -> return j
            '<' -> return -1
            else -> j++
        }
    }
    return -1
}

private fun findMatchingOpen(
    stack: List<OpenTag>,
    name: String,
): Int {
    for (idx in stack.indices.reversed()) {
        if (stack[idx].name.equals(name, ignoreCase = true)) {
            return idx
        }
    }
    return -1
}

private fun startsWith(
    text: CharSequence,
    offset: Int,
    prefix: String,
): Boolean {
    if (offset + prefix.length > text.length) return false
    for (i in prefix.indices) {
        if (text[offset + i] != prefix[i]) return false
    }
    return true
}

private fun indexOf(
    text: CharSequence,
    target: String,
    fromIndex: Int,
): Int {
    val max = text.length - target.length
    for (i in fromIndex..max) {
        if (startsWith(text, i, target)) return i
    }
    return -1
}

private fun skipUntilChar(
    text: CharSequence,
    start: Int,
    target: Char,
): Int {
    var j = start
    while (j < text.length) {
        if (text[j] == target) return j
        j++
    }
    return -1
}
