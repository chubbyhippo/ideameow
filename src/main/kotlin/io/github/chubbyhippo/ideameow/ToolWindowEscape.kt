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
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

object ToolWindowEscape {
    const val TIMEOUT_MS = 500L
    private const val ESC_CHAR = 27
    private const val TYPED_ESC_SWALLOW_MS = 100L

    private var lastWindow: String? = null
    private var lastAt = 0L
    private var swallowTypedUntil = 0L

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

    internal val dispatcher =
        object : IdeEventQueue.NonLockedEventDispatcher {
            override fun dispatch(e: AWTEvent) = this@ToolWindowEscape.dispatch(e)
        }

    @Suppress("UnstableApiUsage")
    private fun dispatch(event: AWTEvent): Boolean {
        if (event !is KeyEvent || event.isConsumed) return false
        if (event.id == KeyEvent.KEY_TYPED) return swallowTypedEscape(event)
        if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_ESCAPE) return false
        return WriteIntentReadAction.compute { handleEscapePress(event) }
    }

    private fun swallowTypedEscape(event: KeyEvent): Boolean {
        if (event.keyChar.code != ESC_CHAR || event.`when` > swallowTypedUntil) return false
        swallowTypedUntil = 0L
        return true
    }

    private fun handleEscapePress(event: KeyEvent): Boolean {
        if (event.modifiersEx != 0 || IdeEventQueue.getInstance().isPopupActive) {
            reset()
            return false
        }
        val component = event.component ?: return false
        val context = DataManager.getInstance().getDataContext(component)
        if (consumeForMeow(component, context, event)) return true
        val project = CommonDataKeys.PROJECT.getData(context)
        if (project == null) {
            reset()
            return false
        }
        val toolWindows = ToolWindowManager.getInstance(project)
        if (!onEscape(toolWindows.activeToolWindowId, event.`when`)) return false
        swallowTypedUntil = event.`when` + TYPED_ESC_SWALLOW_MS
        toolWindows.activateEditorComponent()
        return true
    }

    private fun consumeForMeow(
        component: Component,
        context: DataContext,
        event: KeyEvent,
    ): Boolean {
        val (editor, state) = focusedMeowEditor(component, context) ?: return false
        if (LookupManager.getActiveLookup(editor) != null) return false
        val consumed =
            try {
                MeowEscape.wants(editor, state) && MeowEscape.consume(editor, state)
            } catch (_: RuntimeException) {
                false
            }
        if (!consumed) return false
        reset()
        swallowTypedUntil = event.`when` + TYPED_ESC_SWALLOW_MS
        return true
    }

    private fun focusedMeowEditor(
        component: Component,
        context: DataContext,
    ): Pair<Editor, MeowState>? {
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        for (editor in EditorFactory.getInstance().allEditors) {
            val state = Meow.state(editor) ?: continue
            val content = editor.contentComponent
            if (isWithin(content, component) || isWithin(content, focus)) return editor to state
        }
        val fromData = CommonDataKeys.EDITOR.getData(context) ?: CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(context)
        return fromData?.let { editor -> Meow.state(editor)?.let { editor to it } }
    }

    private fun isWithin(
        ancestor: Component,
        candidate: Component?,
    ): Boolean = candidate != null && (ancestor === candidate || SwingUtilities.isDescendingFrom(candidate, ancestor))
}

@Service(Service.Level.APP)
internal class ToolWindowEscapeLifecycle : Disposable {
    init {
        IdeEventQueue.getInstance().addDispatcher(ToolWindowEscape.dispatcher, this)
        IdeEventQueue.getInstance().addDispatcher(SpaceLeader.dispatcher, this)
        IdeEventQueue.getInstance().addDispatcher(ChordDispatcher.dispatcher, this)
        IdeEventQueue.getInstance().addDispatcher(AceResizeArrows.dispatcher, this)
    }

    override fun dispose() {
        ToolWindowEscape.reset()
        SpaceLeader.reset()
        ChordDispatcher.reset()
    }
}

internal class ToolWindowEscapeStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().getService(ToolWindowEscapeLifecycle::class.java)
    }
}
