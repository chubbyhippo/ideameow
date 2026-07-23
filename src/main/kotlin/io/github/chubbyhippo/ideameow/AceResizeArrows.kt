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
import com.intellij.openapi.editor.EditorFactory
import java.awt.AWTEvent
import java.awt.event.KeyEvent

internal object AceResizeArrows {
    val dispatcher =
        object : IdeEventQueue.NonLockedEventDispatcher {
            override fun dispatch(e: AWTEvent) = this@AceResizeArrows.dispatch(e)
        }

    internal fun arrowDir(e: AWTEvent): AceResize.Dir? {
        if (e !is KeyEvent || e.id != KeyEvent.KEY_PRESSED || e.modifiersEx != 0) return null
        return when (e.keyCode) {
            KeyEvent.VK_LEFT -> AceResize.Dir.LEFT
            KeyEvent.VK_RIGHT -> AceResize.Dir.RIGHT
            KeyEvent.VK_DOWN -> AceResize.Dir.DOWN
            KeyEvent.VK_UP -> AceResize.Dir.UP
            else -> null
        }
    }

    @Suppress("UnstableApiUsage")
    private fun dispatch(e: AWTEvent): Boolean {
        val dir = arrowDir(e) ?: return false
        val state = holdState()
        if (state != null) WriteIntentReadAction.compute { AceResize.holdArrow(state, dir) }
        return state != null
    }

    private fun holdState(): MeowState? =
        EditorFactory.getInstance().allEditors.firstNotNullOfOrNull { editor ->
            Meow.state(editor)?.takeIf { it.aceResize?.phase == AceResize.Phase.HOLD }
        }
}
