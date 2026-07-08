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

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.AWTEvent
import java.awt.event.KeyEvent

/**
 * Double-ESC in a tool window returns focus to the editor.
 *
 * A single ESC already does that in most tool windows — the platform's own
 * escape handling — but the terminal and chat-style windows consume every
 * ESC themselves (a shell or a TUI needs the key), so the platform never
 * sees it there. This watches raw ESC presses ahead of all component and
 * shortcut processing (IdeEventQueue custom dispatchers run before the
 * focused component gets the event): the first plain ESC in a tool window
 * passes through untouched, a second one in the SAME tool window within
 * [TIMEOUT_MS] is consumed and focus jumps to the editor through the
 * platform's own [ToolWindowManager.activateEditorComponent].
 *
 * Guards: only modifier-less presses count; anything while a popup is up
 * never counts (ESC must keep closing popups); focus outside a tool window
 * breaks the pair. After a jump, the KEY_TYPED half of the same keystroke
 * (char 27) is swallowed too, so a terminal's TTY never receives a stray
 * escape byte.
 */
object ToolWindowEscape {
    /** Two presses at most this many ms apart count as a double-press. */
    const val TIMEOUT_MS = 500L

    private var lastWindow: String? = null
    private var lastAt = 0L
    private var swallowTypedUntil = 0L

    /** The double-press decision, kept pure for the specs: every plain ESC
     *  press reports the active tool window id (null = focus is not in a
     *  tool window) and its event time; true = second press of a pair, the
     *  caller jumps. A miss (different window, too slow, null) re-arms with
     *  the current press. */
    fun onEscape(
        windowId: String?,
        at: Long,
    ): Boolean {
        val doubled = windowId != null && windowId == lastWindow && at - lastAt <= TIMEOUT_MS
        if (doubled) {
            reset()
            return true
        }
        lastWindow = windowId
        lastAt = at
        return false
    }

    fun reset() {
        lastWindow = null
        lastAt = 0L
    }

    // -------------------------------------------------------------- wiring

    internal val dispatcher = IdeEventQueue.EventDispatcher { e -> dispatch(e) }

    private fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.isConsumed) return false
        if (e.id == KeyEvent.KEY_TYPED) {
            if (e.keyChar.code == 27 && e.`when` <= swallowTypedUntil) {
                swallowTypedUntil = 0L
                return true
            }
            return false
        }
        if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_ESCAPE) return false
        if (e.modifiersEx != 0 || IdeEventQueue.getInstance().isPopupActive) {
            reset()
            return false
        }
        val component = e.component ?: return false
        val context = DataManager.getInstance().getDataContext(component)
        val project =
            CommonDataKeys.PROJECT.getData(context) ?: run {
                reset()
                return false
            }
        val toolWindows = ToolWindowManager.getInstance(project)
        if (!onEscape(toolWindows.activeToolWindowId, e.`when`)) return false
        swallowTypedUntil = e.`when` + 100
        toolWindows.activateEditorComponent()
        return true
    }
}

/** App-level lifetime anchor, the TreeMeowLifecycle pattern: created on
 *  first project open; the dispatcher is parented to this service, so
 *  dynamic plugin unload removes the JVM-global hook with it. */
@Service(Service.Level.APP)
internal class ToolWindowEscapeLifecycle : Disposable {
    init {
        IdeEventQueue.getInstance().addDispatcher(ToolWindowEscape.dispatcher, this)
    }

    override fun dispose() = ToolWindowEscape.reset()
}

/** Installs the double-ESC hook once any project opens. */
internal class ToolWindowEscapeStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().getService(ToolWindowEscapeLifecycle::class.java)
    }
}
