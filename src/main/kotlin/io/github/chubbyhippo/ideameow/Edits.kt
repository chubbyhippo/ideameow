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

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor

internal object Edits {
    val commands: Map<String, MeowCommand> =
        buildMap {
            put("meow-insert", MeowCommand { ed, st, _ -> insert(ed, st) })
            put("meow-append", MeowCommand { ed, st, _ -> append(ed, st) })
            put("meow-open-above", MeowCommand { ed, st, ctx -> openAbove(ed, st, ctx) })
            put("meow-open-below", MeowCommand { ed, st, ctx -> openBelow(ed, st, ctx) })
            put("meow-change", MeowCommand { ed, st, _ -> change(ed, st) })
            put("meow-delete", MeowCommand { ed, st, _ -> delete(ed, st) })
            put("meow-backward-delete", MeowCommand { ed, st, _ -> backwardDelete(ed, st) })
            put("meow-kill", MeowCommand { ed, st, ctx -> kill(ed, st, ctx) })
            put("meow-save", MeowCommand { ed, st, ctx -> save(ed, st, ctx) })
            put("meow-yank", MeowCommand { ed, _, _ -> yank(ed) })
            put("meow-replace", MeowCommand { ed, st, _ -> replace(ed, st) })
            put("meow-undo", MeowCommand { ed, st, ctx -> undo(ed, st, ctx) })
            put("meow-undo-in-selection", MeowCommand { ed, _, ctx -> undoInSelection(ed, ctx) })
            put("upcase-word", MeowCommand { ed, st, _ -> caseWord(ed, st, CaseOp.UPCASE) })
            put("downcase-word", MeowCommand { ed, st, _ -> caseWord(ed, st, CaseOp.DOWNCASE) })
            put("capitalize-word", MeowCommand { ed, st, _ -> caseWord(ed, st, CaseOp.CAPITALIZE) })
            put("kill-word", MeowCommand { ed, st, ctx -> killWord(ed, st, ctx) })
        }

    private enum class CaseOp(
        val commandName: String,
    ) {
        UPCASE("Meow Upcase Word"),
        DOWNCASE("Meow Downcase Word"),
        CAPITALIZE("Meow Capitalize Word"),
    }

    private inline fun editCarets(
        editor: Editor,
        commandName: String,
        crossinline edit: (Caret) -> Unit,
    ) {
        Ide.runWrite(editor, commandName) {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) edit(caret)
        }
    }

    internal fun allowModify(editor: Editor): Boolean = editor.document.isWritable && !editor.isViewer

    internal fun blockedReadOnly(editor: Editor): Boolean {
        if (allowModify(editor)) return false
        Ide.hint(editor, "Buffer is read-only")
        return true
    }

    private fun insert(
        editor: Editor,
        st: MeowState,
    ) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionStart)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Selections.resetSelectionMemory(st)
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun append(
        editor: Editor,
        st: MeowState,
    ) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionEnd)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Selections.resetSelectionMemory(st)
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openBelow(
        editor: Editor,
        st: MeowState,
        ctx: DataContext?,
    ) {
        if (blockedReadOnly(editor)) return
        Selections.collapse(editor, st)
        Ide.act(editor, ctx, "EditorStartNewLine")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openAbove(
        editor: Editor,
        st: MeowState,
        ctx: DataContext?,
    ) {
        if (blockedReadOnly(editor)) return
        Selections.collapse(editor, st)
        Ide.act(editor, ctx, "EditorStartNewLineBefore")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun deleteAtCaret(
        editor: Editor,
        caret: Caret,
        forward: Boolean,
    ) {
        if (caret.hasSelection()) {
            editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
            caret.removeSelection()
            return
        }
        val o = caret.offset
        if (forward) {
            if (o < editor.document.textLength) editor.document.deleteString(o, o + 1)
        } else if (o > 0) {
            editor.document.deleteString(o - 1, o)
        }
    }

    private fun change(
        editor: Editor,
        st: MeowState,
    ) {
        if (!allowModify(editor)) return
        val primary = editor.caretModel.primaryCaret
        if (!primary.hasSelection() && primary.offset >= editor.document.textLength) return
        editCarets(editor, "Meow Change") { caret -> deleteAtCaret(editor, caret, forward = true) }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun delete(
        editor: Editor,
        st: MeowState,
    ) {
        if (blockedReadOnly(editor)) return
        editCarets(editor, "Meow Delete") { caret -> deleteAtCaret(editor, caret, forward = true) }
        st.selType = SelType.NONE
    }

    private fun backwardDelete(
        editor: Editor,
        st: MeowState,
    ) {
        if (!allowModify(editor)) return
        editCarets(editor, "Meow Backward Delete") { caret -> deleteAtCaret(editor, caret, forward = false) }
        st.selType = SelType.NONE
    }

    private fun prepareLineSelectionsForKill(
        editor: Editor,
        st: MeowState,
    ) {
        if (st.selType != SelType.LINE) return
        val len = editor.document.textLength
        for (caret in editor.caretModel.allCarets.sortedByDescending { it.selectionEnd }) {
            if (!caret.hasSelection()) continue
            val end = caret.selectionEnd
            if (caret.offset >= end && end < len) {
                val start = caret.selectionStart
                caret.moveToOffset(end + 1)
                caret.setSelection(start, end + 1)
            }
        }
    }

    private fun kill(
        editor: Editor,
        st: MeowState,
        ctx: DataContext?,
    ) {
        if (!allowModify(editor)) return
        val sm = editor.selectionModel
        if (st.selType == SelType.JOIN && sm.hasSelection()) {
            joinKill(editor, st)
            return
        }
        if (sm.hasSelection()) {
            prepareLineSelectionsForKill(editor, st)
            Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_CUT)
            st.selType = SelType.NONE
            return
        }
        val doc = editor.document
        val caret = editor.caretModel.offset
        if (doc.textLength == 0) return
        val eol = doc.getLineEndOffset(doc.getLineNumber(caret))
        val end = if (caret == eol) (eol + 1).coerceAtMost(doc.textLength) else eol
        if (end > caret) {
            sm.setSelection(caret, end)
            Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_CUT)
        }
    }

    private fun joinKill(
        editor: Editor,
        st: MeowState,
    ) {
        val sm = editor.selectionModel
        val s = sm.selectionStart
        val e = sm.selectionEnd
        Ide.runWrite(editor, "Meow Join") {
            editor.document.deleteString(s, e)
            val text = editor.document.charsSequence
            val before = if (s > 0) text[s - 1] else '\n'
            val after = if (s < text.length) text[s] else '\n'
            if (before != '\n' &&
                after != '\n' &&
                !before.isWhitespace() &&
                !after.isWhitespace() &&
                after !in ")]}.,;:" &&
                before !in "([{"
            ) {
                editor.document.insertString(s, " ")
            }
            editor.caretModel.moveToOffset(s)
        }
        Selections.collapse(editor, st)
    }

    private fun save(
        editor: Editor,
        st: MeowState,
        ctx: DataContext?,
    ) {
        if (!editor.selectionModel.hasSelection()) return
        prepareLineSelectionsForKill(editor, st)
        Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_COPY)
        for (caret in editor.caretModel.allCarets) caret.removeSelection()
        st.selType = SelType.NONE
        st.selExpand = false
    }

    private fun yank(editor: Editor) {
        if (blockedReadOnly(editor)) return
        val clip = Ide.clipboard() ?: return
        editCarets(editor, "Meow Yank") { caret ->
            val off = caret.offset
            editor.document.insertString(off, clip)
            caret.moveToOffset(off + clip.length)
        }
    }

    private fun replace(
        editor: Editor,
        st: MeowState,
    ) {
        if (!allowModify(editor)) return
        if (!editor.selectionModel.hasSelection()) return
        val clip = (Ide.clipboard() ?: return).trimEnd('\n')
        editCarets(editor, "Meow Replace") { caret ->
            if (caret.hasSelection()) {
                val s = caret.selectionStart
                editor.document.replaceString(s, caret.selectionEnd, clip)
                caret.removeSelection()
                caret.moveToOffset(s + clip.length)
            }
        }
        st.selType = SelType.NONE
    }

    private fun undo(
        editor: Editor,
        st: MeowState,
        ctx: DataContext?,
    ) {
        if (editor.selectionModel.hasSelection()) Selections.cancel(editor, st)
        Ide.act(editor, ctx, IdeActions.ACTION_UNDO)
    }

    private fun undoInSelection(
        editor: Editor,
        ctx: DataContext?,
    ) {
        if (editor.selectionModel.hasSelection()) Ide.act(editor, ctx, IdeActions.ACTION_UNDO)
    }

    private fun casified(
        slice: String,
        op: CaseOp,
    ): String =
        when (op) {
            CaseOp.UPCASE -> slice.uppercase()
            CaseOp.DOWNCASE -> slice.lowercase()
            CaseOp.CAPITALIZE -> capitalizedWords(slice)
        }

    private fun capitalizedWords(slice: String): String {
        val pred = charPred(symbol = false)
        val out = StringBuilder(slice.length)
        var inWord = false
        for (c in slice) {
            if (pred(c)) {
                out.append(if (inWord) c.lowercaseChar() else c.uppercaseChar())
                inWord = true
            } else {
                out.append(c)
                inWord = false
            }
        }
        return out.toString()
    }

    private fun caseWord(
        editor: Editor,
        st: MeowState,
        op: CaseOp,
    ) {
        if (blockedReadOnly(editor)) return
        val n = st.takeCount(1)
        if (n == 0) return
        val hadSelection = editor.selectionModel.hasSelection()
        val pred = charPred(symbol = false)
        editCarets(editor, op.commandName) { caret ->
            val text = editor.document.charsSequence
            val from = caret.offset
            val target = if (n > 0) Words.nextEnd(text, from, n, pred) else Words.prevStart(text, from, -n, pred)
            val s = minOf(from, target)
            val e = maxOf(from, target)
            if (s == e) return@editCarets
            editor.document.replaceString(s, e, casified(text.subSequence(s, e).toString(), op))
            if (n > 0) caret.moveToOffset(e)
        }
        if (hadSelection) Selections.collapse(editor, st)
    }

    private fun killWord(
        editor: Editor,
        st: MeowState,
        ctx: DataContext?,
    ) {
        if (blockedReadOnly(editor)) return
        val n = st.takeCount(1)
        if (n == 0) return
        val text = editor.document.charsSequence
        val pred = charPred(symbol = false)
        var any = false
        for (caret in editor.caretModel.allCarets) {
            val from = caret.offset
            val target = if (n > 0) Words.nextEnd(text, from, n, pred) else Words.prevStart(text, from, -n, pred)
            if (target == from) {
                caret.removeSelection()
                continue
            }
            caret.setSelection(minOf(from, target), maxOf(from, target))
            any = true
        }
        if (any) Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_CUT)
        st.selType = SelType.NONE
        st.selExpand = false
    }
}

internal class EmacsUpcaseWordAction : EmacsChordAction("upcase-word")

internal class EmacsDowncaseWordAction : EmacsChordAction("downcase-word")

internal class EmacsCapitalizeWordAction : EmacsChordAction("capitalize-word")

internal class EmacsKillWordAction : EmacsChordAction("kill-word")
