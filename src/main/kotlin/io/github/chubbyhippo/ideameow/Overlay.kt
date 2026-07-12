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
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent

internal object Overlay {
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

    abstract class Canvas(
        protected val editor: Editor,
    ) : JComponent() {
        override fun paintComponent(g: Graphics) {
            if (editor.isDisposed) return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
            g2.font = font
            paintLabels(g2, editor.contentComponent.getFontMetrics(font))
        }

        protected abstract fun paintLabels(
            g2: Graphics2D,
            metrics: FontMetrics,
        )
    }
}
