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

class MeowEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        when (editor.editorKind) {
            EditorKind.MAIN_EDITOR, EditorKind.DIFF -> {
                attach(editor)
            }

            EditorKind.UNTYPED -> {
                ApplicationManager.getApplication().invokeLater(
                    { if (shouldAttachUntyped(editor)) attach(editor) },
                    ModalityState.any(),
                )
            }

            else -> {}
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val st = Meow.state(editor) ?: return
        Avy.cancel(editor, st)
        WhichKey.hide()
        ExpandHints.clear(st)
        Grab.clear(editor, st)
        st.savedBlockCursor?.let { editor.settings.isBlockCursor = it }
        editor.putUserData(Meow.KEY, null)
    }

    private fun attach(editor: Editor) {
        val st = MeowState()
        st.savedBlockCursor = editor.settings.isBlockCursor
        editor.putUserData(Meow.KEY, st)
        editor.settings.isBlockCursor = true
        Meow.updateWidgets()
    }

    companion object {
        fun shouldAttachUntyped(editor: Editor): Boolean =
            !editor.isDisposed &&
                !editor.isOneLineMode &&
                !editor.isViewer &&
                editor.document.isWritable &&
                Meow.state(editor) == null
    }
}
