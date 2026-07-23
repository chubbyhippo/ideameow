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
import com.intellij.openapi.ui.Messages
import kotlin.math.abs

internal object Motions {
    val commands: Map<String, MeowCommand> =
        buildMap {
            put("meow-left", MeowCommand { editor, state -> moveChar(editor, state, -state.takeCount(1)) })
            put("meow-right", MeowCommand { editor, state -> moveChar(editor, state, state.takeCount(1)) })
            put("meow-next", MeowCommand { editor, state -> moveLine(editor, state, state.takeCount(1)) })
            put("meow-prev", MeowCommand { editor, state -> moveLine(editor, state, -state.takeCount(1)) })
            put(
                "meow-left-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = -state.takeCount(1), dy = 0) },
            )
            put(
                "meow-right-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = state.takeCount(1), dy = 0) },
            )
            put(
                "meow-next-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = 0, dy = state.takeCount(1)) },
            )
            put(
                "meow-prev-expand",
                MeowCommand { editor, state -> moveExpand(editor, state, dx = 0, dy = -state.takeCount(1)) },
            )
            put(
                "meow-next-word",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = false, n = state.takeCount(1)) },
            )
            put(
                "meow-next-symbol",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = true, n = state.takeCount(1)) },
            )
            put(
                "meow-back-word",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = false, n = -state.takeCount(1)) },
            )
            put(
                "meow-back-symbol",
                MeowCommand { editor, state -> wordMotion(editor, state, symbol = true, n = -state.takeCount(1)) },
            )
            put("meow-mark-word", MeowCommand { editor, state -> markWord(editor, state, symbol = false) })
            put("meow-mark-symbol", MeowCommand { editor, state -> markWord(editor, state, symbol = true) })
            put("meow-line", MeowCommand { editor, state -> line(editor, state) })
            put("meow-goto-line", MeowCommand { editor, state -> gotoLine(editor, state) })
            put("meow-find", MeowCommand { _, state -> state.pending = Pending.FIND })
            put("meow-till", MeowCommand { _, state -> state.pending = Pending.TILL })
            put("forward-char", MeowCommand { editor, state -> charOrExpand(editor, state, state.takeCount(1)) })
            put("backward-char", MeowCommand { editor, state -> charOrExpand(editor, state, -state.takeCount(1)) })
            put(
                "next-line",
                MeowCommand { editor, state ->
                    lineOrExpand(editor, state, state.takeCount(1))
                    state.lastCommand = "next-line"
                },
            )
            put(
                "previous-line",
                MeowCommand { editor, state ->
                    lineOrExpand(editor, state, -state.takeCount(1))
                    state.lastCommand = "previous-line"
                },
            )
            put(
                "move-beginning-of-line",
                MeowCommand { editor, state -> moveToOrExpand(editor, state, SelType.CHAR, ::lineStartOffset) },
            )
            put(
                "move-end-of-line",
                MeowCommand { editor, state -> moveToOrExpand(editor, state, SelType.CHAR, ::lineEndOffset) },
            )
            put("forward-word", MeowCommand { editor, state -> wordOrExpand(editor, state, state.takeCount(1)) })
            put("backward-word", MeowCommand { editor, state -> wordOrExpand(editor, state, -state.takeCount(1)) })
            put(
                "forward-sentence",
                MeowCommand { editor, state -> sentenceOrExpand(editor, state, state.takeCount(1)) },
            )
            put(
                "backward-sentence",
                MeowCommand { editor, state -> sentenceOrExpand(editor, state, -state.takeCount(1)) },
            )
            put("beginning-of-buffer", MeowCommand { editor, state -> bufferBoundary(editor, state, top = true) })
            put("end-of-buffer", MeowCommand { editor, state -> bufferBoundary(editor, state, top = false) })
            put(
                "forward-paragraph",
                MeowCommand { editor, state -> paragraphOrExpand(editor, state, state.takeCount(1)) },
            )
            put(
                "backward-paragraph",
                MeowCommand { editor, state -> paragraphOrExpand(editor, state, -state.takeCount(1)) },
            )
        }

    private fun wordType(symbol: Boolean) = if (symbol) SelType.SYMBOL else SelType.WORD

    private val VERTICAL =
        setOf("meow-next", "meow-prev", "meow-next-expand", "meow-prev-expand", "next-line", "previous-line")

    private fun charSelActive(
        editor: Editor,
        state: MeowState,
    ) = state.selType == SelType.CHAR && editor.selectionModel.hasSelection()

    private fun goalColumn(
        editor: Editor,
        state: MeowState,
    ): Int {
        if (state.goalColumn == null || state.lastCommand !in VERTICAL) {
            val doc = editor.document
            val off = editor.caretModel.offset
            state.goalColumn = off - doc.getLineStartOffset(doc.getLineNumber(off))
        }
        return state.goalColumn!!
    }

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

    private fun moveChar(
        editor: Editor,
        state: MeowState,
        dx: Int,
    ) {
        val extend = charSelActive(editor, state)
        if (!extend && editor.selectionModel.hasSelection()) Selections.cancel(editor, state)
        val len = editor.document.textLength
        for (caret in editor.caretModel.allCarets) {
            val target = (caret.offset + dx).coerceIn(0, len)
            applyCaretMove(caret, target, extend)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    private fun moveLine(
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

    private fun columnFor(
        editor: Editor,
        caret: Caret,
        goal: Int,
    ): Int {
        if (caret == editor.caretModel.primaryCaret) return goal
        val doc = editor.document
        return caret.offset - doc.getLineStartOffset(doc.getLineNumber(caret.offset))
    }

    private fun applyCaretMove(
        caret: Caret,
        target: Int,
        extend: Boolean,
    ) {
        if (extend) {
            val lead = caret.leadSelectionOffset
            caret.moveToOffset(target)
            caret.setSelection(lead, target)
        } else {
            caret.moveToOffset(target)
            caret.removeSelection()
        }
    }

    private fun moveExpand(
        editor: Editor,
        state: MeowState,
        dx: Int,
        dy: Int,
    ) {
        val posBefore = editor.caretModel.offset
        val goal = if (dy != 0) goalColumn(editor, state) else 0
        val len = editor.document.textLength
        for (caret in editor.caretModel.allCarets) {
            val target =
                if (dy == 0) {
                    (caret.offset + dx).coerceIn(0, len)
                } else {
                    movedLineOffset(editor, caret.offset, dy, columnFor(editor, caret, goal))
                }
            applyCaretMove(caret, target, extend = true)
        }
        recordExpandedSelection(editor, state, SelType.CHAR, posBefore)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    private fun recordExpandedSelection(
        editor: Editor,
        state: MeowState,
        type: SelType,
        posBefore: Int,
    ) {
        val primary = editor.caretModel.primaryCaret
        Selections.recordSelect(state, type, expand = true, primary.leadSelectionOffset, primary.offset, posBefore)
        state.selType = type
        state.selExpand = true
        Grab.beacon(editor, state)
    }

    private fun charOrExpand(
        editor: Editor,
        state: MeowState,
        dx: Int,
    ) {
        if (editor.selectionModel.hasSelection()) moveExpand(editor, state, dx, dy = 0) else moveChar(editor, state, dx)
    }

    private fun lineOrExpand(
        editor: Editor,
        state: MeowState,
        dy: Int,
    ) {
        if (editor.selectionModel.hasSelection()) moveExpand(editor, state, dx = 0, dy) else moveLine(editor, state, dy)
    }

    private fun lineStartOffset(
        editor: Editor,
        offset: Int,
    ): Int {
        val doc = editor.document
        return doc.getLineStartOffset(doc.getLineNumber(offset.coerceIn(0, doc.textLength)))
    }

    private fun lineEndOffset(
        editor: Editor,
        offset: Int,
    ): Int {
        val doc = editor.document
        return doc.getLineEndOffset(doc.getLineNumber(offset.coerceIn(0, doc.textLength)))
    }

    private fun moveToOrExpand(
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

    private fun wordOrExpand(
        editor: Editor,
        state: MeowState,
        n: Int,
    ) {
        val text = editor.document.charsSequence
        val pred = charPred(symbol = false)
        moveToOrExpand(editor, state, SelType.WORD) { _, offset -> Words.move(text, offset, n, pred) }
    }

    private fun sentenceOrExpand(
        editor: Editor,
        state: MeowState,
        n: Int,
    ) {
        val text = editor.document.charsSequence
        moveToOrExpand(editor, state, SelType.CHAR) { _, offset ->
            if (n >= 0) nextSentenceEnd(text, offset, n) else prevSentenceStart(text, offset, -n)
        }
    }

    private fun paragraphOrExpand(
        editor: Editor,
        state: MeowState,
        n: Int,
    ) {
        val text = editor.document.charsSequence
        moveToOrExpand(editor, state, SelType.CHAR) { _, offset ->
            if (n >= 0) Paragraphs.nextEnd(text, offset, n) else Paragraphs.prevStart(text, offset, -n)
        }
    }

    private fun bufferBoundary(
        editor: Editor,
        state: MeowState,
        top: Boolean,
    ) {
        val counted = state.pendingCount != 0 || state.negative
        val n = state.takeCount(1)
        moveToOrExpand(editor, state, SelType.CHAR) { editor, _ ->
            val len = editor.document.textLength
            if (!counted) {
                if (top) 0 else len
            } else {
                val tenth = len * n / 10
                val raw = (if (top) tenth else len - tenth).coerceIn(0, len)
                nextLineStart(editor, raw)
            }
        }
    }

    private fun nextLineStart(
        editor: Editor,
        offset: Int,
    ): Int {
        val doc = editor.document
        if (doc.textLength == 0) return 0
        val ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
        return if (ln >= doc.lineCount - 1) doc.textLength else doc.getLineStartOffset(ln + 1)
    }

    private fun wordMotion(
        editor: Editor,
        state: MeowState,
        symbol: Boolean,
        n: Int,
    ) {
        if (n == 0) return
        val text = editor.document.charsSequence
        val type = wordType(symbol)
        val pred = charPred(symbol)
        val sm = editor.selectionModel
        if (!(sm.hasSelection() && state.selType == type)) Selections.cancel(editor, state)
        val extend = state.selExpand && state.selType == type && sm.hasSelection()
        val from =
            when {
                extend && n < 0 -> sm.selectionStart
                extend -> sm.selectionEnd
                else -> editor.caretModel.offset
            }
        val target = Words.move(text, from, n, pred)
        if (target == from) return
        val anchor =
            when {
                extend && n < 0 -> sm.selectionEnd

                extend -> sm.selectionStart

                else -> Words.fixSelectionMark(text, target, from, pred)
            }
        Selections.select(editor, state, type, anchor, target, expand = extend)
    }

    private fun markWord(
        editor: Editor,
        state: MeowState,
        symbol: Boolean,
    ) {
        val neg = state.takeCount(1) < 0
        val text = editor.document.charsSequence
        val b =
            Words.boundsAt(text, editor.caretModel.offset, charPred(symbol))
                ?: run {
                    Ide.hint(editor, "No word here")
                    return
                }
        val (s, e) = b
        val (mark, point) = if (neg) e to s else s to e
        Selections.select(editor, state, wordType(symbol), mark, point, expand = true)
        val quoted = Regex.escape(text.subSequence(s, e).toString())
        val pattern = if (symbol) "(?<![\\w$])$quoted(?![\\w$])" else "\\b$quoted\\b"
        Search.push(state, Regex(pattern))
    }

    private fun line(
        editor: Editor,
        state: MeowState,
    ) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val n = state.takeCount(1)
        val ln = doc.getLineNumber(editor.caretModel.offset)
        if (state.selType == SelType.LINE && state.selExpand && editor.selectionModel.hasSelection()) {
            val point = Selections.lineExpandPoint(doc, ln, abs(n), Selections.backwardP(editor))
            Selections.select(editor, state, SelType.LINE, Selections.mark(editor), point, expand = true)
            return
        }
        val (mark, point) =
            if (n < 0) {
                doc.getLineEndOffset(ln) to doc.getLineStartOffset((ln + n + 1).coerceAtLeast(0))
            } else {
                doc.getLineStartOffset(ln) to doc.getLineEndOffset((ln + n - 1).coerceAtMost(doc.lineCount - 1))
            }
        Selections.select(editor, state, SelType.LINE, mark, point, expand = true)
    }

    private fun gotoLine(
        editor: Editor,
        state: MeowState,
    ) {
        val input = Messages.showInputDialog(editor.project, "Goto line:", "Meow", null) ?: return
        val doc = editor.document
        if (doc.textLength == 0) return
        val ln = parsedLineNumber(input, doc.lineCount) ?: return
        Selections.select(
            editor,
            state,
            SelType.LINE,
            doc.getLineStartOffset(ln),
            doc.getLineEndOffset(ln),
            expand = true,
        )
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    fun findTill(
        editor: Editor,
        state: MeowState,
        ch: Char,
        till: Boolean,
    ) {
        val n = state.takeCount(1)
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val target = nthCharTarget(text, ch, caret, abs(n), backward = n < 0, till = till)
        if (target < 0) {
            Ide.hint(editor, "char not found: $ch")
            return
        }
        state.lastFind = ch
        Selections.select(editor, state, if (till) SelType.TILL else SelType.FIND, caret, target, expand = false)
    }
}
