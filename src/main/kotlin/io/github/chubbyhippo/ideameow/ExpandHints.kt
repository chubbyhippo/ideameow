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
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import javax.swing.Timer

object ExpandHints {
    private const val HINT_TIMEOUT_MS = 1000

    private val HINT_COLOR = JBColor(Color(0xD0, 0x5C, 0x0A), Color(0xFF, 0xB0, 0x50))

    fun show(
        editor: Editor,
        state: MeowState,
    ) {
        clear(state)
        if (!editor.selectionModel.hasSelection()) return
        val positions = positions(editor, state, 10)
        if (positions.isEmpty()) return
        val labels = positions.mapIndexed { i, off -> off to ((i + 1) % 10).toString() }
        val canvas = HintsCanvas(editor, labels)
        Overlay.attach(editor, canvas)
        state.hintOverlay = canvas
        state.hintTimer =
            Timer(HINT_TIMEOUT_MS) { clear(state) }.apply {
                isRepeats = false
                start()
            }
    }

    fun clear(state: MeowState) {
        state.hintTimer?.stop()
        state.hintTimer = null
        Overlay.detach(state.hintOverlay)
        state.hintOverlay = null
    }

    private fun positions(
        editor: Editor,
        state: MeowState,
        count: Int,
    ): List<Int> {
        val text = editor.document.charsSequence
        val doc = editor.document
        val caret = editor.caretModel.offset
        val backward = editor.selectionModel.hasSelection() && caret <= editor.selectionModel.selectionStart
        val out = mutableListOf<Int>()
        when (state.selType) {
            SelType.WORD, SelType.SYMBOL -> {
                val pred = charPred(state.selType == SelType.SYMBOL)
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
                val c = state.lastFind ?: return out
                val till = state.selType == SelType.TILL
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
        editor: Editor,
        private val hints: List<Pair<Int, String>>,
    ) : Overlay.Canvas(editor) {
        override fun paintLabels(
            graphics: Graphics2D,
            metrics: FontMetrics,
        ) {
            val text = editor.document.charsSequence
            val lineHeight = editor.lineHeight
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
                        metrics.stringWidth(label) + Overlay.LABEL_PADDING
                    }
                graphics.color = editor.colorsScheme.defaultBackground
                graphics.fillRect(p.x, p.y, cell, lineHeight)
                graphics.color = HINT_COLOR
                graphics.drawString(
                    label,
                    p.x + ((cell - metrics.stringWidth(label)) / 2).coerceAtLeast(0),
                    p.y + editor.ascent,
                )
            }
        }
    }
}
