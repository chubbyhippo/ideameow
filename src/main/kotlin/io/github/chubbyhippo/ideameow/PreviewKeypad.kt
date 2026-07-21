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
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal object PreviewKeypad {
    private var routed: Routed? = null
    private var swallowNextTyped = false

    private class Routed(
        val editor: Editor,
        val state: MeowState,
        val surface: JComponent,
    )

    internal val dispatcher =
        object : IdeEventQueue.NonLockedEventDispatcher {
            override fun dispatch(e: AWTEvent) = this@PreviewKeypad.dispatch(e)
        }

    fun surfaceFor(editor: Editor): JComponent? = routed?.takeIf { it.editor === editor }?.surface

    fun reset() {
        routed = null
        swallowNextTyped = false
    }

    internal fun setForTest(
        editor: Editor,
        st: MeowState,
        surface: JComponent,
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
        if (r.editor.isDisposed || r.state.mode != MeowMode.KEYPAD) {
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
        val target = previewTarget(focus) ?: return false
        if (target.state.mode != MeowMode.NORMAL && target.state.mode != MeowMode.MOTION) return false
        routed = target
        swallowNextTyped = true
        WriteIntentReadAction.compute { Engine.handleChar(target.editor, ' ') }
        return true
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
        if (r.state.mode != MeowMode.KEYPAD) reset()
        return true
    }

    private fun previewTarget(focus: Component): Routed? {
        val context = DataManager.getInstance().getDataContext(focus)
        val project = CommonDataKeys.PROJECT.getData(context) ?: return null
        for (fileEditor in FileEditorManager.getInstance(project).allEditors) {
            val composite = fileEditor as? TextEditorWithPreview ?: continue
            val previewComponent = composite.previewEditor.component
            if (!SwingUtilities.isDescendingFrom(focus, previewComponent)) continue
            val editor = composite.textEditor.editor
            val st = Meow.state(editor) ?: continue
            return Routed(editor, st, previewComponent)
        }
        return null
    }
}
