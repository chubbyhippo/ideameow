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

    internal fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.isConsumed) return false
        if (e.id == KeyEvent.KEY_TYPED) {
            if (!swallowNextTyped) return false
            swallowNextTyped = false
            return true
        }
        if (e.id != KeyEvent.KEY_PRESSED) return false
        return handlePress(e)
    }

    @Suppress("UnstableApiUsage")
    private fun handlePress(e: KeyEvent): Boolean {
        val binding = bindingFor(e) ?: return false
        if (IdeEventQueue.getInstance().isPopupActive) return false
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        val editor = normalEditorAt(focus) ?: return false
        val st = Meow.state(editor) ?: return false
        swallowNextTyped = true
        WriteIntentReadAction.compute {
            Engine.dispatch(editor, st, binding)
            Meow.updateWidgets()
        }
        return true
    }

    internal fun isChord(e: KeyEvent): Boolean =
        e.keyCode != KeyEvent.VK_UNDEFINED && ChordKey.of(e.keyCode, e.modifiersEx).hasNonShiftModifier()

    internal fun bindingFor(e: KeyEvent): Rc.Binding? {
        if (!isChord(e)) return null
        return Rc.chords()[ChordKey.of(e.keyCode, e.modifiersEx)]
    }

    internal fun claims(
        st: MeowState,
        e: KeyEvent,
    ): Boolean = st.mode == MeowMode.NORMAL && bindingFor(e) != null

    private fun normalEditorAt(focus: Component): Editor? =
        EditorFactory.getInstance().allEditors.firstOrNull {
            Meow.state(it)?.mode == MeowMode.NORMAL && SwingUtilities.isDescendingFrom(focus, it.contentComponent)
        }
}
