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

    internal fun blockedReadOnly(editor: Editor): Boolean {
        if (allowModify(editor)) return false
        Ide.hint(editor, "Buffer is read-only")
        return true
    }
}

internal enum class CaseOp(
    val commandName: String,
) {
    UPCASE("Meow Upcase Word"),
    DOWNCASE("Meow Downcase Word"),
    CAPITALIZE("Meow Capitalize Word"),
}

internal inline fun editCarets(
    editor: Editor,
    commandName: String,
    crossinline edit: (Caret) -> Unit,
) {
    Ide.runWrite(editor, commandName) {
        for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) edit(caret)
    }
}

internal fun allowModify(editor: Editor): Boolean = editor.document.isWritable && !editor.isViewer

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
    if (Edits.blockedReadOnly(editor)) return
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
    if (Edits.blockedReadOnly(editor)) return
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
