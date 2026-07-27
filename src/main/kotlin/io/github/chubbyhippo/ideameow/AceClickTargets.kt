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
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JTree
import javax.swing.SwingUtilities

internal fun targetOf(
    component: Component,
    layer: JLayeredPane,
): AceClick.Target? {
    if (component !is JComponent || !component.isShowing) return null
    val click = AceClick.clicker(component)
    val visible = component.visibleRect
    return if (click == null || visible.width <= 0 || visible.height <= 0) {
        null
    } else {
        val screen = Rectangle(visible)
        val corner = screen.location
        SwingUtilities.convertPointToScreen(corner, component)
        screen.location = corner
        val point = centerOf(visible)
        AceClick.Target(
            SwingUtilities.convertRectangle(component, visible, layer),
            component,
            layer,
            screen,
            click = click,
            rightClick = { popupClick(component, point) },
        )
    }
}

internal fun rowTarget(
    component: JComponent,
    rectInComponent: Rectangle,
    layer: JLayeredPane,
    click: () -> Unit,
): AceClick.Target {
    val screen = Rectangle(rectInComponent)
    val corner = screen.location
    SwingUtilities.convertPointToScreen(corner, component)
    screen.location = corner
    val point = centerOf(rectInComponent)
    return AceClick.Target(
        SwingUtilities.convertRectangle(component, rectInComponent, layer),
        component,
        layer,
        screen,
        click = click,
        rightClick = {
            click()
            popupClick(component, point)
        },
    )
}

internal fun selectTreeRow(
    tree: JTree,
    row: Int,
) {
    tree.setSelectionRow(row)
    tree.scrollRowToVisible(row)
}

internal fun selectListCell(
    list: JList<*>,
    index: Int,
) {
    list.selectedIndex = index
    list.ensureIndexIsVisible(index)
}

private fun centerOf(rect: Rectangle) = Point(rect.x + rect.width / 2, rect.y + rect.height / 2)

private fun popupClick(
    component: JComponent,
    point: Point,
) {
    val time = System.currentTimeMillis()
    for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
        component.dispatchEvent(MouseEvent(component, id, time, 0, point.x, point.y, 1, true, MouseEvent.BUTTON3))
    }
}

internal fun treeRows(
    tree: JTree,
    layer: JLayeredPane,
): List<AceClick.Target> {
    val visible = tree.visibleRect
    if (visible.width <= 0 || visible.height <= 0 || tree.rowCount == 0) return emptyList()
    val first = tree.getClosestRowForLocation(visible.x, visible.y).coerceAtLeast(0)
    val last = tree.getClosestRowForLocation(visible.x, visible.y + visible.height - 1)
    val out = mutableListOf<AceClick.Target>()
    for (row in first..last) {
        val bounds = tree.getRowBounds(row) ?: continue
        val clip = bounds.intersection(visible)
        if (!clip.isEmpty) {
            out.add(rowTarget(tree, clip, layer) { selectTreeRow(tree, row) })
        }
    }
    return out
}

internal fun listCells(
    list: JList<*>,
    layer: JLayeredPane,
): List<AceClick.Target> {
    val visible = list.visibleRect
    if (visible.width <= 0 || visible.height <= 0 || list.model.size == 0) return emptyList()
    val first = list.locationToIndex(Point(visible.x, visible.y)).coerceAtLeast(0)
    val last = list.locationToIndex(Point(visible.x, visible.y + visible.height - 1))
    val out = mutableListOf<AceClick.Target>()
    for (index in first..last) {
        val bounds = list.getCellBounds(index, index) ?: continue
        val clip = bounds.intersection(visible)
        if (!clip.isEmpty) {
            out.add(rowTarget(list, clip, layer) { selectListCell(list, index) })
        }
    }
    return out
}
