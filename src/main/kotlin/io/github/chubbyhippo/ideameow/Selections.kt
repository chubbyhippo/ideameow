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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

internal object Selections {
    private const val SELECTION_HISTORY_MAX = 200

    val commands: Map<String, MeowCommand> =
        buildMap {
            for (n in 0..9) put("meow-expand-$n", MeowCommand { editor, state -> expandOrCount(editor, state, n) })
            put("meow-reverse", MeowCommand { editor, _ -> reverse(editor) })
            put("meow-cancel-selection", MeowCommand { editor, state -> cancelAll(editor, state) })
            put("meow-pop-selection", MeowCommand { editor, state -> pop(editor, state) })
        }

    private val EXPANDABLE =
        setOf(
            SelType.CHAR,
            SelType.WORD,
            SelType.SYMBOL,
            SelType.LINE,
            SelType.FIND,
            SelType.TILL,
        )

    fun backwardP(editor: Editor): Boolean {
        val sm = editor.selectionModel
        return sm.hasSelection() && editor.caretModel.offset <= sm.selectionStart
    }

    fun mark(editor: Editor): Int {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) return editor.caretModel.offset
        return if (backwardP(editor)) sm.selectionEnd else sm.selectionStart
    }

    fun lineExpandPoint(
        doc: Document,
        ln: Int,
        n: Int,
        back: Boolean,
    ): Int =
        if (back) {
            doc.getLineStartOffset((ln - n).coerceAtLeast(0))
        } else {
            doc.getLineEndOffset((ln + n).coerceAtMost(doc.lineCount - 1))
        }

    fun recordSelect(
        state: MeowState,
        type: SelType,
        expand: Boolean,
        mark: Int,
        point: Int,
        posBefore: Int,
    ) {
        val prev = state.lastSelection ?: SavedSelection(null, false, posBefore, posBefore)
        if (state.selectionHistory.lastOrNull() != prev) state.selectionHistory.addLast(prev)
        while (state.selectionHistory.size > SELECTION_HISTORY_MAX) state.selectionHistory.removeFirst()
        state.lastSelection = SavedSelection(type, expand, mark, point)
    }

    fun select(
        editor: Editor,
        state: MeowState,
        type: SelType,
        mark: Int,
        point: Int,
        expand: Boolean,
        push: Boolean = true,
    ) {
        val len = editor.document.textLength
        val m = mark.coerceIn(0, len)
        val p = point.coerceIn(0, len)
        val sm = editor.selectionModel
        if (push) {
            recordSelect(state, type, expand, m, p, editor.caretModel.offset)
        } else {
            state.lastSelection = SavedSelection(type, expand, m, p)
        }
        state.selType = type
        state.selExpand = expand
        editor.caretModel.moveToOffset(p)
        sm.setSelection(minOf(m, p), maxOf(m, p))
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        Grab.beacon(editor, state)
        ExpandHints.show(editor, state)
    }

    fun resetSelectionMemory(state: MeowState) {
        state.selectionHistory.clear()
        state.lastSelection = null
    }

    fun collapse(
        editor: Editor,
        state: MeowState,
    ) {
        editor.selectionModel.removeSelection()
        state.selType = SelType.NONE
        state.selExpand = false
    }

    fun cancel(
        editor: Editor,
        state: MeowState,
    ) {
        collapse(editor, state)
        resetSelectionMemory(state)
    }

    fun cancelAll(
        editor: Editor,
        state: MeowState,
    ) {
        if (editor.caretModel.caretCount > 1) editor.caretModel.removeSecondaryCarets()
        cancel(editor, state)
    }

    private fun reverse(editor: Editor) {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) return
        val s = sm.selectionStart
        val e = sm.selectionEnd
        val newPoint = if (editor.caretModel.offset <= s) e else s
        editor.caretModel.moveToOffset(newPoint)
        sm.setSelection(s, e)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    private fun pop(
        editor: Editor,
        state: MeowState,
    ) {
        if (editor.selectionModel.hasSelection()) {
            val entry = state.selectionHistory.removeLastOrNull() ?: return
            if (entry.type == null) {
                editor.caretModel.moveToOffset(entry.point)
                cancel(editor, state)
                Ide.hint(editor, "No previous selection")
            } else {
                select(editor, state, entry.type, entry.mark, entry.point, entry.expand, push = false)
            }
        } else if (!Grab.pop(editor, state)) {
            Ide.hint(editor, "No previous selection")
        }
    }

    private fun expandOrCount(
        editor: Editor,
        state: MeowState,
        n: Int,
    ) {
        if (editor.selectionModel.hasSelection() && state.selType in EXPANDABLE) {
            expand(editor, state, if (n == 0) 10 else n)
        } else {
            state.pendingCount = state.pendingCount * 10 + n
        }
    }

    private fun expand(
        editor: Editor,
        state: MeowState,
        n: Int,
    ) {
        val text = editor.document.charsSequence
        val doc = editor.document
        val back = backwardP(editor)
        val caret = editor.caretModel.offset
        val target: Int =
            when (state.selType) {
                SelType.CHAR -> {
                    caret + if (back) -n else n
                }

                SelType.WORD, SelType.SYMBOL -> {
                    val p = charPred(state.selType == SelType.SYMBOL)
                    if (back) Words.prevStart(text, caret, n, p) else Words.nextEnd(text, caret, n, p)
                }

                SelType.LINE -> lineExpandPoint(doc, doc.getLineNumber(caret), n, back)

                SelType.FIND, SelType.TILL -> {
                    val ch = state.lastFind ?: return
                    val t = nthCharTarget(text, ch, caret, n, backward = back, till = state.selType == SelType.TILL)
                    if (t < 0) return
                    t
                }

                else -> {
                    return
                }
            }
        select(editor, state, state.selType, mark(editor), target, expand = false)
    }
}
