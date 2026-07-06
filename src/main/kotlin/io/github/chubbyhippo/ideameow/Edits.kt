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
        put("meow-save", MeowCommand { ed, st, ctx -> save(ed, st, ctx) })
        put("meow-yank", MeowCommand { ed, _, _ -> yank(ed) })
        put("meow-replace", MeowCommand { ed, st, _ -> replace(ed, st) })
        put("meow-undo", MeowCommand { ed, st, ctx -> undo(ed, st, ctx) })
        put("meow-undo-in-selection", MeowCommand { ed, st, ctx -> undoInSelection(ed, st, ctx) })
    }

    /** One write command over every caret, highest offset first. */
    private inline fun editCarets(editor: Editor, commandName: String, crossinline edit: (Caret) -> Unit) {
        Ide.runWrite(editor, commandName) {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) edit(caret)
        }
    }

    /**
     * meow--allow-modify-p (meow-util.el): read-only buffers keep the full
     * NORMAL layout, but the text-changing commands are inert. meow gates
     * kill/change/backspace/replace into SILENT no-ops; delete/yank/open
     * (and swap-grab) instead fail with Emacs' "Buffer is read-only" error —
     * surfaced here as an editor hint.
     */
    internal fun allowModify(editor: Editor): Boolean =
        editor.document.isWritable && !editor.isViewer

    /** @return true when the edit must be blocked — telling the user why. */
    internal fun blockedReadOnly(editor: Editor): Boolean {
        if (allowModify(editor)) return false
        Ide.hint(editor, "Buffer is read-only")
        return true
    }

    private fun insert(editor: Editor, st: MeowState) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionStart)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Selections.resetSelectionMemory(st) // meow-insert runs meow--cancel-selection
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun append(editor: Editor, st: MeowState) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionEnd)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Selections.resetSelectionMemory(st) // meow-append runs meow--cancel-selection
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openBelow(editor: Editor, st: MeowState, ctx: DataContext?) {
        if (blockedReadOnly(editor)) return
        Selections.collapse(editor, st) // meow-open-below never cancels; RET just deactivates
        Ide.act(editor, ctx, "EditorStartNewLine")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openAbove(editor: Editor, st: MeowState, ctx: DataContext?) {
        if (blockedReadOnly(editor)) return
        Selections.collapse(editor, st) // as in openBelow: no history clearing
        Ide.act(editor, ctx, "EditorStartNewLineBefore")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun change(editor: Editor, st: MeowState) {
        if (!allowModify(editor)) return // meow gates change silently
        // fallback meow-change-char at point-max: nothing happens, not even INSERT
        val primary = editor.caretModel.primaryCaret
        if (!primary.hasSelection() && primary.offset >= editor.document.textLength) return
        editCarets(editor, "Meow Change") { caret ->
            if (caret.hasSelection()) {
                editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                caret.removeSelection()
            } else {
                // fallback meow-change-char: delete-char takes ANY char,
                // newlines included (probed against meow 1.5.0)
                val o = caret.offset
                if (o < editor.document.textLength) {
                    editor.document.deleteString(o, o + 1)
                }
            }
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun delete(editor: Editor, st: MeowState) {
        if (blockedReadOnly(editor)) return
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
        if (!allowModify(editor)) return // meow gates backspace silently
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

    /**
     * meow--prepare-region-for-kill (meow-util.el): a FORWARD line-type
     * selection takes its trailing newline with it before a kill or save —
     * and meow's point (the caret) moves past that newline too. Backward
     * selections and the last line are killed as-is.
     */
    private fun prepareLineSelectionsForKill(editor: Editor, st: MeowState) {
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

    private fun kill(editor: Editor, st: MeowState, ctx: DataContext?) {
        if (!allowModify(editor)) return // meow gates kill silently
        val sm = editor.selectionModel
        if (st.selType == SelType.JOIN && sm.hasSelection()) { joinKill(editor, st); return }
        if (sm.hasSelection()) {
            prepareLineSelectionsForKill(editor, st)
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
        Selections.collapse(editor, st) // an edit, not a cancel: history survives
    }

    /** meow-save: copy — with kill-ring-save's mark deactivation: the
     *  selection is cancelled afterwards and the caret stays at point
     *  (past the newline for a forward line selection). */
    private fun save(editor: Editor, st: MeowState, ctx: DataContext?) {
        if (!editor.selectionModel.hasSelection()) return
        prepareLineSelectionsForKill(editor, st)
        Ide.act(editor, ctx, IdeActions.ACTION_EDITOR_COPY)
        for (caret in editor.caretModel.allCarets) caret.removeSelection()
        st.selType = SelType.NONE
        st.selExpand = false
    }

    /** meow-yank: insert the clipboard at every caret, caret lands after it. */
    private fun yank(editor: Editor) {
        if (blockedReadOnly(editor)) return
        val clip = Ide.clipboard() ?: return
        editCarets(editor, "Meow Yank") { caret ->
            val off = caret.offset
            editor.document.insertString(off, clip)
            caret.moveToOffset(off + clip.length)
        }
    }

    /** meow-replace: selection := clipboard; the clipboard stays intact. */
    private fun replace(editor: Editor, st: MeowState) {
        if (!allowModify(editor)) return // meow gates replace silently
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

    /** meow-undo cancels the selection (with its history) BEFORE undoing —
     *  but only when a region is active. */
    private fun undo(editor: Editor, st: MeowState, ctx: DataContext?) {
        if (editor.selectionModel.hasSelection()) Selections.cancel(editor, st)
        Ide.act(editor, ctx, IdeActions.ACTION_UNDO)
    }

    /** meow-undo-in-selection only acts with an active region; region-scoped
     *  undo has no IDE analog, so it is a plain undo (see README). */
    private fun undoInSelection(editor: Editor, st: MeowState, ctx: DataContext?) {
        if (editor.selectionModel.hasSelection()) Ide.act(editor, ctx, IdeActions.ACTION_UNDO)
    }
}
