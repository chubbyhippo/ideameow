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
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.ln

object Avy {
    private const val KEYS = "asdfghjkl"

    private const val TIMEOUT_MS = 250

    private val LEAD_FG = JBColor(Color.WHITE, Color.WHITE)
    private val LEAD_BG = JBColor(Color(0xE5, 0x2B, 0x50), Color(0xE5, 0x2B, 0x50))

    val commands: Map<String, MeowCommand> =
        mapOf(
            "avy-goto-char-timer" to MeowCommand { ed, st -> startCharTimer(ed, st) },
            "avy-goto-line" to MeowCommand { ed, st -> startGotoLine(ed, st) },
        )

    enum class Phase { COLLECTING, SELECTING }

    class Session(
        val gotoLine: Boolean,
    ) {
        var phase = Phase.COLLECTING
        var input = ""
        var node: Branch? = null
        var timer: Timer? = null
        var canvas: JComponent? = null
        val matchHighlights = mutableListOf<RangeHighlighter>()
    }

    sealed class Node

    class Leaf(
        val offset: Int,
    ) : Node()

    class Branch(
        val children: List<Pair<Char, Node>>,
    ) : Node()

    fun subdiv(
        n: Int,
        b: Int,
    ): List<Int> {
        val p = kotlin.math.floor(ln(n.toDouble()) / ln(b.toDouble()) + 1e-6).toInt() - 1
        var x1 = 1
        repeat(p) { x1 *= b }
        val x2 = b * x1
        val delta = n - x2
        val n2 = delta / (x2 - x1)
        val n1 = b - n2 - 1
        return List(n1) { x1 } + listOf(n - n1 * x1 - n2 * x2) + List(n2) { x2 }
    }

    fun tree(
        candidates: List<Int>,
        keys: String = KEYS,
    ): Branch {
        if (candidates.size < keys.length) {
            return Branch(keys.toList().zip(candidates.map { Leaf(it) as Node }))
        }
        var rest = candidates
        val children = mutableListOf<Pair<Char, Node>>()
        for ((i, size) in subdiv(candidates.size, keys.length).withIndex()) {
            val taken = rest.take(size)
            rest = rest.drop(size)
            children.add(keys[i] to if (size == 1) Leaf(taken[0]) else tree(taken, keys))
        }
        return Branch(children)
    }

    fun labels(node: Branch): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()

        fun walk(
            n: Node,
            path: String,
        ) {
            when (n) {
                is Leaf -> out.add(n.offset to path)
                is Branch -> n.children.forEach { (k, child) -> walk(child, path + k) }
            }
        }
        walk(node, "")
        return out
    }

    private fun startCharTimer(
        editor: Editor,
        st: MeowState,
    ) {
        cancel(editor, st)
        st.avy = Session(gotoLine = false)
    }

    private fun startGotoLine(
        editor: Editor,
        st: MeowState,
    ) {
        cancel(editor, st)
        val session = Session(gotoLine = true)
        st.avy = session
        val doc = editor.document
        val (first, last) = Ide.visibleLines(editor)
        val candidates = (first..last).map { doc.getLineStartOffset(it) }
        toSelecting(editor, session, candidates)
    }

    fun key(
        editor: Editor,
        st: MeowState,
        c: Char,
    ) {
        val session = st.avy ?: return
        when (session.phase) {
            Phase.COLLECTING -> collect(editor, st, session, c)
            Phase.SELECTING -> select(editor, st, session, c)
        }
    }

    private fun collect(
        editor: Editor,
        st: MeowState,
        session: Session,
        c: Char,
    ) {
        session.input += c
        session.timer?.stop()
        session.timer =
            Timer(TIMEOUT_MS) { finishInput(editor, st) }.apply {
                isRepeats = false
                start()
            }
        highlightMatches(editor, session)
    }

    fun finishInput(
        editor: Editor,
        st: MeowState,
    ) {
        val session = st.avy ?: return
        if (session.phase != Phase.COLLECTING) return
        session.timer?.stop()
        session.timer = null
        val candidates = matches(editor, session.input)
        when {
            candidates.isEmpty() -> {
                cancel(editor, st)
                Ide.hint(editor, "zero candidates")
            }

            candidates.size == 1 -> {
                cancel(editor, st)
                jump(editor, candidates[0])
            }

            else -> {
                toSelecting(editor, session, candidates)
            }
        }
    }

    private fun toSelecting(
        editor: Editor,
        session: Session,
        candidates: List<Int>,
    ) {
        clearVisuals(editor, session)
        session.phase = Phase.SELECTING
        session.node = tree(candidates)
        paintLabels(editor, session)
    }

    private fun select(
        editor: Editor,
        st: MeowState,
        session: Session,
        c: Char,
    ) {
        if (session.gotoLine && c.isDigit()) {
            cancel(editor, st)
            val input =
                Messages.showInputDialog(
                    editor.project,
                    "Goto line:",
                    "Avy",
                    null,
                    c.toString(),
                    null,
                ) ?: return
            val doc = editor.document
            val ln = parsedLineNumber(input, doc.lineCount) ?: return
            jump(editor, doc.getLineStartOffset(ln))
            return
        }
        val node = session.node ?: return
        when (val child = node.children.firstOrNull { it.first == c }?.second) {
            is Leaf -> {
                cancel(editor, st)
                jump(editor, child.offset)
            }

            is Branch -> {
                session.node = child
                paintLabels(editor, session)
            }

            null -> {
                Ide.hint(editor, "No such candidate: $c")
            }
        }
    }

    private fun jump(
        editor: Editor,
        offset: Int,
    ) {
        val sm = editor.selectionModel
        if (sm.hasSelection()) {
            val anchor = Selections.mark(editor)
            editor.caretModel.moveToOffset(offset)
            sm.setSelection(minOf(anchor, offset), maxOf(anchor, offset))
        } else {
            editor.caretModel.moveToOffset(offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    fun cancel(
        editor: Editor,
        st: MeowState,
    ) {
        st.avy?.let { session ->
            session.timer?.stop()
            session.timer = null
            clearVisuals(editor, session)
        }
        st.avy = null
    }

    private fun matches(
        editor: Editor,
        input: String,
    ): List<Int> {
        if (input.isEmpty()) return emptyList()
        val doc = editor.document
        val (first, last) = Ide.visibleLines(editor)
        val from = doc.getLineStartOffset(first)
        val to = doc.getLineEndOffset(last)
        val text = doc.charsSequence
        val out = mutableListOf<Int>()
        var i = from
        while (i <= to - input.length) {
            if (text.regionMatches(i, input, 0, input.length, ignoreCase = true)) {
                out.add(i)
                i += input.length
            } else {
                i++
            }
        }
        return out
    }

    private fun highlightMatches(
        editor: Editor,
        session: Session,
    ) {
        session.matchHighlights.forEach { editor.markupModel.removeHighlighter(it) }
        session.matchHighlights.clear()
        val attrs = editor.colorsScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
        for (offset in matches(editor, session.input)) {
            session.matchHighlights.add(
                editor.markupModel.addRangeHighlighter(
                    offset,
                    offset + session.input.length,
                    HighlighterLayer.SELECTION + 1,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE,
                ),
            )
        }
    }

    private fun paintLabels(
        editor: Editor,
        session: Session,
    ) {
        clearVisuals(editor, session)
        val labels = session.node?.let { labels(it) } ?: return
        val canvas = LabelsCanvas(editor, labels)
        Overlay.attach(editor, canvas)
        session.canvas = canvas
    }

    private fun clearVisuals(
        editor: Editor,
        session: Session,
    ) {
        Overlay.detach(session.canvas)
        session.canvas = null
        session.matchHighlights.forEach { editor.markupModel.removeHighlighter(it) }
        session.matchHighlights.clear()
    }

    private class LabelsCanvas(
        editor: Editor,
        private val labels: List<Pair<Int, String>>,
    ) : Overlay.Canvas(editor) {
        override fun paintLabels(
            g2: Graphics2D,
            metrics: FontMetrics,
        ) {
            val text = editor.document.charsSequence
            for ((offset, label) in labels) {
                val p = editor.offsetToXY(offset, true, false)
                var covered = 0
                var end = offset
                while (covered < label.length && end < text.length && text[end] != '\n') {
                    end++
                    covered++
                }
                val right = if (end > offset) editor.offsetToXY(end, true, false) else null
                val width =
                    if (right != null && right.y == p.y && right.x > p.x) {
                        maxOf(right.x - p.x, metrics.stringWidth(label) + Overlay.LABEL_PADDING)
                    } else {
                        metrics.stringWidth(label) + Overlay.LABEL_PADDING
                    }
                g2.color = LEAD_BG
                g2.fillRect(p.x, p.y, width, editor.lineHeight)
                g2.color = LEAD_FG
                g2.drawString(label, p.x + 1, p.y + editor.ascent)
            }
        }
    }
}
