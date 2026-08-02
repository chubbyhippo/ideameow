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
import com.intellij.openapi.editor.SelectionModel

private const val TENTH_DIVISOR = 10

internal fun wordOrExpand(
    editor: Editor,
    state: MeowState,
    count: Int,
) {
    val text = editor.document.charsSequence
    val isWord = charPred(symbol = false)
    moveToOrExpand(editor, state, SelType.WORD) { _, offset -> Words.move(text, offset, count, isWord) }
}

internal fun sentenceOrExpand(
    editor: Editor,
    state: MeowState,
    count: Int,
) {
    val text = editor.document.charsSequence
    moveToOrExpand(editor, state, SelType.CHAR) { _, offset ->
        if (count >= 0) nextSentenceEnd(text, offset, count) else prevSentenceStart(text, offset, -count)
    }
}

internal fun paragraphOrExpand(
    editor: Editor,
    state: MeowState,
    count: Int,
) {
    val text = editor.document.charsSequence
    moveToOrExpand(editor, state, SelType.CHAR) { _, offset ->
        if (count >= 0) Paragraphs.nextEnd(text, offset, count) else Paragraphs.prevStart(text, offset, -count)
    }
}

internal fun bufferBoundary(
    editor: Editor,
    state: MeowState,
    top: Boolean,
) {
    val counted = state.pendingCount != 0 || state.negative
    val count = state.takeCount(1)
    moveToOrExpand(editor, state, SelType.CHAR) { editor, _ ->
        val length = editor.document.textLength
        if (!counted) {
            if (top) 0 else length
        } else {
            val tenth = length * count / TENTH_DIVISOR
            val rawOffset = (if (top) tenth else length - tenth).coerceIn(0, length)
            nextLineStart(editor, rawOffset)
        }
    }
}

internal fun nextLineStart(
    editor: Editor,
    offset: Int,
): Int {
    val doc = editor.document
    if (doc.textLength == 0) return 0
    val line = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
    return if (line >= doc.lineCount - 1) doc.textLength else doc.getLineStartOffset(line + 1)
}

internal fun wordMotion(
    editor: Editor,
    state: MeowState,
    symbol: Boolean,
    count: Int,
) {
    if (count == 0) return
    val text = editor.document.charsSequence
    val type = wordType(symbol)
    val isWord = charPred(symbol)
    val selectionModel = editor.selectionModel
    if (!(selectionModel.hasSelection() && state.selType == type)) Selections.cancel(editor, state)
    val extend = state.selExpand && state.selType == type && selectionModel.hasSelection()
    val start = wordMotionStart(editor, selectionModel, extend, count)
    val target = Words.move(text, start, count, isWord)
    if (target == start) return
    val anchor =
        if (extend) {
            wordMotionAnchor(selectionModel, count)
        } else {
            Words.fixSelectionMark(text, target, start, isWord)
        }
    Selections.select(editor, state, Selections.SelectionSpec(type, anchor, target, expand = extend))
}

private fun wordMotionStart(
    editor: Editor,
    selectionModel: SelectionModel,
    extend: Boolean,
    count: Int,
): Int =
    when {
        extend && count < 0 -> selectionModel.selectionStart
        extend -> selectionModel.selectionEnd
        else -> editor.caretModel.offset
    }

private fun wordMotionAnchor(
    selectionModel: SelectionModel,
    count: Int,
): Int = if (count < 0) selectionModel.selectionEnd else selectionModel.selectionStart

internal fun markWord(
    editor: Editor,
    state: MeowState,
    symbol: Boolean,
) {
    val negative = state.takeCount(1) < 0
    val text = editor.document.charsSequence
    val bounds =
        Words.boundsAt(text, editor.caretModel.offset, charPred(symbol))
            ?: run {
                Ide.hint(editor, "No word here")
                return
            }
    val (start, end) = bounds
    val (mark, point) = if (negative) end to start else start to end
    Selections.select(editor, state, Selections.SelectionSpec(wordType(symbol), mark, point, expand = true))
    val quoted = Regex.escape(text.subSequence(start, end).toString())
    val pattern = if (symbol) "(?<![\\w$])$quoted(?![\\w$])" else "\\b$quoted\\b"
    Search.push(state, Regex(pattern))
}
