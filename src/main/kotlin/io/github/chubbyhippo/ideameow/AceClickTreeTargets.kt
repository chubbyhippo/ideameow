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

import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JLayeredPane
import javax.swing.JTree

private const val CHANGES_TREE_CLASS = "com.intellij.openapi.vcs.changes.ui.ChangesTree"

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

internal fun isCheckboxAwareTree(tree: JTree): Boolean = tree is CheckboxTreeBase || isChangesTree(tree.javaClass)

private fun isChangesTree(start: Class<*>): Boolean {
    var current: Class<*>? = start
    while (current != null && current.name != CHANGES_TREE_CLASS) current = current.superclass
    return current != null
}

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
