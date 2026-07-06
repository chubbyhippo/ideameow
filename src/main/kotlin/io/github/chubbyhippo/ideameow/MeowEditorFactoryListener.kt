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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

/**
 * Attaches meow state to every main file editor and every diff editor
 * (read-only ones — including a diff's revision side, which the diff
 * framework creates as a viewer — start in MOTION), and to dialog /
 * tool-window fields (EditorKind.UNTYPED — the commit message box, ...) when
 * they are multi-line and writable, mirroring IdeaVim's default
 * `ideavimsupport=dialog`. EditorTextField switches on one-line mode only
 * after the editor is created, so the UNTYPED decision is deferred until the
 * current EDT event finishes; ModalityState.any() keeps it working for
 * editors born inside modal dialogs.
 */
class MeowEditorFactoryListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        when (editor.editorKind) {
            EditorKind.MAIN_EDITOR, EditorKind.DIFF -> attach(editor)
            EditorKind.UNTYPED -> ApplicationManager.getApplication().invokeLater(
                { if (shouldAttachUntyped(editor)) attach(editor) },
                ModalityState.any(),
            )
            else -> Unit
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val st = Meow.state(editor) ?: return
        WhichKey.hide()
        ExpandHints.clear(st)
        Grab.clear(editor, st)
        st.savedBlockCursor?.let { editor.settings.isBlockCursor = it }
        editor.putUserData(Meow.KEY, null)
    }

    private fun attach(editor: Editor) {
        val st = MeowState()
        st.savedBlockCursor = editor.settings.isBlockCursor
        st.mode = if (editor.isViewer || !editor.document.isWritable) MeowMode.MOTION else MeowMode.NORMAL
        editor.putUserData(Meow.KEY, st)
        editor.settings.isBlockCursor = true
        Meow.updateWidgets()
    }

    companion object {
        /** One-line fields (Evaluate Expression, ...), viewers, and read-only
         * documents keep native editing; the commit message box qualifies. */
        fun shouldAttachUntyped(editor: Editor): Boolean =
            !editor.isDisposed &&
                !editor.isOneLineMode &&
                !editor.isViewer &&
                editor.document.isWritable &&
                Meow.state(editor) == null
    }
}
