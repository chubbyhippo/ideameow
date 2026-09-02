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

import com.intellij.ui.CheckBoxList
import java.awt.Point
import javax.swing.JLayeredPane
import javax.swing.JList

internal fun selectListCell(
    list: JList<*>,
    index: Int,
) {
    list.selectedIndex = index
    list.ensureIndexIsVisible(index)
}

internal fun clickListCheckbox(
    list: JList<*>,
    index: Int,
) {
    if (list !is CheckBoxList<*>) return
    val checkbox = list.model.getElementAt(index)
    if (checkbox == null || !checkbox.isEnabled) return
    val value = !checkbox.isSelected
    checkbox.isSelected = value
    list.repaint()
    notifyCheckBoxListListener(list, index, value)
}

private fun notifyCheckBoxListListener(
    list: CheckBoxList<*>,
    index: Int,
    value: Boolean,
) {
    runCatching {
        val field = CheckBoxList::class.java.getDeclaredField("checkBoxListListener")
        field.isAccessible = true
        val listener = field.get(list) ?: return
        val method =
            listener.javaClass.getMethod(
                "checkBoxSelectionChanged",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        method.invoke(listener, index, value)
    }
}

internal fun listCells(
    list: JList<*>,
    layer: JLayeredPane,
): List<AceClick.Target> {
    val visible = list.visibleRect
    if (visible.width <= 0 || visible.height <= 0 || list.model.size == 0) return emptyList()
    val first = list.locationToIndex(Point(visible.x, visible.y)).coerceAtLeast(0)
    val last = list.locationToIndex(Point(visible.x, visible.y + visible.height - 1))
    val checkboxList = list is CheckBoxList<*>
    val out = mutableListOf<AceClick.Target>()
    for (index in first..last) {
        val bounds = list.getCellBounds(index, index) ?: continue
        val clip = bounds.intersection(visible)
        if (!clip.isEmpty) {
            out.add(
                rowTarget(list, clip, layer) {
                    selectListCell(list, index)
                    if (checkboxList) clickListCheckbox(list, index)
                },
            )
        }
    }
    return out
}
