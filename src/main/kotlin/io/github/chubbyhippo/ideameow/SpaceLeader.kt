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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.CheckBoxList
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

internal object SpaceLeader {
    private const val CHECKBOX_TREE = "com.intellij.ui.CheckboxTree"
    private const val CHANGES_TREE = "com.intellij.openapi.vcs.changes.ui.ChangesTree"
    private const val TERMINAL_PACKAGE = "com.jediterm"
    internal val SPACE_TREES = listOf(CHECKBOX_TREE, CHANGES_TREE)

    private var routed: Routed? = null
    private var swallowNextTyped = false

    private class Routed(
        val editor: Editor,
        val state: MeowState,
        val surface: Component,
    )

    internal val dispatcher =
        object : IdeEventQueue.NonLockedEventDispatcher {
            override fun dispatch(e: AWTEvent) = this@SpaceLeader.dispatch(e)
        }

    fun surfaceFor(editor: Editor): Component? = routed?.takeIf { it.editor === editor }?.surface

    fun reset() {
        routed = null
        swallowNextTyped = false
    }

    internal fun setForTest(
        editor: Editor,
        state: MeowState,
        surface: Component,
    ) {
        routed = Routed(editor, state, surface)
    }

    internal fun dispatch(event: AWTEvent): Boolean {
        if (event !is KeyEvent || event.isConsumed) return false
        if (event.id == KeyEvent.KEY_TYPED && swallowNextTyped) {
            swallowNextTyped = false
            return true
        }
        val active = routed
        if (active == null) return armOnSpace(event)
        if (active.editor.isDisposed || !wantsKeys(active.state)) {
            reset()
            return false
        }
        return when (event.id) {
            KeyEvent.KEY_PRESSED -> routePressed(active, event)
            KeyEvent.KEY_TYPED -> routeTyped(active, event)
            else -> false
        }
    }

    @Suppress("UnstableApiUsage")
    private fun armOnSpace(event: KeyEvent): Boolean {
        if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_SPACE || event.modifiersEx != 0) {
            return false
        }
        if (IdeEventQueue.getInstance().isPopupActive) return false
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        if (blocksArming(menuOpen(), focus)) return false
        val target = leaderTarget(focus) ?: return false
        routed = target
        swallowNextTyped = true
        WriteIntentReadAction.compute { openKeypad(target.editor, target.state) }
        return true
    }

    private fun menuOpen(): Boolean = MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()

    internal fun blocksArming(
        menuOpen: Boolean,
        focus: Component,
    ): Boolean = !menuOpen && (nativeSpace(focus) || inAnyEditor(focus))

    internal fun openKeypad(
        editor: Editor,
        state: MeowState,
    ) {
        if (state.mode == MeowMode.INSERT) {
            Engine.enterKeypad(editor, state)
        } else {
            Engine.handleChar(editor, ' ')
        }
    }

    private fun wantsKeys(state: MeowState) =
        state.mode == MeowMode.KEYPAD ||
            state.avy != null ||
            state.aceWindow != null ||
            state.aceClick != null ||
            state.aceResize != null

    internal fun nativeSpace(focus: Component): Boolean {
        var component: Component? = focus
        while (component != null && component !is Window) {
            when {
                component is JTextComponent -> return true

                component is AbstractButton -> return true

                component is JComboBox<*> -> return true

                component is CheckBoxList<*> -> return true

                nativeSpaceTreeOrTerminal(component) -> return true
            }
            component = component.parent
        }
        return false
    }

    private fun nativeSpaceTreeOrTerminal(component: Component): Boolean =
        component.javaClass.name.startsWith(TERMINAL_PACKAGE) || treeConsumesSpace(component.javaClass)

    internal fun treeConsumesSpace(start: Class<*>): Boolean {
        var current: Class<*>? = start
        while (current != null) {
            if (current.name in SPACE_TREES) return true
            current = current.superclass
        }
        return false
    }

    private fun inAnyEditor(focus: Component): Boolean =
        EditorFactory.getInstance().allEditors.any {
            SwingUtilities.isDescendingFrom(focus, it.contentComponent)
        }

    private fun leaderTarget(focus: Component): Routed? {
        val context = DataManager.getInstance().getDataContext(focus)
        if (PlatformDataKeys.SPEED_SEARCH_TEXT.getData(context) != null) return null
        val project = CommonDataKeys.PROJECT.getData(context) ?: return null
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val state = Meow.state(editor) ?: return null
        if (state.mode == MeowMode.KEYPAD) return null
        val editorWindow = SwingUtilities.getWindowAncestor(editor.component)
        if (!windowChainContains(SwingUtilities.getWindowAncestor(focus), editorWindow)) return null
        return Routed(editor, state, focus)
    }

    private fun windowChainContains(
        from: Window?,
        target: Window?,
    ): Boolean = generateSequence(from) { it.owner }.any { it === target }

    @Suppress("UnstableApiUsage")
    private fun routePressed(
        active: Routed,
        event: KeyEvent,
    ): Boolean {
        if (event.keyCode == KeyEvent.VK_ESCAPE && event.modifiersEx == 0) {
            swallowNextTyped = true
            WriteIntentReadAction.compute { MeowEscape.consume(active.editor, active.state) }
            reset()
            return true
        }
        return event.keyChar != KeyEvent.CHAR_UNDEFINED &&
            (event.modifiersEx == 0 || event.modifiersEx == InputEvent.SHIFT_DOWN_MASK)
    }

    @Suppress("UnstableApiUsage")
    private fun routeTyped(
        active: Routed,
        event: KeyEvent,
    ): Boolean {
        if (event.keyChar == KeyEvent.CHAR_UNDEFINED) return false
        val chord = InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK
        if (event.modifiersEx and chord != 0) return false
        WriteIntentReadAction.compute { Engine.handleChar(active.editor, event.keyChar) }
        if (!wantsKeys(active.state)) reset()
        return true
    }
}
