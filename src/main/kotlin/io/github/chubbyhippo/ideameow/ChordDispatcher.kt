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

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

internal object ChordDispatcher {
    private var swallowNextTyped = false

    val dispatcher =
        object : IdeEventQueue.NonLockedEventDispatcher {
            override fun dispatch(e: AWTEvent) = this@ChordDispatcher.dispatch(e)
        }

    fun reset() {
        swallowNextTyped = false
    }

    internal fun dispatch(event: AWTEvent): Boolean {
        if (event !is KeyEvent || event.isConsumed) return false
        if (event.id == KeyEvent.KEY_TYPED) {
            if (!swallowNextTyped) return false
            swallowNextTyped = false
            return true
        }
        if (event.id != KeyEvent.KEY_PRESSED) return false
        return handlePress(event)
    }

    @Suppress("UnstableApiUsage")
    private fun handlePress(event: KeyEvent): Boolean {
        val binding = bindingFor(event) ?: return false
        if (IdeEventQueue.getInstance().isPopupActive) return false
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        val editor = normalEditorAt(focus) ?: return false
        val state = Meow.state(editor) ?: return false
        swallowNextTyped = true
        WriteIntentReadAction.compute {
            Engine.dispatch(editor, state, binding)
            Meow.updateWidgets()
        }
        return true
    }

    internal fun isChord(event: KeyEvent): Boolean =
        event.keyCode != KeyEvent.VK_UNDEFINED && ChordKey.of(event.keyCode, event.modifiersEx).hasNonShiftModifier()

    internal fun bindingFor(event: KeyEvent): Rc.Binding? {
        if (!isChord(event)) return null
        return Rc.chords()[ChordKey.of(event.keyCode, event.modifiersEx)]
    }

    internal fun claims(
        state: MeowState,
        event: KeyEvent,
    ): Boolean = state.mode == MeowMode.NORMAL && bindingFor(event) != null

    private fun normalEditorAt(focus: Component): Editor? =
        EditorFactory.getInstance().allEditors.firstOrNull {
            Meow.state(it)?.mode == MeowMode.NORMAL && SwingUtilities.isDescendingFrom(focus, it.contentComponent)
        }
}
