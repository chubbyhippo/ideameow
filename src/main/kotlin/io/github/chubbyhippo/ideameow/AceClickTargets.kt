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
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JTree
import javax.swing.SwingUtilities

private const val CHANGES_TREE_CLASS = "com.intellij.openapi.vcs.changes.ui.ChangesTree"

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

internal fun clickTreeCheckbox(
    tree: JTree,
    row: Int,
) {
    val node = tree.getPathForRow(row)?.lastPathComponent ?: return
    if (tree is CheckboxTreeBase && node is CheckedTreeNode) {
        tree.setNodeState(node, !node.isChecked)
        return
    }
    if (isChangesTree(tree.javaClass)) clickChangesTreeCheckbox(tree, row)
}

private fun isChangesTree(start: Class<*>): Boolean {
    var current: Class<*>? = start
    while (current != null && current.name != CHANGES_TREE_CLASS) current = current.superclass
    return current != null
}

internal fun isCheckboxAwareTree(tree: JTree): Boolean = tree is CheckboxTreeBase || isChangesTree(tree.javaClass)

private fun clickChangesTreeCheckbox(
    tree: JTree,
    row: Int,
) {
    val bounds = tree.getRowBounds(row) ?: return
    val x = bounds.x + JCheckBox().preferredSize.width / 2
    val y = bounds.y + bounds.height / 2
    val time = System.currentTimeMillis()
    for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
        tree.dispatchEvent(MouseEvent(tree, id, time, 0, x, y, 1, false, MouseEvent.BUTTON1))
    }
}

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
    val checkbox = list.model.getElementAt(index) as? JCheckBox ?: return
    if (!checkbox.isEnabled) return
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
    val checkboxTree = isCheckboxAwareTree(tree)
    val out = mutableListOf<AceClick.Target>()
    for (row in first..last) {
        val bounds = tree.getRowBounds(row) ?: continue
        val clip = bounds.intersection(visible)
        if (!clip.isEmpty) {
            out.add(
                rowTarget(tree, clip, layer) {
                    selectTreeRow(tree, row)
                    if (checkboxTree) clickTreeCheckbox(tree, row)
                },
            )
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
