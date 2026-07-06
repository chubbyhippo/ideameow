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

/**
 * The selection primitive, and the commands that act on the selection itself
 * (reverse, cancel, pop, digit expand). Every selecting command funnels
 * through [select], which mirrors meow's (expand|select . type) model: it
 * records the type and expand flag, pushes the replaced region to the history
 * (meow-pop-selection), lets the beacon react (a selection inside a grab
 * spawns carets), and paints the expand hints.
 */
internal object Selections {

    val commands: Map<String, MeowCommand> = buildMap {
        for (n in 0..9) put("meow-expand-$n", MeowCommand { ed, st, _ -> expandOrCount(ed, st, n) })
        put("meow-reverse", MeowCommand { ed, _, _ -> reverse(ed) })
        put("meow-cancel-selection", MeowCommand { ed, st, _ -> cancelAll(ed, st) })
        put("meow-pop-selection", MeowCommand { ed, st, _ -> pop(ed, st) })
    }

    /** The types digit expand can grow; anything else makes digits a count. */
    private val EXPANDABLE = setOf(
        SelType.CHAR, SelType.WORD, SelType.SYMBOL, SelType.LINE, SelType.FIND, SelType.TILL
    )

    /** Is the selection reversed (caret at its start), meow--direction-backward-p. */
    fun backwardP(editor: Editor): Boolean {
        val sm = editor.selectionModel
        return sm.hasSelection() && editor.caretModel.offset <= sm.selectionStart
    }

    /** The anchor a same-direction re-selection keeps, meow's mark. */
    fun mark(editor: Editor): Int {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) return editor.caretModel.offset
        return if (backwardP(editor)) sm.selectionEnd else sm.selectionStart
    }

    /** meow--select's history bookkeeping: push the previous meow--selection —
     *  or a null placeholder at [posBefore] when there was none — then
     *  remember the new one. */
    fun recordSelect(st: MeowState, type: SelType, expand: Boolean, anchor: Int, active: Int, posBefore: Int) {
        val prev = st.lastSelection ?: SavedSelection(null, false, posBefore, posBefore)
        if (st.selectionHistory.lastOrNull() != prev) st.selectionHistory.addLast(prev)
        while (st.selectionHistory.size > 200) st.selectionHistory.removeFirst()
        st.lastSelection = SavedSelection(type, expand, anchor, active)
    }

    fun select(
        editor: Editor, st: MeowState, type: SelType,
        mark: Int, point: Int, expand: Boolean, push: Boolean = true,
    ) {
        val len = editor.document.textLength
        val m = mark.coerceIn(0, len)
        val p = point.coerceIn(0, len)
        val sm = editor.selectionModel
        if (push) recordSelect(st, type, expand, m, p, editor.caretModel.offset)
        else st.lastSelection = SavedSelection(type, expand, m, p)
        st.selType = type
        st.selExpand = expand
        editor.caretModel.moveToOffset(p)
        sm.setSelection(minOf(m, p), maxOf(m, p))
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        Grab.beacon(editor, st)
        ExpandHints.show(editor, st)
    }

    /** Forget the selection chain — the history-clearing half of
     *  meow--cancel-selection. */
    fun resetSelectionMemory(st: MeowState) {
        st.selectionHistory.clear()
        st.lastSelection = null
    }

    /** Collapse the selection WITHOUT touching the history — for edits that
     *  kill the region as a side effect (meow never cancels there). */
    fun collapse(editor: Editor, st: MeowState) {
        editor.selectionModel.removeSelection()
        st.selType = SelType.NONE
        st.selExpand = false
    }

    /** meow--cancel-selection: collapse AND clear the selection history. */
    fun cancel(editor: Editor, st: MeowState) {
        collapse(editor, st)
        resetSelectionMemory(st)
    }

    fun cancelAll(editor: Editor, st: MeowState) {
        if (editor.caretModel.caretCount > 1) editor.caretModel.removeSecondaryCarets()
        cancel(editor, st)
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

    /** meow-pop-selection: with an active region, pop the history (a typed
     *  entry restores type AND direction; the null placeholder returns the
     *  caret to where the chain started and cancels); else pop the grab. */
    private fun pop(editor: Editor, st: MeowState) {
        if (editor.selectionModel.hasSelection()) {
            val entry = st.selectionHistory.removeLastOrNull() ?: return // meow is silent here
            if (entry.type == null) {
                editor.caretModel.moveToOffset(entry.active)
                cancel(editor, st)
                Ide.hint(editor, "No previous selection")
            } else {
                select(editor, st, entry.type, entry.anchor, entry.active, entry.expand, push = false)
            }
        } else if (!Grab.pop(editor, st)) {
            Ide.hint(editor, "No previous selection")
        }
    }

    /** meow-expand-N (0 = 10); without an expandable selection it falls back
     *  to meow-digit-argument (meow-selection-command-fallback). */
    private fun expandOrCount(editor: Editor, st: MeowState, n: Int) {
        if (editor.selectionModel.hasSelection() && st.selType in EXPANDABLE) {
            expand(editor, st, if (n == 0) 10 else n)
        } else {
            st.pendingCount = st.pendingCount * 10 + n
        }
    }

    private fun expand(editor: Editor, st: MeowState, n: Int) {
        val text = editor.document.charsSequence
        val doc = editor.document
        val back = backwardP(editor)
        val caret = editor.caretModel.offset
        val target: Int = when (st.selType) {
            SelType.CHAR -> caret + if (back) -n else n
            SelType.WORD, SelType.SYMBOL -> {
                val p = charPred(st.selType == SelType.SYMBOL)
                if (back) Words.prevStart(text, caret, n, p) else Words.nextEnd(text, caret, n, p)
            }
            SelType.LINE -> {
                val ln = doc.getLineNumber(caret)
                if (back) doc.getLineStartOffset((ln - n).coerceAtLeast(0))
                else doc.getLineEndOffset((ln + n).coerceAtMost(doc.lineCount - 1))
            }
            SelType.FIND, SelType.TILL -> {
                val ch = st.lastFind ?: return
                val t = nthCharTarget(text, ch, caret, n, backward = back, till = st.selType == SelType.TILL)
                if (t < 0) return
                t
            }
            else -> return
        }
        // meow-expand-selection-type defaults to 'select: the expanded
        // selection is demoted, so follow-up word motions re-mark and `x`
        // re-selects its line instead of extending
        select(editor, st, st.selType, mark(editor), target, expand = false)
    }
}
