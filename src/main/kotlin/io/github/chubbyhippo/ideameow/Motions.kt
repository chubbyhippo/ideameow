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

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import kotlin.math.abs

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
            put("meow-back-word", MeowCommand { ed, st, _ -> wordMotion(ed, st, symbol = false, n = -st.takeCount(1)) })
            put("meow-back-symbol", MeowCommand { ed, st, _ -> wordMotion(ed, st, symbol = true, n = -st.takeCount(1)) })
            put("meow-mark-word", MeowCommand { ed, st, _ -> markWord(ed, st, symbol = false) })
            put("meow-mark-symbol", MeowCommand { ed, st, _ -> markWord(ed, st, symbol = true) })
            put("meow-line", MeowCommand { ed, st, _ -> line(ed, st) })
            put("meow-goto-line", MeowCommand { ed, st, _ -> gotoLine(ed, st) })
            put("meow-find", MeowCommand { _, st, _ -> st.pending = Pending.FIND })
            put("meow-till", MeowCommand { _, st, _ -> st.pending = Pending.TILL })
            put("forward-char", MeowCommand { ed, st, _ -> charOrExpand(ed, st, st.takeCount(1)) })
            put("backward-char", MeowCommand { ed, st, _ -> charOrExpand(ed, st, -st.takeCount(1)) })
            put("next-line", MeowCommand { ed, st, _ -> lineOrExpand(ed, st, st.takeCount(1)) })
            put("previous-line", MeowCommand { ed, st, _ -> lineOrExpand(ed, st, -st.takeCount(1)) })
            put(
                "move-beginning-of-line",
                MeowCommand { ed, st, _ -> moveToOrExpand(ed, st, SelType.CHAR, ::lineStartOffset) },
            )
            put("move-end-of-line", MeowCommand { ed, st, _ -> moveToOrExpand(ed, st, SelType.CHAR, ::lineEndOffset) })
            put("forward-word", MeowCommand { ed, st, _ -> wordOrExpand(ed, st, st.takeCount(1)) })
            put("backward-word", MeowCommand { ed, st, _ -> wordOrExpand(ed, st, -st.takeCount(1)) })
            put("forward-sentence", MeowCommand { ed, st, _ -> sentenceOrExpand(ed, st, st.takeCount(1)) })
            put("backward-sentence", MeowCommand { ed, st, _ -> sentenceOrExpand(ed, st, -st.takeCount(1)) })
            put("beginning-of-buffer", MeowCommand { ed, st, _ -> bufferBoundary(ed, st, top = true) })
            put("end-of-buffer", MeowCommand { ed, st, _ -> bufferBoundary(ed, st, top = false) })
        }

    private fun wordType(symbol: Boolean) = if (symbol) SelType.SYMBOL else SelType.WORD

    private val VERTICAL = setOf("meow-next", "meow-prev", "meow-next-expand", "meow-prev-expand")

    private fun charSelActive(
        editor: Editor,
        st: MeowState,
    ) = st.selType == SelType.CHAR && editor.selectionModel.hasSelection()

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
        st: MeowState,
        dx: Int,
    ) {
        val extend = charSelActive(editor, st)
        if (!extend && editor.selectionModel.hasSelection()) Selections.cancel(editor, st)
        val len = editor.document.textLength
        for (caret in editor.caretModel.allCarets) {
            val target = (caret.offset + dx).coerceIn(0, len)
            applyCaretMove(caret, target, extend)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    private fun moveLine(
        editor: Editor,
        st: MeowState,
        dy: Int,
    ) {
        val extend = charSelActive(editor, st)
        if (!extend) Selections.cancel(editor, st)
        val goal = goalColumn(editor, st)
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
        st: MeowState,
        dx: Int,
        dy: Int,
    ) {
        val posBefore = editor.caretModel.offset
        val goal = if (dy != 0) goalColumn(editor, st) else 0
        val len = editor.document.textLength
        val primary = editor.caretModel.primaryCaret
        for (caret in editor.caretModel.allCarets) {
            val target =
                if (dy == 0) {
                    (caret.offset + dx).coerceIn(0, len)
                } else {
                    movedLineOffset(editor, caret.offset, dy, columnFor(editor, caret, goal))
                }
            applyCaretMove(caret, target, true)
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

    private fun charOrExpand(
        editor: Editor,
        st: MeowState,
        dx: Int,
    ) {
        if (editor.selectionModel.hasSelection()) moveExpand(editor, st, dx, 0) else moveChar(editor, st, dx)
    }

    private fun lineOrExpand(
        editor: Editor,
        st: MeowState,
        dy: Int,
    ) {
        if (editor.selectionModel.hasSelection()) moveExpand(editor, st, 0, dy) else moveLine(editor, st, dy)
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
        st: MeowState,
        type: SelType,
        target: (Editor, Int) -> Int,
    ) {
        val extend = editor.selectionModel.hasSelection()
        val posBefore = editor.caretModel.offset
        val primary = editor.caretModel.primaryCaret
        for (caret in editor.caretModel.allCarets) {
            applyCaretMove(caret, target(editor, caret.offset), extend)
        }
        if (extend) {
            Selections.recordSelect(st, type, true, primary.leadSelectionOffset, primary.offset, posBefore)
            st.selType = type
            st.selExpand = true
            Grab.beacon(editor, st)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    private fun wordOrExpand(
        editor: Editor,
        st: MeowState,
        n: Int,
    ) {
        val text = editor.document.charsSequence
        val pred = charPred(symbol = false)
        moveToOrExpand(editor, st, SelType.WORD) { _, offset ->
            if (n >= 0) Words.nextEnd(text, offset, n, pred) else Words.prevStart(text, offset, -n, pred)
        }
    }

    private fun sentenceOrExpand(
        editor: Editor,
        st: MeowState,
        n: Int,
    ) {
        val text = editor.document.charsSequence
        moveToOrExpand(editor, st, SelType.CHAR) { _, offset ->
            if (n >= 0) nextSentenceEnd(text, offset, n) else prevSentenceStart(text, offset, -n)
        }
    }

    private fun bufferBoundary(
        editor: Editor,
        st: MeowState,
        top: Boolean,
    ) {
        val counted = st.pendingCount != 0 || st.negative
        val n = st.takeCount(1)
        moveToOrExpand(editor, st, SelType.CHAR) { ed, _ ->
            val len = ed.document.textLength
            if (!counted) {
                if (top) 0 else len
            } else {
                val tenth = len * n / 10
                val raw = (if (top) 1 + tenth else len - tenth).coerceIn(0, len)
                nextLineStart(ed, raw)
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
        st: MeowState,
        symbol: Boolean,
        n: Int,
    ) {
        if (n == 0) return
        val text = editor.document.charsSequence
        val type = wordType(symbol)
        val sm = editor.selectionModel
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

                else -> Words.fixSelectionMark(text, target, from, charPred(symbol))
            }
        Selections.select(editor, st, type, anchor, target, expand = extend)
    }

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

    private fun line(
        editor: Editor,
        st: MeowState,
    ) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val n = st.takeCount(1)
        val sm = editor.selectionModel
        val lastLine = doc.lineCount - 1
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

    private fun gotoLine(
        editor: Editor,
        st: MeowState,
    ) {
        val input = Messages.showInputDialog(editor.project, "Goto line:", "Meow", null) ?: return
        val doc = editor.document
        if (doc.textLength == 0) return
        val ln = parsedLineNumber(input, doc.lineCount) ?: return
        Selections.select(editor, st, SelType.LINE, doc.getLineStartOffset(ln), doc.getLineEndOffset(ln), expand = true)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

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
        st.lastFind = ch
        Selections.select(editor, st, if (till) SelType.TILL else SelType.FIND, caret, target, expand = false)
    }
}

internal sealed class EmacsChordAction(
    private val command: String,
) : DumbAwareAction() {
    init {
        isEnabledInModalContext = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && Meow.state(editor)?.mode == MeowMode.NORMAL
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val st = Meow.state(editor) ?: return
        Engine.COMMANDS[command]?.invoke(editor, st, e.dataContext)
        Meow.updateWidgets()
    }
}

internal class EmacsForwardCharAction : EmacsChordAction("forward-char")

internal class EmacsBackwardCharAction : EmacsChordAction("backward-char")

internal class EmacsNextLineAction : EmacsChordAction("next-line")

internal class EmacsPreviousLineAction : EmacsChordAction("previous-line")

internal class EmacsBeginningOfLineAction : EmacsChordAction("move-beginning-of-line")

internal class EmacsEndOfLineAction : EmacsChordAction("move-end-of-line")

internal class EmacsForwardWordAction : EmacsChordAction("forward-word")

internal class EmacsBackwardWordAction : EmacsChordAction("backward-word")

internal class EmacsBackwardSentenceAction : EmacsChordAction("backward-sentence")

internal class EmacsForwardSentenceAction : EmacsChordAction("forward-sentence")

internal class EmacsBeginningOfBufferAction : EmacsChordAction("beginning-of-buffer")

internal class EmacsEndOfBufferAction : EmacsChordAction("end-of-buffer")

internal class EmacsChordPromoter : ActionPromoter {
    override fun promote(
        actions: List<AnAction>,
        context: DataContext,
    ): List<AnAction> = actions.sortedByDescending { it is EmacsChordAction }
}
