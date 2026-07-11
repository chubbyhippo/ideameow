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
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.Timer

object ExpandHints {
    private const val HINT_TIMEOUT_MS = 1000

    private val HINT_COLOR = JBColor(Color(0xD0, 0x5C, 0x0A), Color(0xFF, 0xB0, 0x50))

    fun show(
        editor: Editor,
        st: MeowState,
    ) {
        clear(st)
        if (!editor.selectionModel.hasSelection()) return
        val positions = positions(editor, st, 10)
        if (positions.isEmpty()) return
        val labels = positions.mapIndexed { i, off -> off to ((i + 1) % 10).toString() }
        val host = editor.contentComponent
        val canvas = HintsCanvas(editor, labels)
        canvas.isOpaque = false
        canvas.bounds = Rectangle(0, 0, host.width, host.height)
        host.add(canvas)
        host.repaint()
        st.hintOverlay = canvas
        st.hintTimer =
            Timer(HINT_TIMEOUT_MS) { clear(st) }.apply {
                isRepeats = false
                start()
            }
    }

    fun clear(st: MeowState) {
        st.hintTimer?.stop()
        st.hintTimer = null
        st.hintOverlay?.let { canvas ->
            canvas.parent?.let { parent ->
                parent.remove(canvas)
                parent.repaint()
            }
        }
        st.hintOverlay = null
    }

    private fun positions(
        editor: Editor,
        st: MeowState,
        count: Int,
    ): List<Int> {
        val text = editor.document.charsSequence
        val doc = editor.document
        val caret = editor.caretModel.offset
        val backward = editor.selectionModel.hasSelection() && caret <= editor.selectionModel.selectionStart
        val out = mutableListOf<Int>()
        when (st.selType) {
            SelType.WORD, SelType.SYMBOL -> {
                val pred = charPred(st.selType == SelType.SYMBOL)
                var i = caret
                repeat(count) {
                    i = if (backward) Words.prevStart(text, i, 1, pred) else Words.nextEnd(text, i, 1, pred)
                    if (if (backward) i <= 0 else i >= text.length) return@repeat
                    out.add(i)
                }
            }

            SelType.LINE -> {
                var ln = doc.getLineNumber(caret)
                repeat(count) {
                    ln += if (backward) -1 else 1
                    if (ln < 0 || ln > doc.lineCount - 1) return@repeat
                    out.add(if (backward) doc.getLineStartOffset(ln) else doc.getLineEndOffset(ln))
                }
            }

            SelType.FIND, SelType.TILL -> {
                val c = st.lastFind ?: return out
                val till = st.selType == SelType.TILL
                for (k in 1..count) {
                    val t = nthCharTarget(text, c, caret, k, backward, till)
                    if (t < 0) break
                    out.add(t)
                }
            }

            else -> {}
        }
        return out.distinct()
    }

    private class HintsCanvas(
        private val editor: Editor,
        private val hints: List<Pair<Int, String>>,
    ) : JComponent() {
        override fun paintComponent(g: Graphics) {
            if (editor.isDisposed) return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
            val metrics = editor.contentComponent.getFontMetrics(font)
            val text = editor.document.charsSequence
            val lineHeight = editor.lineHeight
            g2.font = font
            for ((offset, label) in hints) {
                val p = editor.offsetToXY(offset, true, false)
                val next =
                    if (offset < text.length && text[offset] != '\n') {
                        editor.offsetToXY(offset + 1, true, false)
                    } else {
                        null
                    }
                val cell =
                    if (next != null && next.y == p.y && next.x > p.x) {
                        next.x - p.x
                    } else {
                        metrics.stringWidth(label) + 2
                    }
                g2.color = editor.colorsScheme.defaultBackground
                g2.fillRect(p.x, p.y, cell, lineHeight)
                g2.color = HINT_COLOR
                g2.drawString(
                    label,
                    p.x + ((cell - metrics.stringWidth(label)) / 2).coerceAtLeast(0),
                    p.y + editor.ascent,
                )
            }
        }
    }
}
