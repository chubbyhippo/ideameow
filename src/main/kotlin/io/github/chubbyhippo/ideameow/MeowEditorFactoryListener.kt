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

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

/** Attaches meow state to every main file editor; read-only ones start in MOTION. */
class MeowEditorFactoryListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor.editorKind != EditorKind.MAIN_EDITOR) return
        val st = MeowState()
        st.savedBlockCursor = editor.settings.isBlockCursor
        st.mode = if (editor.isViewer || !editor.document.isWritable) MeowMode.MOTION else MeowMode.NORMAL
        editor.putUserData(Meow.KEY, st)
        editor.settings.isBlockCursor = true
        Meow.updateWidgets()
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val st = Meow.state(editor) ?: return
        WhichKey.hide()
        ExpandHints.clear(st)
        Engine.clearGrab(editor, st)
        st.savedBlockCursor?.let { editor.settings.isBlockCursor = it }
        editor.putUserData(Meow.KEY, null)
    }
}
