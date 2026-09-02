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
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider

private const val TIGHT_FOLLOWERS = "$CLOSE_BRACKETS.,;:"

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

internal fun kill(
    editor: Editor,
    state: MeowState,
) {
    if (!allowModify(editor)) return
    val selectionModel = editor.selectionModel
    when {
        state.selType == SelType.JOIN && selectionModel.hasSelection() -> {
            joinKill(editor, state)
        }

        selectionModel.hasSelection() -> {
            prepareLineSelectionsForKill(editor, state)
            Ide.act(editor, IdeActions.ACTION_EDITOR_CUT)
            state.selType = SelType.NONE
        }

        else -> {
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
                after !in TIGHT_FOLLOWERS &&
                before !in OPEN_BRACKETS
        if (needsSpace) editor.document.insertString(start, " ")
        editor.caretModel.moveToOffset(start)
    }
    Selections.collapse(editor, state)
}

internal fun save(
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

internal fun yank(editor: Editor) {
    if (Edits.blockedReadOnly(editor)) return
    val clipboardText = Ide.clipboard() ?: return
    editCarets(editor, "Meow Yank") { caret ->
        val offset = caret.offset
        editor.document.insertString(offset, clipboardText)
        caret.moveToOffset(offset + clipboardText.length)
    }
}

internal fun replace(
    editor: Editor,
    state: MeowState,
) {
    if (!allowModify(editor) || !editor.selectionModel.hasSelection()) return
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

internal fun undo(
    editor: Editor,
    state: MeowState,
) {
    val hadSelection = editor.selectionModel.hasSelection()
    performUndo(editor)
    if (hadSelection) Selections.cancel(editor, state)
}

internal fun undoInSelection(editor: Editor) {
    if (editor.selectionModel.hasSelection()) performUndo(editor)
}

private fun performUndo(editor: Editor) {
    val project = editor.project ?: return
    val fileEditor = TextEditorProvider.getInstance().getTextEditor(editor)
    val manager = UndoManager.getInstance(project)
    if (manager.isUndoAvailable(fileEditor)) manager.undo(fileEditor)
}
