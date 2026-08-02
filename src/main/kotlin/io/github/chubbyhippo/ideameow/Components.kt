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

import java.awt.Component
import java.awt.Container

internal fun visibleComponents(
    root: Component,
    descendInto: (Component) -> Boolean = { true },
): List<Component> {
    val found = mutableListOf<Component>()
    val queue = ArrayDeque<Component>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!component.isVisible) continue
        found.add(component)
        if (component is Container && descendInto(component)) queue += component.components
    }
    return found
}
