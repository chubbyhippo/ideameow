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

internal fun CharSequence.indexOfChar(
    char: Char,
    from: Int,
): Int {
    var i = from.coerceAtLeast(0)
    while (i < length) {
        if (this[i] == char) return i
        i++
    }
    return -1
}

internal fun CharSequence.lastIndexOfChar(
    char: Char,
    from: Int,
): Int {
    var i = from.coerceAtMost(length - 1)
    while (i >= 0) {
        if (this[i] == char) return i
        i--
    }
    return -1
}

internal fun charPred(symbol: Boolean): (Char) -> Boolean = if (symbol) Things::isSymbolChar else Things::isWordChar

internal fun parsedLineNumber(
    input: String?,
    lineCount: Int,
): Int? {
    val number = input?.trim()?.toIntOrNull() ?: return null
    return (number - 1).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
}

internal fun nthCharTarget(
    text: CharSequence,
    char: Char,
    caret: Int,
    count: Int,
    backward: Boolean,
    till: Boolean,
): Int {
    var found = -1
    var from =
        when {
            backward && till -> caret - 2
            backward -> caret - 1
            till -> caret + 1
            else -> caret
        }
    repeat(count) {
        found = if (backward) text.lastIndexOfChar(char, from) else text.indexOfChar(char, from)
        if (found < 0) return -1
        from = if (backward) found - 1 else found + 1
    }
    if (found < 0) return -1
    return when {
        backward -> if (till) found + 1 else found
        else -> if (till) found else found + 1
    }
}

internal fun nextSentenceEnd(
    text: CharSequence,
    from: Int,
    count: Int,
): Int {
    var i = from.coerceIn(0, text.length)
    repeat(count) {
        while (i < text.length && text[i] !in SENTENCE_ENDERS) i++
        while (i < text.length && text[i] in SENTENCE_ENDERS) i++
        while (i < text.length && text[i].isWhitespace()) i++
    }
    return i
}

internal fun prevSentenceStart(
    text: CharSequence,
    from: Int,
    count: Int,
): Int {
    fun isGap(char: Char) = char.isWhitespace() || char in SENTENCE_ENDERS
    var i = from.coerceIn(0, text.length)
    repeat(count) {
        while (i > 0 && isGap(text[i - 1])) i--
        while (i > 0 && !isGap(text[i - 1])) i--
    }
    return i
}

object Paragraphs {
    fun nextEnd(
        text: CharSequence,
        from: Int,
        count: Int,
    ): Int {
        var pos = from.coerceIn(0, text.length)
        repeat(count) {
            var i = lineStartAt(text, pos)
            while (i < text.length && blankLineAt(text, i)) i = followingLineStart(text, i)
            while (i < text.length && !blankLineAt(text, i)) i = followingLineStart(text, i)
            pos = i
        }
        return pos
    }

    fun prevStart(
        text: CharSequence,
        from: Int,
        count: Int,
    ): Int {
        var pos = from.coerceIn(0, text.length)
        repeat(count) {
            if (pos > 0) {
                val start = startBefore(text, pos)
                pos = if (start < pos) start else startBefore(text, start - 1)
            }
        }
        return pos
    }

    private fun startBefore(
        text: CharSequence,
        offset: Int,
    ): Int {
        var i = lineStartAt(text, offset)
        while (i > 0 && blankLineAt(text, i)) i = lineStartAt(text, i - 1)
        while (i > 0 && !blankLineAt(text, lineStartAt(text, i - 1))) i = lineStartAt(text, i - 1)
        val prevLineEmpty = i > 0 && text[i - 1] == '\n' && (i == 1 || text[i - 2] == '\n')
        return if (prevLineEmpty) i - 1 else i
    }

    private fun lineStartAt(
        text: CharSequence,
        offset: Int,
    ): Int {
        var i = offset
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun followingLineStart(
        text: CharSequence,
        lineStart: Int,
    ): Int {
        var i = lineStart
        while (i < text.length && text[i] != '\n') i++
        return if (i < text.length) i + 1 else i
    }

    private fun blankLineAt(
        text: CharSequence,
        lineStart: Int,
    ): Boolean {
        var i = lineStart
        while (i < text.length && text[i] != '\n') {
            if (!text[i].isWhitespace()) return false
            i++
        }
        return true
    }
}

object Words {
    fun nextEnd(
        text: CharSequence,
        from: Int,
        count: Int,
        pred: (Char) -> Boolean,
    ): Int {
        var i = from.coerceIn(0, text.length)
        repeat(count) {
            while (i < text.length && !pred(text[i])) i++
            while (i < text.length && pred(text[i])) i++
        }
        return i
    }

    fun prevStart(
        text: CharSequence,
        from: Int,
        count: Int,
        pred: (Char) -> Boolean,
    ): Int {
        var i = from.coerceIn(0, text.length)
        repeat(count) {
            while (i > 0 && !pred(text[i - 1])) i--
            while (i > 0 && pred(text[i - 1])) i--
        }
        return i
    }

    fun move(
        text: CharSequence,
        from: Int,
        count: Int,
        pred: (Char) -> Boolean,
    ): Int = if (count >= 0) nextEnd(text, from, count, pred) else prevStart(text, from, -count, pred)

    fun spanAt(
        text: CharSequence,
        offset: Int,
        pred: (Char) -> Boolean,
    ): Pair<Int, Int> {
        var start = offset
        var end = offset
        while (start > 0 && pred(text[start - 1])) start--
        while (end < text.length && pred(text[end])) end++
        return start to end
    }

    fun fixSelectionMark(
        text: CharSequence,
        pos: Int,
        mark: Int,
        pred: (Char) -> Boolean,
    ): Int {
        val probe = (if (mark > pos) pos else pos - 1).coerceIn(0, (text.length - 1).coerceAtLeast(0))
        val bounds = boundsAt(text, probe, pred) ?: return mark
        return if (mark > pos) minOf(mark, bounds.second) else maxOf(mark, bounds.first)
    }

    fun boundsAt(
        text: CharSequence,
        offset: Int,
        pred: (Char) -> Boolean,
    ): Pair<Int, Int>? {
        var index = offset
        if (index >= text.length || !pred(text[index])) {
            when {
                index > 0 && pred(text[index - 1]) -> {
                    index--
                }

                else -> {
                    var next = index
                    while (next < text.length && !pred(text[next])) next++
                    if (next >= text.length) return null
                    index = next
                }
            }
        }
        return spanAt(text, index, pred)
    }
}
