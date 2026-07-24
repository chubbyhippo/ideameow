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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor

internal object Edits {
    val commands: Map<String, MeowCommand> =
        buildMap {
            put("meow-insert", MeowCommand { editor, state -> enterInsert(editor, state, atSelectionEnd = false) })
            put("meow-append", MeowCommand { editor, state -> enterInsert(editor, state, atSelectionEnd = true) })
            put("meow-open-above", MeowCommand { editor, state -> openLine(editor, state, "EditorStartNewLineBefore") })
            put("meow-open-below", MeowCommand { editor, state -> openLine(editor, state, "EditorStartNewLine") })
            put("meow-change", MeowCommand { editor, state -> change(editor, state) })
            put("meow-delete", MeowCommand { editor, state -> delete(editor, state) })
            put("meow-backward-delete", MeowCommand { editor, state -> backwardDelete(editor, state) })
            put("meow-kill", MeowCommand { editor, state -> kill(editor, state) })
            put("meow-save", MeowCommand { editor, state -> save(editor, state) })
            put("meow-yank", MeowCommand { editor, _ -> yank(editor) })
            put("meow-replace", MeowCommand { editor, state -> replace(editor, state) })
            put("meow-undo", MeowCommand { editor, state -> undo(editor, state) })
            put("meow-undo-in-selection", MeowCommand { editor, _ -> undoInSelection(editor) })
            put("upcase-word", MeowCommand { editor, state -> caseWord(editor, state, CaseOp.UPCASE) })
            put("downcase-word", MeowCommand { editor, state -> caseWord(editor, state, CaseOp.DOWNCASE) })
            put("capitalize-word", MeowCommand { editor, state -> caseWord(editor, state, CaseOp.CAPITALIZE) })
            put("kill-word", MeowCommand { editor, state -> killWord(editor, state) })
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

    private fun enterInsert(
        editor: Editor,
        state: MeowState,
        atSelectionEnd: Boolean,
    ) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) {
                caret.moveToOffset(if (atSelectionEnd) caret.selectionEnd else caret.selectionStart)
            }
            caret.removeSelection()
        }
        state.selType = SelType.NONE
        Selections.resetSelectionMemory(state)
        Meow.setMode(editor, state, MeowMode.INSERT)
    }

    private fun openLine(
        editor: Editor,
        state: MeowState,
        actionId: String,
    ) {
        if (blockedReadOnly(editor)) return
        Selections.collapse(editor, state)
        Ide.act(editor, actionId)
        Meow.setMode(editor, state, MeowMode.INSERT)
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
        val offset = caret.offset
        if (forward) {
            if (offset < editor.document.textLength) editor.document.deleteString(offset, offset + 1)
        } else if (offset > 0) {
            editor.document.deleteString(offset - 1, offset)
        }
    }

    private fun change(
        editor: Editor,
        state: MeowState,
    ) {
        if (!allowModify(editor)) return
        val primary = editor.caretModel.primaryCaret
        if (!primary.hasSelection() && primary.offset >= editor.document.textLength) return
        editCarets(editor, "Meow Change") { caret -> deleteAtCaret(editor, caret, forward = true) }
        state.selType = SelType.NONE
        Meow.setMode(editor, state, MeowMode.INSERT)
    }

    private fun delete(
        editor: Editor,
        state: MeowState,
    ) {
        if (blockedReadOnly(editor)) return
        editCarets(editor, "Meow Delete") { caret -> deleteAtCaret(editor, caret, forward = true) }
        state.selType = SelType.NONE
    }

    private fun backwardDelete(
        editor: Editor,
        state: MeowState,
    ) {
        if (!allowModify(editor)) return
        editCarets(editor, "Meow Backward Delete") { caret -> deleteAtCaret(editor, caret, forward = false) }
        state.selType = SelType.NONE
    }

    private fun prepareLineSelectionsForKill(
        editor: Editor,
        state: MeowState,
    ) {
        if (state.selType != SelType.LINE) return
        val length = editor.document.textLength
        for (caret in editor.caretModel.allCarets.sortedByDescending { it.selectionEnd }) {
            if (!caret.hasSelection()) continue
            val end = caret.selectionEnd
            if (caret.offset >= end && end < length) {
                val start = caret.selectionStart
                caret.moveToOffset(end + 1)
                caret.setSelection(start, end + 1)
            }
        }
    }

    private fun kill(
        editor: Editor,
        state: MeowState,
    ) {
        if (!allowModify(editor)) return
        val selectionModel = editor.selectionModel
        if (state.selType == SelType.JOIN && selectionModel.hasSelection()) {
            joinKill(editor, state)
            return
        }
        if (selectionModel.hasSelection()) {
            prepareLineSelectionsForKill(editor, state)
            Ide.act(editor, IdeActions.ACTION_EDITOR_CUT)
            state.selType = SelType.NONE
            return
        }
        val doc = editor.document
        val caret = editor.caretModel.offset
        if (doc.textLength == 0) return
        val lineEnd = doc.getLineEndOffset(doc.getLineNumber(caret))
        val end = if (caret == lineEnd) (lineEnd + 1).coerceAtMost(doc.textLength) else lineEnd
        if (end > caret) {
            selectionModel.setSelection(caret, end)
            Ide.act(editor, IdeActions.ACTION_EDITOR_CUT)
        }
    }

    private fun joinKill(
        editor: Editor,
        state: MeowState,
    ) {
        val selectionModel = editor.selectionModel
        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        Ide.runWrite(editor, "Meow Join") {
            editor.document.deleteString(start, end)
            val text = editor.document.charsSequence
            val before = if (start > 0) text[start - 1] else '\n'
            val after = if (start < text.length) text[start] else '\n'
            val needsSpace =
                before != '\n' &&
                    after != '\n' &&
                    !before.isWhitespace() &&
                    !after.isWhitespace() &&
                    after !in ")]}.,;:" &&
                    before !in "([{"
            if (needsSpace) editor.document.insertString(start, " ")
            editor.caretModel.moveToOffset(start)
        }
        Selections.collapse(editor, state)
    }

    private fun save(
        editor: Editor,
        state: MeowState,
    ) {
        if (!editor.selectionModel.hasSelection()) return
        prepareLineSelectionsForKill(editor, state)
        Ide.act(editor, IdeActions.ACTION_EDITOR_COPY)
        for (caret in editor.caretModel.allCarets) caret.removeSelection()
        state.selType = SelType.NONE
        state.selExpand = false
    }

    private fun yank(editor: Editor) {
        if (blockedReadOnly(editor)) return
        val clipboardText = Ide.clipboard() ?: return
        editCarets(editor, "Meow Yank") { caret ->
            val offset = caret.offset
            editor.document.insertString(offset, clipboardText)
            caret.moveToOffset(offset + clipboardText.length)
        }
    }

    private fun replace(
        editor: Editor,
        state: MeowState,
    ) {
        if (!allowModify(editor)) return
        if (!editor.selectionModel.hasSelection()) return
        val clipboardText = (Ide.clipboard() ?: return).trimEnd('\n')
        editCarets(editor, "Meow Replace") { caret ->
            if (caret.hasSelection()) {
                val start = caret.selectionStart
                editor.document.replaceString(start, caret.selectionEnd, clipboardText)
                caret.removeSelection()
                caret.moveToOffset(start + clipboardText.length)
            }
        }
        state.selType = SelType.NONE
    }

    private fun undo(
        editor: Editor,
        state: MeowState,
    ) {
        if (editor.selectionModel.hasSelection()) Selections.cancel(editor, state)
        Ide.act(editor, IdeActions.ACTION_UNDO)
    }

    private fun undoInSelection(editor: Editor) {
        if (editor.selectionModel.hasSelection()) Ide.act(editor, IdeActions.ACTION_UNDO)
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
        for (char in slice) {
            if (pred(char)) {
                out.append(if (inWord) char.lowercaseChar() else char.uppercaseChar())
                inWord = true
            } else {
                out.append(char)
                inWord = false
            }
        }
        return out.toString()
    }

    private fun caseWord(
        editor: Editor,
        state: MeowState,
        op: CaseOp,
    ) {
        if (blockedReadOnly(editor)) return
        val count = state.takeCount(1)
        if (count == 0) return
        val hadSelection = editor.selectionModel.hasSelection()
        val pred = charPred(symbol = false)
        editCarets(editor, op.commandName) { caret ->
            val text = editor.document.charsSequence
            val from = caret.offset
            val target = Words.move(text, from, count, pred)
            val start = minOf(from, target)
            val end = maxOf(from, target)
            if (start == end) return@editCarets
            editor.document.replaceString(start, end, casified(text.subSequence(start, end).toString(), op))
            if (count > 0) caret.moveToOffset(end)
        }
        if (hadSelection) Selections.collapse(editor, state)
    }

    private fun killWord(
        editor: Editor,
        state: MeowState,
    ) {
        if (blockedReadOnly(editor)) return
        val count = state.takeCount(1)
        if (count == 0) return
        val text = editor.document.charsSequence
        val pred = charPred(symbol = false)
        var any = false
        for (caret in editor.caretModel.allCarets) {
            val from = caret.offset
            val target = Words.move(text, from, count, pred)
            if (target == from) {
                caret.removeSelection()
                continue
            }
            caret.setSelection(minOf(from, target), maxOf(from, target))
            any = true
        }
        if (any) Ide.act(editor, IdeActions.ACTION_EDITOR_CUT)
        state.selType = SelType.NONE
        state.selExpand = false
    }
}
