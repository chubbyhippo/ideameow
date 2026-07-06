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
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor

/**
 * The command modules' one bridge to the platform: dispatching IDE actions,
 * running write commands, the clipboard kill-ring, and editor hints. Commands
 * depend on this narrow surface instead of the action system's details.
 */
internal object Ide {

    fun act(editor: Editor, ctx: DataContext?, id: String) {
        val action = ActionManager.getInstance().getAction(id)
            ?: run { hint(editor, "Unknown action: $id"); return }
        val dc = ctx ?: DataManager.getInstance().getDataContext(editor.contentComponent)
        val event = AnActionEvent.createEvent(action, dc, null, "MeowPlugin", ActionUiKind.NONE, null)
        // run the action's update first, exactly like the keymap path does:
        // performing a disabled action trips platform assertions (e.g. undo
        // with an exhausted stack fails UndoManagerImpl's isUndoAvailable)
        ActionUtil.updateAction(action, event)
        if (!event.presentation.isEnabled) return
        ActionUtil.performAction(action, event)
    }

    fun hint(editor: Editor, text: String) {
        // best-effort: hints cannot render in headless (test) mode
        runCatching { HintManager.getInstance().showInformationHint(editor, text) }
    }

    fun runWrite(editor: Editor, name: String, body: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(editor.project, name, "meow", { body() })
    }

    /** The kill-ring is the system clipboard (meow-use-clipboard behavior). */
    fun clipboard(): String? =
        CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
}
