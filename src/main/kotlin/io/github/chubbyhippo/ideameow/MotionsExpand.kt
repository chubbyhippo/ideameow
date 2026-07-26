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
import com.intellij.openapi.editor.ScrollType

internal fun moveExpand(
    editor: Editor,
    state: MeowState,
    dx: Int,
    dy: Int,
) {
    val posBefore = editor.caretModel.offset
    val goal = if (dy != 0) goalColumn(editor, state) else 0
    val length = editor.document.textLength
    for (caret in editor.caretModel.allCarets) {
        val target =
            if (dy == 0) {
                (caret.offset + dx).coerceIn(0, length)
            } else {
                movedLineOffset(editor, caret.offset, dy, columnFor(editor, caret, goal))
            }
        applyCaretMove(caret, target, extend = true)
    }
    recordExpandedSelection(editor, state, SelType.CHAR, posBefore)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
}

internal fun recordExpandedSelection(
    editor: Editor,
    state: MeowState,
    type: SelType,
    posBefore: Int,
) {
    val primary = editor.caretModel.primaryCaret
    Selections.recordSelect(
        state,
        Selections.SelectionSpec(type, primary.leadSelectionOffset, primary.offset, expand = true),
        posBefore,
    )
    state.selType = type
    state.selExpand = true
    Grab.beacon(editor, state)
}

internal fun charOrExpand(
    editor: Editor,
    state: MeowState,
    dx: Int,
) {
    if (editor.selectionModel.hasSelection()) moveExpand(editor, state, dx, dy = 0) else moveChar(editor, state, dx)
}

internal fun lineOrExpand(
    editor: Editor,
    state: MeowState,
    dy: Int,
) {
    if (editor.selectionModel.hasSelection()) moveExpand(editor, state, dx = 0, dy) else moveLine(editor, state, dy)
}

internal fun lineStartOffset(
    editor: Editor,
    offset: Int,
): Int {
    val doc = editor.document
    return doc.getLineStartOffset(doc.getLineNumber(offset.coerceIn(0, doc.textLength)))
}

internal fun lineEndOffset(
    editor: Editor,
    offset: Int,
): Int {
    val doc = editor.document
    return doc.getLineEndOffset(doc.getLineNumber(offset.coerceIn(0, doc.textLength)))
}

internal fun moveToOrExpand(
    editor: Editor,
    state: MeowState,
    type: SelType,
    target: (Editor, Int) -> Int,
) {
    val extend = editor.selectionModel.hasSelection()
    val posBefore = editor.caretModel.offset
    for (caret in editor.caretModel.allCarets) {
        applyCaretMove(caret, target(editor, caret.offset), extend)
    }
    if (extend) recordExpandedSelection(editor, state, type, posBefore)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
}

internal fun isBlank(ch: Char): Boolean = ch == ' ' || ch == '\t'

internal fun indentationOffset(
    editor: Editor,
    offset: Int,
): Int {
    val doc = editor.document
    val line = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
    val end = doc.getLineEndOffset(line)
    val text = doc.charsSequence
    var at = doc.getLineStartOffset(line)
    while (at < end && isBlank(text[at])) at++
    return at
}
