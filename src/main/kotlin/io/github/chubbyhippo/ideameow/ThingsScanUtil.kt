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

internal fun startsWith(
    text: CharSequence,
    offset: Int,
    prefix: String,
): Boolean = offset + prefix.length <= text.length && prefix.indices.all { text[offset + it] == prefix[it] }

internal fun indexOf(
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

internal fun skipQuote(
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

internal fun isTagNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'

internal fun isTagNamePart(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == ':'
