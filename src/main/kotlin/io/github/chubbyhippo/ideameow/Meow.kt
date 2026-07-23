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

object Meow {
    val KEY: Key<MeowState> = Key.create("meow.state")

    fun state(editor: Editor): MeowState? = editor.getUserData(KEY)

    fun setMode(
        editor: Editor,
        state: MeowState,
        mode: MeowMode,
    ) {
        state.mode = mode
        if (mode != MeowMode.KEYPAD) state.keypad.setLength(0)
        editor.settings.isBlockCursor = mode != MeowMode.INSERT
        updateWidgets()
    }

    fun updateWidgets() {
        for (p in ProjectManager.getInstance().openProjects) {
            WindowManager.getInstance().getStatusBar(p)?.updateWidget(MeowWidgetFactory.ID)
        }
    }

    fun statusText(project: Project): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return ""
        val state = state(editor) ?: return ""
        val beacon = editor.caretModel.caretCount > 1
        val repeat = Engine.repeatMap
        return when {
            state.mode == MeowMode.KEYPAD -> "MEOW KEYPAD  SPC ${state.keypad.chunked(1).joinToString(" ")}"

            beacon && state.mode == MeowMode.INSERT -> "MEOW BEACON-INSERT"

            beacon -> "MEOW BEACON"

            repeat != null -> "MEOW ${state.mode} [repeat ${repeat.keys.joinToString(" ")}]"

            else -> "MEOW ${state.mode}"
        }
    }
}
