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
        if (quote != '"' && quote != '\'' && quote != '`') {
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
