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

import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import java.awt.Rectangle
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities

internal fun annotationRows(
    gutter: EditorGutterComponentEx,
    layer: JLayeredPane,
): List<AceClick.Target>? {
    if (!gutter.isAnnotationsShown) return null
    val visible = gutter.visibleRect
    val width = gutter.annotationsAreaWidth
    if (visible.width <= 0 || visible.height <= 0 || width <= 0) return emptyList()
    val editor = gutter.editor
    val x = gutter.annotationsAreaOffset
    val first = editor.yToVisualLine(visible.y).coerceAtLeast(0)
    val last = editor.yToVisualLine(visible.y + visible.height - 1)
    val out = mutableListOf<AceClick.Target>()
    for (visualLine in first..last) {
        val range = editor.visualLineToYRange(visualLine)
        val rowRect = Rectangle(x, range[0], width, range[1] - range[0]).intersection(visible)
        if (!rowRect.isEmpty) out.add(annotationTarget(gutter, rowRect, layer))
    }
    return out
}

private fun annotationTarget(
    gutter: EditorGutterComponentEx,
    rectInComponent: Rectangle,
    layer: JLayeredPane,
): AceClick.Target {
    val screen = Rectangle(rectInComponent)
    val corner = screen.location
    SwingUtilities.convertPointToScreen(corner, gutter)
    screen.location = corner
    val point = centerOf(rectInComponent)
    return AceClick.Target(
        SwingUtilities.convertRectangle(gutter, rectInComponent, layer),
        gutter,
        layer,
        screen,
        click = { pointClick(gutter, point) },
        rightClick = { popupClick(gutter, point) },
    )
}
