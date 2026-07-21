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
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

internal object SpaceLeader {
    private const val CHECKBOX_TREE = "com.intellij.ui.CheckboxTree"
    private const val TERMINAL_PACKAGE = "com.jediterm"

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
        st: MeowState,
        surface: Component,
    ) {
        routed = Routed(editor, st, surface)
    }

    internal fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.isConsumed) return false
        if (e.id == KeyEvent.KEY_TYPED && swallowNextTyped) {
            swallowNextTyped = false
            return true
        }
        val r = routed
        if (r == null) return armOnSpace(e)
        if (r.editor.isDisposed || !wantsKeys(r.state)) {
            reset()
            return false
        }
        return when (e.id) {
            KeyEvent.KEY_PRESSED -> routePressed(r, e)
            KeyEvent.KEY_TYPED -> routeTyped(r, e)
            else -> false
        }
    }

    @Suppress("UnstableApiUsage")
    private fun armOnSpace(e: KeyEvent): Boolean {
        if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_SPACE || e.modifiersEx != 0) return false
        if (IdeEventQueue.getInstance().isPopupActive) return false
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        if (nativeSpace(focus) || inAnyEditor(focus)) return false
        val target = leaderTarget(focus) ?: return false
        routed = target
        swallowNextTyped = true
        WriteIntentReadAction.compute { openKeypad(target.editor, target.state) }
        return true
    }

    internal fun openKeypad(
        editor: Editor,
        st: MeowState,
    ) {
        if (st.mode == MeowMode.INSERT) {
            Engine.enterKeypad(editor, st)
        } else {
            Engine.handleChar(editor, ' ')
        }
    }

    private fun wantsKeys(st: MeowState) = st.mode == MeowMode.KEYPAD || st.avy != null || st.aceWindow != null || st.aceClick != null

    internal fun nativeSpace(focus: Component): Boolean {
        var c: Component? = focus
        while (c != null && c !is Window) {
            when {
                c is JTextComponent -> return true

                c is AbstractButton -> return true

                c is JComboBox<*> -> return true

                c is CheckBoxList<*> -> return true

                checkboxTreeOrTerminal(c) -> return true
            }
            c = c.parent
        }
        return false
    }

    private fun checkboxTreeOrTerminal(c: Component): Boolean {
        if (c.javaClass.name.startsWith(TERMINAL_PACKAGE)) return true
        var k: Class<*>? = c.javaClass
        while (k != null) {
            if (k.name == CHECKBOX_TREE) return true
            k = k.superclass
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
        val st = Meow.state(editor) ?: return null
        if (st.mode == MeowMode.KEYPAD) return null
        if (SwingUtilities.getWindowAncestor(focus) !== SwingUtilities.getWindowAncestor(editor.component)) return null
        return Routed(editor, st, focus)
    }

    @Suppress("UnstableApiUsage")
    private fun routePressed(
        r: Routed,
        e: KeyEvent,
    ): Boolean {
        if (e.keyCode == KeyEvent.VK_ESCAPE && e.modifiersEx == 0) {
            swallowNextTyped = true
            WriteIntentReadAction.compute { MeowEscape.consume(r.editor, r.state) }
            reset()
            return true
        }
        return e.keyChar != KeyEvent.CHAR_UNDEFINED &&
            (e.modifiersEx == 0 || e.modifiersEx == InputEvent.SHIFT_DOWN_MASK)
    }

    @Suppress("UnstableApiUsage")
    private fun routeTyped(
        r: Routed,
        e: KeyEvent,
    ): Boolean {
        if (e.keyChar == KeyEvent.CHAR_UNDEFINED) return false
        val chord = InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK
        if (e.modifiersEx and chord != 0) return false
        WriteIntentReadAction.compute { Engine.handleChar(r.editor, e.keyChar) }
        if (!wantsKeys(r.state)) reset()
        return true
    }
}
