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

import com.intellij.openapi.editor.Editor

object Things {
    data class Bounds(
        val start: Int,
        val end: Int,
    )

    fun inner(
        editor: Editor,
        char: Char,
        offset: Int,
    ): Bounds? = compute(editor, char, offset, inner = true)

    fun bounds(
        editor: Editor,
        char: Char,
        offset: Int,
    ): Bounds? = compute(editor, char, offset, inner = false)
}

private fun compute(
    editor: Editor,
    char: Char,
    offset: Int,
    inner: Boolean,
): Things.Bounds? {
    val text = editor.document.charsSequence
    return when (char) {
        'r', '(', ')' -> pair(text, offset, '(', ')', inner)
        's', '[', ']' -> pair(text, offset, '[', ']', inner)
        'c' -> pair(text, offset, '{', '}', inner)
        't' -> tag(text, offset, inner)
        'g' -> string(text, offset, inner)
        'e' -> symbol(text, offset)
        'w' -> window(editor)
        'b' -> Things.Bounds(0, text.length)
        'p' -> paragraph(editor, offset, inner)
        'l' -> line(editor, offset, inner)
        'v' -> visualLine(editor, offset)
        'd' -> defun(editor, offset)
        '.' -> sentence(text, offset, inner)
        else -> null
    }
}

internal fun pair(
    text: CharSequence,
    offset: Int,
    open: Char,
    close: Char,
    inner: Boolean,
): Things.Bounds? {
    val start = scanBackToOpen(text, offset, open, close)
    if (start < 0) return null
    val end = scanForwardToClose(text, offset, start, open, close)
    return when {
        end < 0 -> null
        inner -> Things.Bounds(start + 1, end)
        else -> Things.Bounds(start, end + 1)
    }
}

private fun scanBackToOpen(
    text: CharSequence,
    offset: Int,
    open: Char,
    close: Char,
): Int {
    var depth = 0
    var i = offset - 1
    while (i >= 0) {
        val char = text[i]
        if (char == close) {
            depth++
        } else if (char == open) {
            if (depth == 0) return i
            depth--
        }
        i--
    }
    return -1
}

private fun scanForwardToClose(
    text: CharSequence,
    offset: Int,
    start: Int,
    open: Char,
    close: Char,
): Int {
    var depth = 0
    var j = offset
    while (j < text.length) {
        val char = text[j]
        if (char == open && j != start) {
            depth++
        } else if (char == close) {
            if (depth == 0) return j
            depth--
        }
        j++
    }
    return -1
}

internal const val SENTENCE_ENDERS = ".!?"

internal const val OPEN_BRACKETS = "([{"

internal const val CLOSE_BRACKETS = ")]}"

internal const val QUOTES = "\"'`"

internal fun isQuote(char: Char) = char in QUOTES
