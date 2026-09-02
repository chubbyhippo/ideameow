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

internal object DotRepeat {
    fun recordUnitKey(
        state: MeowState,
        key: Char,
        command: String?,
        pending: Pending?,
    ) {
        if (state.replaying || command == "repeat") return
        if (pending == null && state.pendingCount == 0 && !state.negative) state.unitKeys.clear()
        state.unitKeys.add(key)
    }

    fun recordLastKeys(
        state: MeowState,
        command: String?,
    ) {
        if (!state.replaying && command != "repeat" && !isPrefixCommand(state, command)) {
            state.lastKeys = state.unitKeys.toList()
        }
    }

    fun repeatLast(
        editor: Editor,
        state: MeowState,
    ) {
        val keys = state.lastKeys
        if (keys.isEmpty()) return
        state.replaying = true
        try {
            for (key in keys) Engine.handleChar(editor, key)
        } finally {
            state.replaying = false
        }
    }

    private fun isPrefixCommand(
        state: MeowState,
        command: String?,
    ): Boolean =
        state.pending != null ||
            (state.pendingCount != 0 && command?.startsWith("meow-expand-") == true) ||
            (state.negative && command == "meow-negative-argument") ||
            command == "meow-keypad"
}
