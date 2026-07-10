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

/**
 * The command modules' one bridge to the platform: dispatching IDE actions,
 * running write commands, the clipboard kill-ring, and editor hints. Commands
 * depend on this narrow surface instead of the action system's details.
 */
internal object Ide {
    /** [ctx] is deliberately unread: tryToExecute derives its own context
     *  from the content component, but the DataContext stays in the command
     *  ABI (MeowTypedHandler receives a real one) so a future bridge that
     *  needs focus-sensitive dispatch doesn't have to re-thread it through
     *  every command family. */
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
        // the platform's own programmatic-invocation path: update (properly
        // threaded), the enabled gate, then perform — identical to a keymap
        // press. Hand-rolling update+perform here broke in-IDE dispatch while
        // passing headless (and skipping update trips assertions, e.g. undo
        // past an exhausted stack fails UndoManagerImpl's isUndoAvailable).
        ActionManagerEx.getInstanceEx().tryToExecute(
            action,
            null,
            editor.contentComponent,
            "MeowPlugin",
            true,
        )
    }

    /** [act] for editor-less surfaces (tool-window trees): same invocation
     *  path, the component as context. No hint surface here — an unknown id
     *  is inert, like every other undefined key on a tree. */
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
        // best-effort: hints cannot render in headless (test) mode
        runCatching { HintManager.getInstance().showInformationHint(editor, text) }
    }

    /** The visible logical line range (first to last) — Emacs' window-start/
     *  window-end, shared by avy's candidate scan and the `w` window thing.
     *  A zero-height viewport (headless specs) or an empty document falls
     *  back to the whole buffer. */
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

    /** The kill-ring is the system clipboard (meow-use-clipboard behavior). */
    fun clipboard(): String? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
}
