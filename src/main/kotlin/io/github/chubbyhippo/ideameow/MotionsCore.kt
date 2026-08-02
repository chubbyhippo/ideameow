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

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

internal fun wordType(symbol: Boolean) = if (symbol) SelType.SYMBOL else SelType.WORD

private val VERTICAL =
    setOf("meow-next", "meow-prev", "meow-next-expand", "meow-prev-expand", "next-line", "previous-line")

internal fun charSelActive(
    editor: Editor,
    state: MeowState,
) = state.selType == SelType.CHAR && editor.selectionModel.hasSelection()

internal fun goalColumn(
    editor: Editor,
    state: MeowState,
): Int {
    if (state.goalColumn == null || state.lastCommand !in VERTICAL) {
        val doc = editor.document
        val offset = editor.caretModel.offset
        state.goalColumn = offset - doc.getLineStartOffset(doc.getLineNumber(offset))
    }
    return state.goalColumn!!
}

internal fun movedLineOffset(
    editor: Editor,
    offset: Int,
    dy: Int,
    column: Int,
): Int {
    val doc = editor.document
    val line = doc.getLineNumber(offset)
    val target = line + dy
    return when {
        target < 0 -> {
            0
        }

        target > doc.lineCount - 1 -> {
            doc.textLength
        }

        else -> {
            val lineStart = doc.getLineStartOffset(target)
            lineStart + minOf(column, doc.getLineEndOffset(target) - lineStart)
        }
    }
}

internal fun moveChar(
    editor: Editor,
    state: MeowState,
    dx: Int,
) {
    val extend = charSelActive(editor, state)
    if (!extend && editor.selectionModel.hasSelection()) Selections.cancel(editor, state)
    val length = editor.document.textLength
    for (caret in editor.caretModel.allCarets) {
        val target = (caret.offset + dx).coerceIn(0, length)
        applyCaretMove(caret, target, extend)
    }
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
}

internal fun moveLine(
    editor: Editor,
    state: MeowState,
    dy: Int,
) {
    val extend = charSelActive(editor, state)
    if (!extend) Selections.cancel(editor, state)
    val goal = goalColumn(editor, state)
    for (caret in editor.caretModel.allCarets) {
        val target = movedLineOffset(editor, caret.offset, dy, columnFor(editor, caret, goal))
        applyCaretMove(caret, target, extend)
    }
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
}

internal fun columnFor(
    editor: Editor,
    caret: Caret,
    goal: Int,
): Int {
    if (caret == editor.caretModel.primaryCaret) return goal
    val doc = editor.document
    return caret.offset - doc.getLineStartOffset(doc.getLineNumber(caret.offset))
}

internal fun applyCaretMove(
    caret: Caret,
    target: Int,
    extend: Boolean,
) {
    if (extend) {
        val leadOffset = caret.leadSelectionOffset
        caret.moveToOffset(target)
        caret.setSelection(leadOffset, target)
    } else {
        caret.moveToOffset(target)
        caret.removeSelection()
    }
}
