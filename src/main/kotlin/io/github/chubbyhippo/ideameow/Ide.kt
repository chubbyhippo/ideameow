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

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import javax.swing.JComponent

internal object Ide {
    fun act(
        editor: Editor,
        @Suppress("UNUSED_PARAMETER") ctx: DataContext?,
        id: String,
    ) {
        val action =
            ActionManager.getInstance().getAction(id)
                ?: run {
                    hint(editor, "Unknown action: $id")
                    return
                }
        ActionManagerEx.getInstanceEx().tryToExecute(
            action,
            null,
            editor.contentComponent,
            "MeowPlugin",
            true,
        )
    }

    fun actOn(
        component: JComponent,
        id: String,
    ) {
        val action = ActionManager.getInstance().getAction(id) ?: return
        ActionManagerEx.getInstanceEx().tryToExecute(action, null, component, "MeowPlugin", true)
    }

    fun hint(
        editor: Editor,
        text: String,
    ) {
        runCatching { HintManager.getInstance().showInformationHint(editor, text) }
    }

    fun visibleLines(editor: Editor): Pair<Int, Int> {
        val doc = editor.document
        if (doc.textLength == 0) return 0 to 0
        val last = (doc.lineCount - 1).coerceAtLeast(0)
        val area = editor.scrollingModel.visibleArea
        if (area.height <= 0) return 0 to last
        val top = editor.xyToLogicalPosition(Point(0, area.y)).line.coerceIn(0, last)
        val bottom = editor.xyToLogicalPosition(Point(0, area.y + area.height)).line.coerceIn(0, last)
        return top to bottom
    }

    fun runWrite(
        editor: Editor,
        name: String,
        body: () -> Unit,
    ) {
        WriteCommandAction.runWriteCommandAction(editor.project, name, "meow", { body() })
    }

    fun clipboard(): String? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
}
