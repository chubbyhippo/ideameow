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

/**
 * Text-mutating commands: entering INSERT (insert/append/open above/below),
 * change, delete, kill with meow's kill-line and join fallbacks, save / yank /
 * replace against the clipboard kill-ring, and undo. Multi-caret edits walk
 * the carets in descending offset order so beacon editing never invalidates
 * the offsets still to come.
 */
internal object Edits {

    val commands: Map<String, MeowCommand> = buildMap {
        put("meow-insert", MeowCommand { ed, st, _ -> insert(ed, st) })
        put("meow-append", MeowCommand { ed, st, _ -> append(ed, st) })
        put("meow-open-above", MeowCommand { ed, st, ctx -> openAbove(ed, st, ctx) })
        put("meow-open-below", MeowCommand { ed, st, ctx -> openBelow(ed, st, ctx) })
        put("meow-change", MeowCommand { ed, st, _ -> change(ed, st) })
        put("meow-delete", MeowCommand { ed, st, _ -> delete(ed, st) })
        put("meow-backward-delete", MeowCommand { ed, st, _ -> backwardDelete(ed, st) })
        put("meow-kill", MeowCommand { ed, st, ctx -> kill(ed, st, ctx) })
        put("meow-save", MeowCommand { ed, _, ctx -> save(ed, ctx) })
        put("meow-yank", MeowCommand { ed, _, _ -> yank(ed) })
        put("meow-replace", MeowCommand { ed, st, _ -> replace(ed, st) })
        put("meow-undo", MeowCommand { ed, st, ctx -> undo(ed, st, ctx) })
        // undo-in-selection: IDE undo cannot scope to a region — plain undo, see README
        put("meow-undo-in-selection", MeowCommand { ed, st, ctx -> undo(ed, st, ctx) })
    }

    /** One write command over every caret, highest offset first. */
    private inline fun editCarets(editor: Editor, commandName: String, crossinline edit: (Caret) -> Unit) {
        Ide.runWrite(editor, commandName) {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) edit(caret)
        }
    }

    private fun insert(editor: Editor, st: MeowState) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionStart)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun append(editor: Editor, st: MeowState) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionEnd)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openBelow(editor: Editor, st: MeowState, ctx: DataContext?) {
        Selections.cancel(editor, st)
        Ide.act(editor, ctx, "EditorStartNewLine")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openAbove(editor: Editor, st: MeowState, ctx: DataContext?) {
        Selections.cancel(editor, st)
        Ide.act(editor, ctx, "EditorStartNewLineBefore")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun change(editor: Editor, st: MeowState) {
        editCarets(editor, "Meow Change") { caret ->
            if (caret.hasSelection()) {
                editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                caret.removeSelection()
            } else {
                // fallback meow-change-char
                val o = caret.offset
                if (o < editor.document.textLength && editor.document.charsSequence[o] != '\n') {
                    editor.document.deleteString(o, o + 1)
                }
            }
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun delete(editor: Editor, st: MeowState) {
        editCarets(editor, "Meow Delete") { caret ->
            if (caret.hasSelection()) {
                editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                caret.removeSelection()
            } else {
                val o = caret.offset
                if (o < editor.document.textLength) editor.document.deleteString(o, o + 1)
            }
        }
        st.selType = SelType.NONE
    }

    private fun backwardDelete(editor: Editor, st: MeowState) {
        editCarets(editor, "Meow Backward Delete") { caret ->
            if (caret.hasSelection()) {
                editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                caret.removeSelection()
            } else {
                val o = caret.offset
                if (o > 0) editor.document.deleteString(o - 1, o)
            }
        }
        st.selType = SelType.NONE
    }

    private fun kill(editor: Editor, st: MeowState, ctx: DataContext?) {
        val sm = editor.selectionModel
        if (st.selType == SelType.JOIN && sm.hasSelection()) { joinKill(editor, st); return }
        if (sm.hasSelection()) {
            Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_CUT)
            st.selType = SelType.NONE
            return
        }
        // fallback meow-C-k: kill to end of line, or the newline when at eol
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

    /** Killing a join selection = delete-indentation: single space, none at
     *  line edges or against brackets (Emacs' fixup-whitespace). */
    private fun joinKill(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        val s = sm.selectionStart
        val e = sm.selectionEnd
        Ide.runWrite(editor, "Meow Join") {
            editor.document.deleteString(s, e)
            val text = editor.document.charsSequence
            val before = if (s > 0) text[s - 1] else '\n'
            val after = if (s < text.length) text[s] else '\n'
            if (before != '\n' && after != '\n' && !before.isWhitespace() && !after.isWhitespace() &&
                after !in ")]}.,;:" && before !in "([{"
            ) editor.document.insertString(s, " ")
            editor.caretModel.moveToOffset(s)
        }
        Selections.cancel(editor, st)
    }

    /** meow-save: copy, selection stays active. */
    private fun save(editor: Editor, ctx: DataContext?) {
        if (editor.selectionModel.hasSelection()) Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_COPY)
    }

    /** meow-yank: insert the clipboard at every caret, caret lands after it. */
    private fun yank(editor: Editor) {
        val clip = Ide.clipboard() ?: return
        editCarets(editor, "Meow Yank") { caret ->
            val off = caret.offset
            editor.document.insertString(off, clip)
            caret.moveToOffset(off + clip.length)
        }
    }

    /** meow-replace: selection := clipboard; the clipboard stays intact. */
    private fun replace(editor: Editor, st: MeowState) {
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

    /** meow-undo cancels the selection BEFORE undoing (meow does the same). */
    private fun undo(editor: Editor, st: MeowState, ctx: DataContext?) {
        Selections.cancel(editor, st)
        Ide.act(editor, ctx, IdeActions.ACTION_UNDO)
    }
}
