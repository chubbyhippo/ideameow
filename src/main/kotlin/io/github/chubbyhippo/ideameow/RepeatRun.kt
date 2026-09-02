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

internal object RepeatRun {
    var map: Map<Char, Rc.Binding>? = null

    fun consume(key: Char): Rc.Binding? {
        val binding = map?.get(key)
        if (binding == null) map = null
        return binding
    }

    fun armAfter(
        editor: Editor,
        binding: Rc.Binding,
    ) {
        val repeatKeymap = RcLookups.repeatMapFor(binding) ?: return
        if (map == null) {
            Ide.hint(editor, "Repeat with ${repeatKeymap.keys.joinToString(", ")}")
        }
        map = repeatKeymap
    }
}
