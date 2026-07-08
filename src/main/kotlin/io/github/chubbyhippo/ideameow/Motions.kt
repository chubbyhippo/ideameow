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
import com.intellij.openapi.ui.Messages
import kotlin.math.abs

/**
 * Cursor motion and the selections it creates: char/line movement with the
 * -expand variants, word/symbol motions, meow-line, goto-line, and find/till.
 * Every behavior here follows meow-command.el, not vim intuition — see the
 * [wordMotion] doc for the direction-normalization rule that makes `w` then
 * `b` extend instead of re-mark.
 */
internal object Motions {
    val commands: Map<String, MeowCommand> =
        buildMap {
            put("meow-left", MeowCommand { ed, st, _ -> moveChar(ed, st, -st.takeCount(1)) })
            put("meow-right", MeowCommand { ed, st, _ -> moveChar(ed, st, st.takeCount(1)) })
            put("meow-next", MeowCommand { ed, st, _ -> moveLine(ed, st, st.takeCount(1)) })
            put("meow-prev", MeowCommand { ed, st, _ -> moveLine(ed, st, -st.takeCount(1)) })
            put("meow-left-expand", MeowCommand { ed, st, _ -> moveExpand(ed, st, -st.takeCount(1), 0) })
            put("meow-right-expand", MeowCommand { ed, st, _ -> moveExpand(ed, st, st.takeCount(1), 0) })
            put("meow-next-expand", MeowCommand { ed, st, _ -> moveExpand(ed, st, 0, st.takeCount(1)) })
            put("meow-prev-expand", MeowCommand { ed, st, _ -> moveExpand(ed, st, 0, -st.takeCount(1)) })
            put("meow-next-word", MeowCommand { ed, st, _ -> wordMotion(ed, st, symbol = false, n = st.takeCount(1)) })
            put("meow-next-symbol", MeowCommand { ed, st, _ -> wordMotion(ed, st, symbol = true, n = st.takeCount(1)) })
            // meow-back-word = meow-next-thing with -N
            put("meow-back-word", MeowCommand { ed, st, _ -> wordMotion(ed, st, symbol = false, n = -st.takeCount(1)) })
            put("meow-back-symbol", MeowCommand { ed, st, _ -> wordMotion(ed, st, symbol = true, n = -st.takeCount(1)) })
            put("meow-mark-word", MeowCommand { ed, st, _ -> markWord(ed, st, symbol = false) })
            put("meow-mark-symbol", MeowCommand { ed, st, _ -> markWord(ed, st, symbol = true) })
            put("meow-line", MeowCommand { ed, st, _ -> line(ed, st) })
            put("meow-goto-line", MeowCommand { ed, st, _ -> gotoLine(ed, st) })
            put("meow-find", MeowCommand { _, st, _ -> st.pending = Pending.FIND })
            put("meow-till", MeowCommand { _, st, _ -> st.pending = Pending.TILL })
        }

    private fun wordType(symbol: Boolean) = if (symbol) SelType.SYMBOL else SelType.WORD

    /** The commands whose chains keep Emacs' temporary-goal-column alive. */
    private val VERTICAL = setOf("meow-next", "meow-prev", "meow-next-expand", "meow-prev-expand")

    private fun charSelActive(
        editor: Editor,
        st: MeowState,
    ) = st.selType == SelType.CHAR && editor.selectionModel.hasSelection()

    /** Set (or keep) the goal column, Emacs temporary-goal-column style: it
     *  only survives while the previous command was a vertical move too. */
    private fun goalColumn(
        editor: Editor,
        st: MeowState,
    ): Int {
        if (st.goalColumn == null || st.lastCommand !in VERTICAL) {
            val doc = editor.document
            val off = editor.caretModel.offset
            st.goalColumn = off - doc.getLineStartOffset(doc.getLineNumber(off))
        }
        return st.goalColumn!!
    }

    /** next-line/previous-line target: goal/own column on the target line;
     *  past the first/last line the point goes to the buffer edge. */
    private fun movedLineOffset(
        editor: Editor,
        offset: Int,
        dy: Int,
        col: Int,
    ): Int {
        val doc = editor.document
        val ln = doc.getLineNumber(offset)
        val target = ln + dy
        return when {
            target < 0 -> {
                0
            }

            target > doc.lineCount - 1 -> {
                doc.textLength
            }

            else -> {
                val bol = doc.getLineStartOffset(target)
                bol + minOf(col, doc.getLineEndOffset(target) - bol)
            }
        }
    }

    /** meow-left/right run backward-char/forward-char: plain offsets, which
     *  cross newlines — h at bol lands on the previous line's end. */
    private fun moveChar(
        editor: Editor,
        st: MeowState,
        dx: Int,
    ) {
        val extend = charSelActive(editor, st)
        // meow-left/right cancel (clearing the history) only with a region
        if (!extend && editor.selectionModel.hasSelection()) Selections.cancel(editor, st)
        val len = editor.document.textLength
        for (caret in editor.caretModel.allCarets) {
            val target = (caret.offset + dx).coerceIn(0, len)
            if (extend) {
                val lead = caret.leadSelectionOffset
                caret.moveToOffset(target)
                caret.setSelection(lead, target)
            } else {
                caret.moveToOffset(target)
                caret.removeSelection()
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    private fun moveLine(
        editor: Editor,
        st: MeowState,
        dy: Int,
    ) {
        val extend = charSelActive(editor, st)
        // meow-next/prev run meow--cancel-selection unconditionally otherwise
        if (!extend) Selections.cancel(editor, st)
        val goal = goalColumn(editor, st)
        val doc = editor.document
        val primary = editor.caretModel.primaryCaret
        for (caret in editor.caretModel.allCarets) {
            val col =
                if (caret == primary) {
                    goal
                } else {
                    caret.offset - doc.getLineStartOffset(doc.getLineNumber(caret.offset))
                }
            val target = movedLineOffset(editor, caret.offset, dy, col)
            if (extend) {
                val lead = caret.leadSelectionOffset
                caret.moveToOffset(target)
                caret.setSelection(lead, target)
            } else {
                caret.moveToOffset(target)
                caret.removeSelection()
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /** meow-left/right/next/prev-expand: (expand . char) selection through
     *  meow--select — so the history is recorded — then the char/line motion. */
    private fun moveExpand(
        editor: Editor,
        st: MeowState,
        dx: Int,
        dy: Int,
    ) {
        val posBefore = editor.caretModel.offset
        val goal = if (dy != 0) goalColumn(editor, st) else 0
        val doc = editor.document
        val len = doc.textLength
        val primary = editor.caretModel.primaryCaret
        for (caret in editor.caretModel.allCarets) {
            val target =
                if (dy == 0) {
                    (caret.offset + dx).coerceIn(0, len)
                } else {
                    val col =
                        if (caret == primary) {
                            goal
                        } else {
                            caret.offset - doc.getLineStartOffset(doc.getLineNumber(caret.offset))
                        }
                    movedLineOffset(editor, caret.offset, dy, col)
                }
            val lead = caret.leadSelectionOffset
            caret.moveToOffset(target)
            caret.setSelection(lead, target)
        }
        Selections.recordSelect(
            st,
            SelType.CHAR,
            true,
            primary.leadSelectionOffset,
            primary.offset,
            posBefore,
        )
        st.selType = SelType.CHAR
        st.selExpand = true
        Grab.beacon(editor, st)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * meow-next-thing for word/symbol: when the current selection is the
     * matching (expand . type), the selection direction is normalized to the
     * motion FIRST (meow--direction-forward/-backward) — so after `w`, `e`
     * extends from the right end and `b` extends from the left end, anchored
     * at the opposite end (meow--make-selection keeps min/max of the original
     * region as the mark). Without a matching selection: fresh (select . type)
     * from point. No motion -> no selection change.
     */
    private fun wordMotion(
        editor: Editor,
        st: MeowState,
        symbol: Boolean,
        n: Int,
    ) {
        if (n == 0) return
        val text = editor.document.charsSequence
        val type = wordType(symbol)
        val sm = editor.selectionModel
        // meow-next-thing: a selection of another type (or none) is cancelled
        // FIRST — meow--cancel-selection, so the chain history restarts and a
        // later z pops the null placeholder, not the foreign selection
        if (!(sm.hasSelection() && st.selType == type)) Selections.cancel(editor, st)
        val extend = st.selExpand && st.selType == type && sm.hasSelection()
        val from =
            when {
                extend && n < 0 -> sm.selectionStart
                extend -> sm.selectionEnd
                else -> editor.caretModel.offset
            }
        val target =
            if (n > 0) {
                Words.nextEnd(text, from, n, charPred(symbol))
            } else {
                Words.prevStart(text, from, -n, charPred(symbol))
            }
        if (target == from) return
        val anchor =
            when {
                extend && n < 0 -> sm.selectionEnd

                extend -> sm.selectionStart

                // meow--fix-thing-selection-mark: a fresh selection snaps its
                // mark to the word's own bounds — the separators between the old
                // point and the word stay OUTSIDE (e e e steps bare words)
                else -> Words.fixSelectionMark(text, target, from, charPred(symbol))
            }
        Selections.select(editor, st, type, anchor, target, expand = extend)
    }

    /** meow-mark-word/-symbol: select the thing at point as (expand . type)
     *  and push its bounded regexp to the search ring — why `n` works after `w`. */
    private fun markWord(
        editor: Editor,
        st: MeowState,
        symbol: Boolean,
    ) {
        val neg = st.takeCount(1) < 0
        val text = editor.document.charsSequence
        val b =
            Words.boundsAt(text, editor.caretModel.offset, charPred(symbol))
                ?: run {
                    Ide.hint(editor, "No word here")
                    return
                }
        val (s, e) = b
        if (neg) {
            Selections.select(editor, st, wordType(symbol), e, s, expand = true)
        } else {
            Selections.select(editor, st, wordType(symbol), s, e, expand = true)
        }
        Search.push(st, Regex("\\b" + Regex.escape(text.subSequence(s, e).toString()) + "\\b"))
    }

    /** meow-line: [bol, eol) without the newline; repeats extend in the
     *  selection's direction, a negative argument reverses. */
    private fun line(
        editor: Editor,
        st: MeowState,
    ) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val n = st.takeCount(1)
        val sm = editor.selectionModel
        val lastLine = doc.lineCount - 1
        // extension needs exactly (expand . line) — a digit-expanded
        // (select . line) selection re-selects the current line instead
        if (st.selType == SelType.LINE && st.selExpand && sm.hasSelection()) {
            val caretLn = doc.getLineNumber(editor.caretModel.offset)
            if (Selections.backwardP(editor)) {
                val ln = (caretLn - abs(n)).coerceAtLeast(0)
                Selections.select(editor, st, SelType.LINE, Selections.mark(editor), doc.getLineStartOffset(ln), expand = true)
            } else {
                val ln = (caretLn + abs(n)).coerceAtMost(lastLine)
                Selections.select(editor, st, SelType.LINE, Selections.mark(editor), doc.getLineEndOffset(ln), expand = true)
            }
            return
        }
        val ln = doc.getLineNumber(editor.caretModel.offset)
        if (n < 0) {
            val startLn = (ln + n + 1).coerceAtLeast(0)
            Selections.select(editor, st, SelType.LINE, doc.getLineEndOffset(ln), doc.getLineStartOffset(startLn), expand = true)
        } else {
            val endLn = (ln + n - 1).coerceAtMost(lastLine)
            Selections.select(editor, st, SelType.LINE, doc.getLineStartOffset(ln), doc.getLineEndOffset(endLn), expand = true)
        }
    }

    /** meow-goto-line: select the target line (expand . line) and recenter. */
    private fun gotoLine(
        editor: Editor,
        st: MeowState,
    ) {
        val input = Messages.showInputDialog(editor.project, "Goto line:", "Meow", null) ?: return
        val doc = editor.document
        if (doc.textLength == 0) return
        val ln = ((input.trim().toIntOrNull() ?: return) - 1).coerceIn(0, doc.lineCount - 1)
        Selections.select(editor, st, SelType.LINE, doc.getLineStartOffset(ln), doc.getLineEndOffset(ln), expand = true)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    /** The second half of meow-find/meow-till, once the char arrives. */
    fun findTill(
        editor: Editor,
        st: MeowState,
        ch: Char,
        till: Boolean,
    ) {
        val n = st.takeCount(1)
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val target = nthCharTarget(text, ch, caret, abs(n), backward = n < 0, till = till)
        if (target < 0) {
            Ide.hint(editor, "char not found: $ch")
            return
        }
        Selections.select(editor, st, if (till) SelType.TILL else SelType.FIND, caret, target, expand = false)
        st.lastFind = ch
    }
}
