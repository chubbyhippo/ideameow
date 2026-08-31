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

private data class TagEndResult(
    val end: Int,
    val selfClosing: Boolean,
)

private data class MarkupSpec(
    val prefix: String,
    val terminator: String,
)

private val MARKUP_SPECS =
    listOf(
        MarkupSpec("<!--", "-->"),
        MarkupSpec("<![CDATA[", "]]>"),
        MarkupSpec("<!", ">"),
        MarkupSpec("<?", "?>"),
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
        val special = skipSpecialMarkup(text, i)
        i =
            when {
                special != null -> special
                i + 1 < text.length && text[i + 1] == '/' -> handleCloseTag(text, i, stack, pairs)
                else -> handleOpenTag(text, i, stack)
            }
    }

    val matched = findBestPair(pairs, offset) ?: return null
    return if (inner) {
        Things.Bounds(matched.contentStart, matched.contentEnd)
    } else {
        Things.Bounds(matched.openStart, matched.closeEnd)
    }
}

private fun skipSpecialMarkup(
    text: CharSequence,
    i: Int,
): Int? {
    val spec = MARKUP_SPECS.firstOrNull { startsWith(text, i, it.prefix) } ?: return null
    val end = indexOf(text, spec.terminator, i + spec.prefix.length)
    return if (end >= 0) end + spec.terminator.length else text.length
}

private fun handleCloseTag(
    text: CharSequence,
    i: Int,
    stack: MutableList<OpenTag>,
    pairs: MutableList<TagPair>,
): Int {
    val nameStart = i + 2
    val nameEnd = scanTagName(text, nameStart)
    val closeTagEnd = if (nameEnd > nameStart) skipTagAttributes(text, nameEnd) else -1
    if (closeTagEnd < 0) return i + 1
    val tagName = text.subSequence(nameStart, nameEnd).toString()
    val fullCloseEnd = closeTagEnd + 1
    val openIndex = findMatchingOpen(stack, tagName)
    if (openIndex >= 0) {
        val openTag = stack[openIndex]
        pairs.add(TagPair(openTag.openStart, openTag.contentStart, i, fullCloseEnd))
        while (stack.size > openIndex) {
            stack.removeAt(stack.lastIndex)
        }
    }
    return fullCloseEnd
}

private fun handleOpenTag(
    text: CharSequence,
    i: Int,
    stack: MutableList<OpenTag>,
): Int {
    val nameStart = i + 1
    val nameEnd = scanTagName(text, nameStart)
    val tagEndResult = if (nameEnd > nameStart) scanOpeningTagEnd(text, nameEnd) else TagEndResult(-1, false)
    if (tagEndResult.end < 0) return i + 1
    val tagName = text.subSequence(nameStart, nameEnd).toString()
    val fullOpenEnd = tagEndResult.end + 1
    if (!tagEndResult.selfClosing) {
        stack.add(OpenTag(tagName, i, fullOpenEnd))
    }
    return fullOpenEnd
}

private fun findBestPair(
    pairs: List<TagPair>,
    offset: Int,
): TagPair? {
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
    return bestPair
}

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
                j = text.length
            }

            else -> {
                j++
            }
        }
    }
    return TagEndResult(-1, false)
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

            '>' -> {
                return j
            }

            '<' -> {
                j = text.length
            }

            else -> {
                j++
            }
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
