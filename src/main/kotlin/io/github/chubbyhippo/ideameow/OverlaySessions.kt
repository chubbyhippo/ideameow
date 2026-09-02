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

internal object OverlaySessions {
    fun routeKey(
        editor: Editor,
        state: MeowState,
        key: Char,
    ): Boolean {
        state.lastCommand =
            when {
                state.mode == MeowMode.KEYPAD -> {
                    Keypad.key(editor, state, key)
                    "keypad"
                }

                state.avy != null -> {
                    Avy.key(editor, state, key)
                    "avy"
                }

                state.aceWindow != null -> {
                    AceWindow.key(editor, state, key)
                    "ace-window"
                }

                state.aceClick != null -> {
                    AceClick.key(editor, state, key)
                    "ace-click"
                }

                state.aceResize != null -> {
                    AceResize.key(editor, state, key)
                    "ace-resize"
                }

                else -> {
                    return false
                }
            }
        Meow.updateWidgets()
        return true
    }
}
