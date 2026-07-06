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
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Timer

/**
 * meow's expand hints: after an expandable selection (word/symbol/line/
 * find/till), small digit labels mark where 1-9 and 0 (=10) would take the
 * selection. Removed on the next key or after meow-expand-hint-remove-delay
 * (1 second), whichever comes first.
 */
object ExpandHints {
    private val HINT_COLOR = JBColor(Color(0xD0, 0x5C, 0x0A), Color(0xFF, 0xB0, 0x50))

    fun show(editor: Editor, st: MeowState) {
        clear(st)
        if (!editor.selectionModel.hasSelection()) return
        val positions = positions(editor, st, 10)
        for ((i, off) in positions.withIndex()) {
            val label = ((i + 1) % 10).toString()
            val inlay = editor.inlayModel.addInlineElement(off, true, DigitRenderer(label, editor)) ?: continue
            st.hints.add(inlay)
        }
        if (st.hints.isNotEmpty()) {
            st.hintTimer = Timer(1000) { clear(st) }.apply { isRepeats = false; start() }
        }
    }

    fun clear(st: MeowState) {
        st.hintTimer?.stop()
        st.hintTimer = null
        for (h in st.hints) if (h.isValid) Disposer.dispose(h)
        st.hints.clear()
    }

    private fun positions(editor: Editor, st: MeowState, count: Int): List<Int> {
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
                var i = caret
                repeat(count) {
                    val next = if (backward) text.lastIndexOfChar(c, i - 1) else text.indexOfChar(c, i + 1)
                    if (next < 0) return@repeat
                    out.add(if (st.selType == SelType.TILL) next else (next + 1).coerceAtMost(text.length))
                    i = next
                }
            }
            else -> {}
        }
        return out.distinct()
    }

    private class DigitRenderer(val label: String, val editor: Editor) : EditorCustomElementRenderer {
        private fun font() = editor.colorsScheme.getFont(EditorFontType.BOLD)

        override fun calcWidthInPixels(inlay: Inlay<*>): Int =
            editor.contentComponent.getFontMetrics(font()).stringWidth(label)

        override fun paint(inlay: Inlay<*>, g: Graphics, target: Rectangle, textAttributes: TextAttributes) {
            g.font = font()
            g.color = HINT_COLOR
            val fm = g.fontMetrics
            g.drawString(label, target.x, target.y + fm.ascent)
        }
    }
}
