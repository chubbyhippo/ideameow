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

/**
 * EditorEscape override: INSERT -> NORMAL, cancels KEYPAD and pending keys,
 * collapses beacon carets; delegates when a completion popup is up or the
 * editor has no meow state.
 */
class MeowEscapeHandler(private val original: EditorActionHandler) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val st = Meow.state(editor)
        if (st == null || LookupManager.getActiveLookup(editor) != null) {
            original.execute(editor, caret, dataContext)
            return
        }
        st.pending = null
        WhichKey.hide()
        ExpandHints.clear(st)
        when {
            st.mode == MeowMode.INSERT -> Meow.setMode(editor, st, MeowMode.NORMAL)
            st.mode == MeowMode.KEYPAD -> Meow.setMode(editor, st, MeowMode.NORMAL)
            editor.caretModel.caretCount > 1 -> {
                editor.caretModel.removeSecondaryCarets()
                Meow.updateWidgets()
            }
            else -> original.execute(editor, caret, dataContext)
        }
    }
}
