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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

enum class RevealAt {
    CENTER,
    TOP,
    BOTTOM,
}

internal object View {
    const val RECENTER_COMMAND = "recenter-top-bottom"
    const val SCROLL_UP_COMMAND = "scroll-up-command"
    const val SCROLL_DOWN_COMMAND = "scroll-down-command"

    private const val SCREEN_CONTEXT_LINES = 2

    val RECENTER_POSITIONS = listOf(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM)

    fun recenterPosition(phase: Int): RevealAt = RECENTER_POSITIONS[Math.floorMod(phase, RECENTER_POSITIONS.size)]

    fun nextRecenterPhase(
        previousCommand: String?,
        phase: Int,
    ): Int = if (previousCommand == RECENTER_COMMAND) phase + 1 else 0

    fun pageLineCount(editor: Editor): Int {
        val lineHeight = editor.lineHeight.coerceAtLeast(1)
        val visibleLines = editor.scrollingModel.visibleArea.height / lineHeight
        return (visibleLines - SCREEN_CONTEXT_LINES).coerceAtLeast(1)
    }

    val commands: Map<String, MeowCommand> =
        buildMap {
            put(
                RECENTER_COMMAND,
                MeowCommand { editor, state ->
                    state.recenterPhase = nextRecenterPhase(state.lastCommand, state.recenterPhase)
                    state.lastCommand = RECENTER_COMMAND
                    revealCaret(editor, recenterPosition(state.recenterPhase))
                },
            )
            put(
                SCROLL_UP_COMMAND,
                MeowCommand { editor, state ->
                    lineOrExpand(editor, state, pageLineCount(editor) * state.takeCount(1))
                    state.lastCommand = SCROLL_UP_COMMAND
                },
            )
            put(
                SCROLL_DOWN_COMMAND,
                MeowCommand { editor, state ->
                    lineOrExpand(editor, state, -pageLineCount(editor) * state.takeCount(1))
                    state.lastCommand = SCROLL_DOWN_COMMAND
                },
            )
        }

    private fun revealCaret(
        editor: Editor,
        at: RevealAt,
    ) {
        val scrolling = editor.scrollingModel
        if (at == RevealAt.CENTER) {
            scrolling.scrollToCaret(ScrollType.CENTER)
            return
        }
        val caretY = editor.visualPositionToXY(editor.caretModel.visualPosition).y
        val visibleHeight = scrolling.visibleArea.height
        val top = if (at == RevealAt.TOP) caretY else caretY + editor.lineHeight - visibleHeight
        scrolling.scrollVertically(maxOf(0, top))
    }
}
