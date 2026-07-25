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
import com.intellij.openapi.editor.VisualPosition

internal fun window(editor: Editor): Things.Bounds {
    val (startLine, endLine) = Ide.visibleLines(editor)
    val doc = editor.document
    return Things.Bounds(doc.getLineStartOffset(startLine), doc.getLineEndOffset(endLine))
}

internal fun paragraph(
    editor: Editor,
    offset: Int,
    inner: Boolean,
): Things.Bounds? {
    val doc = editor.document
    if (doc.lineCount == 0) return null

    fun blank(line: Int): Boolean {
        val range = doc.charsSequence.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line))
        return range.isBlank()
    }
    val ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
    return if (blank(ln)) {
        null
    } else {
        var first = ln
        var last = ln
        while (first > 0 && !blank(first - 1)) first--
        while (last < doc.lineCount - 1 && !blank(last + 1)) last++
        val start = doc.getLineStartOffset(first)
        if (inner) {
            Things.Bounds(start, doc.getLineEndOffset(last))
        } else {
            var stop = last
            while (stop < doc.lineCount - 1 && blank(stop + 1)) stop++
            val end = if (stop < doc.lineCount - 1) doc.getLineStartOffset(stop + 1) else doc.getLineEndOffset(stop)
            Things.Bounds(start, end)
        }
    }
}

internal fun line(
    editor: Editor,
    offset: Int,
    inner: Boolean,
): Things.Bounds {
    val doc = editor.document
    val ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
    val end = doc.getLineEndOffset(ln)
    return if (inner) {
        Things.Bounds(doc.getLineStartOffset(ln), end)
    } else {
        Things.Bounds(doc.getLineStartOffset(ln), (end + 1).coerceAtMost(doc.textLength))
    }
}

internal fun visualLine(
    editor: Editor,
    offset: Int,
): Things.Bounds {
    val visualLineNumber = editor.offsetToVisualPosition(offset).line
    val start = editor.visualPositionToOffset(VisualPosition(visualLineNumber, 0))
    var end = editor.visualPositionToOffset(VisualPosition(visualLineNumber + 1, 0))
    if (end <= start) {
        end = editor.document.textLength
    } else if (end > 0 && editor.document.charsSequence[end - 1] == '\n') {
        end--
    }
    return Things.Bounds(start, end)
}
