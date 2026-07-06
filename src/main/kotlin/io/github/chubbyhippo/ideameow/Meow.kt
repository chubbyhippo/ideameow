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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.WindowManager

/**
 * Per-editor state access and the mode lifecycle: which editor has meow, what
 * mode it is in, and what the status-bar widget should say about it.
 */
object Meow {
    val KEY: Key<MeowState> = Key.create("meow.state")

    fun state(editor: Editor): MeowState? = editor.getUserData(KEY)

    fun setMode(editor: Editor, st: MeowState, mode: MeowMode) {
        st.mode = mode
        if (mode != MeowMode.KEYPAD) st.keypad.setLength(0)
        editor.settings.isBlockCursor = mode != MeowMode.INSERT
        updateWidgets()
    }

    fun updateWidgets() {
        for (p in ProjectManager.getInstance().openProjects) {
            WindowManager.getInstance().getStatusBar(p)?.updateWidget(MeowWidgetFactory.ID)
        }
    }

    /** Status text for the widget, derived from the focused editor. */
    fun statusText(project: Project): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return ""
        val st = state(editor) ?: return ""
        val beacon = editor.caretModel.caretCount > 1
        return when {
            st.mode == MeowMode.KEYPAD -> "MEOW KEYPAD  SPC ${st.keypad.toString().toCharArray().joinToString(" ")}"
            beacon && st.mode == MeowMode.INSERT -> "MEOW BEACON-INSERT"
            beacon -> "MEOW BEACON"
            else -> "MEOW ${st.mode}"
        }
    }
}
