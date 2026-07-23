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
import com.intellij.openapi.editor.colors.EditorFontType
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JLayeredPane

internal object Overlay {
    const val LABEL_PADDING = 2

    fun attach(
        editor: Editor,
        canvas: Canvas,
    ) {
        val host = editor.contentComponent
        canvas.isOpaque = false
        canvas.bounds = Rectangle(0, 0, host.width, host.height)
        host.add(canvas)
        host.repaint()
    }

    fun detach(canvas: JComponent?) {
        val parent = canvas?.parent ?: return
        parent.remove(canvas)
        parent.repaint()
    }

    fun badge(
        layer: JLayeredPane,
        badges: List<Pair<Rectangle, String>>,
    ): JComponent {
        val canvas = BadgeCanvas(badges)
        canvas.isOpaque = false
        canvas.bounds = Rectangle(0, 0, layer.width, layer.height)
        layer.setLayer(canvas, JLayeredPane.DRAG_LAYER)
        layer.add(canvas)
        layer.repaint()
        return canvas
    }

    fun layerCanvas(
        layer: JLayeredPane,
        canvas: JComponent,
    ): JComponent {
        canvas.isOpaque = false
        canvas.bounds = Rectangle(0, 0, layer.width, layer.height)
        layer.setLayer(canvas, JLayeredPane.DRAG_LAYER)
        layer.add(canvas)
        layer.repaint()
        return canvas
    }

    private class BadgeCanvas(
        private val badges: List<Pair<Rectangle, String>>,
    ) : JComponent() {
        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = false

        override fun paintComponent(g: Graphics) {
            val graphics = g as Graphics2D
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.font = graphics.font.deriveFont(Font.BOLD)
            val metrics = graphics.fontMetrics
            for ((rect, label) in badges) {
                val width = metrics.stringWidth(label) + LABEL_PADDING
                graphics.color = Avy.LEAD_BG
                graphics.fillRect(rect.x, rect.y, width, metrics.height)
                graphics.color = Avy.LEAD_FG
                graphics.drawString(label, rect.x + 1, rect.y + metrics.ascent)
            }
        }
    }

    abstract class Canvas(
        protected val editor: Editor,
    ) : JComponent() {
        override fun paintComponent(g: Graphics) {
            if (editor.isDisposed) return
            val graphics = g as Graphics2D
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
            graphics.font = font
            paintLabels(graphics, editor.contentComponent.getFontMetrics(font))
        }

        protected abstract fun paintLabels(
            graphics: Graphics2D,
            metrics: FontMetrics,
        )
    }
}
