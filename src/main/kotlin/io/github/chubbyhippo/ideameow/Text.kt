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
    c: Char,
    from: Int,
): Int {
    var i = from.coerceAtLeast(0)
    while (i < length) {
        if (this[i] == c) return i
        i++
    }
    return -1
}

internal fun CharSequence.lastIndexOfChar(
    c: Char,
    from: Int,
): Int {
    var i = from.coerceAtMost(length - 1)
    while (i >= 0) {
        if (this[i] == c) return i
        i--
    }
    return -1
}

internal fun charPred(symbol: Boolean): (Char) -> Boolean = if (symbol) Things::isSymbolChar else Things::isWordChar

internal fun parsedLineNumber(
    input: String?,
    lineCount: Int,
): Int? {
    val n = input?.trim()?.toIntOrNull() ?: return null
    return (n - 1).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
}

internal fun nthCharTarget(
    text: CharSequence,
    ch: Char,
    caret: Int,
    n: Int,
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
    repeat(n) {
        found = if (backward) text.lastIndexOfChar(ch, from) else text.indexOfChar(ch, from)
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
    n: Int,
): Int {
    var i = from.coerceIn(0, text.length)
    repeat(n) {
        while (i < text.length && text[i] !in SENTENCE_ENDERS) i++
        while (i < text.length && text[i] in SENTENCE_ENDERS) i++
        while (i < text.length && text[i].isWhitespace()) i++
    }
    return i
}

internal fun prevSentenceStart(
    text: CharSequence,
    from: Int,
    n: Int,
): Int {
    fun isGap(c: Char) = c.isWhitespace() || c in SENTENCE_ENDERS
    var i = from.coerceIn(0, text.length)
    repeat(n) {
        while (i > 0 && isGap(text[i - 1])) i--
        while (i > 0 && !isGap(text[i - 1])) i--
    }
    return i
}

object Words {
    fun nextEnd(
        text: CharSequence,
        from: Int,
        n: Int,
        pred: (Char) -> Boolean,
    ): Int {
        var i = from.coerceIn(0, text.length)
        repeat(n) {
            while (i < text.length && !pred(text[i])) i++
            while (i < text.length && pred(text[i])) i++
        }
        return i
    }

    fun prevStart(
        text: CharSequence,
        from: Int,
        n: Int,
        pred: (Char) -> Boolean,
    ): Int {
        var i = from.coerceIn(0, text.length)
        repeat(n) {
            while (i > 0 && !pred(text[i - 1])) i--
            while (i > 0 && pred(text[i - 1])) i--
        }
        return i
    }

    fun move(
        text: CharSequence,
        from: Int,
        n: Int,
        pred: (Char) -> Boolean,
    ): Int = if (n >= 0) nextEnd(text, from, n, pred) else prevStart(text, from, -n, pred)

    fun spanAt(
        text: CharSequence,
        offset: Int,
        pred: (Char) -> Boolean,
    ): Pair<Int, Int> {
        var s = offset
        var e = offset
        while (s > 0 && pred(text[s - 1])) s--
        while (e < text.length && pred(text[e])) e++
        return s to e
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
        var o = offset
        if (o >= text.length || !pred(text[o])) {
            when {
                o > 0 && pred(text[o - 1]) -> {
                    o--
                }

                else -> {
                    var f = o
                    while (f < text.length && !pred(text[f])) f++
                    if (f >= text.length) return null
                    o = f
                }
            }
        }
        return spanAt(text, o, pred)
    }
}
