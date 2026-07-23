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

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

internal object MeowEscape {
    fun wants(
        editor: Editor,
        state: MeowState,
    ): Boolean =
        state.avy != null ||
            state.aceWindow != null ||
            state.aceClick != null ||
            state.aceResize != null ||
            state.pending != null ||
            Engine.repeatMap != null ||
            state.mode == MeowMode.INSERT ||
            state.mode == MeowMode.KEYPAD ||
            editor.caretModel.caretCount > 1 ||
            editor.selectionModel.hasSelection()

    fun consume(
        editor: Editor,
        state: MeowState,
    ): Boolean {
        if (state.avy != null) {
            Avy.cancel(editor, state)
            Meow.updateWidgets()
            return true
        }
        if (state.aceWindow != null) {
            AceWindow.cancel(state)
            Meow.updateWidgets()
            return true
        }
        if (state.aceClick != null) {
            AceClick.cancel(state)
            Meow.updateWidgets()
            return true
        }
        if (state.aceResize != null) {
            AceResize.cancel(state)
            Meow.updateWidgets()
            return true
        }
        val hadTransient = state.pending != null || Engine.repeatMap != null
        state.pending = null
        Engine.repeatMap = null
        WhichKey.hide()
        ExpandHints.clear(state)
        return when {
            state.mode == MeowMode.INSERT -> {
                Meow.setMode(editor, state, MeowMode.NORMAL)
                true
            }

            state.mode == MeowMode.KEYPAD -> {
                Keypad.exit(editor, state)
                true
            }

            editor.caretModel.caretCount > 1 -> {
                editor.caretModel.removeSecondaryCarets()
                Meow.updateWidgets()
                true
            }

            editor.selectionModel.hasSelection() -> {
                editor.selectionModel.removeSelection()
                true
            }

            else -> hadTransient
        }
    }
}

class MeowEscapeHandler(
    private val original: EditorActionHandler,
) : EditorActionHandler() {
    override fun doExecute(
        editor: Editor,
        caret: Caret?,
        dataContext: DataContext?,
    ) {
        val state = Meow.state(editor)
        if (state == null || LookupManager.getActiveLookup(editor) != null || !MeowEscape.consume(editor, state)) {
            original.execute(editor, caret, dataContext)
        }
    }
}
