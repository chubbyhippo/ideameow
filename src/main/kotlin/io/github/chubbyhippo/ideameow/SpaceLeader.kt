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
import javax.swing.JComboBox
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

internal object SpaceLeader {
    internal const val TERMINAL_PACKAGE = "com.jediterm"

    private var routed: Routed? = null
    private var swallowNextTyped = false

    internal class Routed(
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

    internal fun routeTo(
        editor: Editor,
        state: MeowState,
        surface: Component,
    ) {
        routed = Routed(editor, state, surface)
    }

    internal fun dispatch(event: AWTEvent): Boolean =
        run {
            if (event !is KeyEvent || event.isConsumed) return@run false
            if (event.id == KeyEvent.KEY_TYPED && swallowNextTyped) {
                swallowNextTyped = false
                return@run true
            }
            val active = routed ?: return@run armOnSpace(event)
            if (active.editor.isDisposed || !wantsKeys(active.state)) {
                reset()
                return@run false
            }
            when (event.id) {
                KeyEvent.KEY_PRESSED -> routePressed(active, event)
                KeyEvent.KEY_TYPED -> routeTyped(active, event)
                else -> false
            }
        }

    @Suppress("UnstableApiUsage")
    private fun armOnSpace(event: KeyEvent): Boolean =
        run {
            if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_SPACE || event.modifiersEx != 0) {
                return@run false
            }
            if (IdeEventQueue.getInstance().isPopupActive) return@run false
            val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return@run false
            if (blocksArming(menuOpen(), focus)) return@run false
            val target = leaderTarget(focus) ?: return@run false
            routed = target
            swallowNextTyped = true
            WriteIntentReadAction.compute { openKeypad(target.editor, target.state) }
            true
        }

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

    private fun wantsKeys(state: MeowState) = state.mode == MeowMode.KEYPAD || state.hasActiveOverlaySession

    internal fun leaderTarget(focus: Component): Routed? =
        run {
            val context = DataManager.getInstance().getDataContext(focus)
            if (activeSpeedSearch(PlatformDataKeys.SPEED_SEARCH_TEXT.getData(context))) return@run null
            val project = CommonDataKeys.PROJECT.getData(context) ?: return@run null
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@run null
            val state = Meow.state(editor) ?: return@run null
            if (state.mode == MeowMode.KEYPAD) return@run null
            Routed(editor, state, focus)
        }

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
        val chord = InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK
        if (event.keyChar == KeyEvent.CHAR_UNDEFINED || event.modifiersEx and chord != 0) return false
        WriteIntentReadAction.compute { Engine.handleChar(active.editor, event.keyChar) }
        if (!wantsKeys(active.state)) reset()
        return true
    }
}

private fun menuOpen(): Boolean = MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()

internal fun activeSpeedSearch(text: String?): Boolean = !text.isNullOrEmpty()

internal fun blocksArming(
    menuOpen: Boolean,
    focus: Component,
): Boolean = !menuOpen && (nativeSpace(focus) || inAnyEditor(focus))

internal fun nativeSpace(focus: Component): Boolean {
    var component: Component? = focus
    while (component != null && component !is Window) {
        val consumesSpace =
            component is JTextComponent ||
                component is JComboBox<*> ||
                component is CheckBoxList<*> ||
                component.javaClass.name.startsWith(SpaceLeader.TERMINAL_PACKAGE)
        if (consumesSpace) return true
        component = component.parent
    }
    return false
}

private fun inAnyEditor(focus: Component): Boolean =
    EditorFactory.getInstance().allEditors.any {
        SwingUtilities.isDescendingFrom(focus, it.contentComponent)
    }

internal fun routeIfOutsideEditor(
    editor: Editor,
    state: MeowState,
    focus: Component?,
) {
    if (focus != null && !inAnyEditor(focus)) SpaceLeader.routeTo(editor, state, focus)
}
