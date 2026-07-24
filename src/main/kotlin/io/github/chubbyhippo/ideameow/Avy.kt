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
import java.awt.FontMetrics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.ln

object Avy {
    private const val KEYS = "asdfghjkl"

    private const val TIMEOUT_MS = 250

    val LEAD_FG: JBColor get() = Rc.overlayTextColor()
    val LEAD_BG: JBColor get() = Rc.overlayColor()

    val commands: Map<String, MeowCommand> =
        mapOf(
            "avy-goto-char-timer" to MeowCommand { editor, state -> startCharTimer(editor, state) },
            "avy-goto-line" to MeowCommand { editor, state -> startGotoLine(editor, state) },
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
        count: Int,
        base: Int,
    ): List<Int> {
        val power = kotlin.math.floor(ln(count.toDouble()) / ln(base.toDouble()) + 1e-6).toInt() - 1
        var x1 = 1
        repeat(power) { x1 *= base }
        val x2 = base * x1
        val delta = count - x2
        val n2 = delta / (x2 - x1)
        val n1 = base - n2 - 1
        return List(n1) { x1 } + listOf(count - n1 * x1 - n2 * x2) + List(n2) { x2 }
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
            current: Node,
            path: String,
        ) {
            when (current) {
                is Leaf -> out.add(current.offset to path)
                is Branch -> current.children.forEach { (key, child) -> walk(child, path + key) }
            }
        }
        walk(node, "")
        return out
    }

    private fun startCharTimer(
        editor: Editor,
        state: MeowState,
    ) {
        cancel(editor, state)
        state.avy = Session(gotoLine = false)
    }

    private fun startGotoLine(
        editor: Editor,
        state: MeowState,
    ) {
        cancel(editor, state)
        val session = Session(gotoLine = true)
        state.avy = session
        val doc = editor.document
        val (first, last) = Ide.visibleLines(editor)
        val candidates = (first..last).map { doc.getLineStartOffset(it) }
        toSelecting(editor, session, candidates)
    }

    fun key(
        editor: Editor,
        state: MeowState,
        char: Char,
    ) {
        val session = state.avy ?: return
        when (session.phase) {
            Phase.COLLECTING -> collect(editor, state, session, char)
            Phase.SELECTING -> select(editor, state, session, char)
        }
    }

    private fun collect(
        editor: Editor,
        state: MeowState,
        session: Session,
        char: Char,
    ) {
        session.input += char
        session.timer?.stop()
        session.timer =
            Timer(TIMEOUT_MS) { finishInput(editor, state) }.apply {
                isRepeats = false
                start()
            }
        highlightMatches(editor, session)
    }

    fun finishInput(
        editor: Editor,
        state: MeowState,
    ) {
        val session = state.avy ?: return
        if (session.phase != Phase.COLLECTING) return
        session.timer?.stop()
        session.timer = null
        val candidates = matches(editor, session.input)
        when {
            candidates.isEmpty() -> {
                cancel(editor, state)
                Ide.hint(editor, "zero candidates")
            }

            candidates.size == 1 -> {
                cancel(editor, state)
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
        state: MeowState,
        session: Session,
        char: Char,
    ) {
        if (session.gotoLine && char.isDigit()) {
            cancel(editor, state)
            val input =
                Messages.showInputDialog(
                    editor.project,
                    "Goto line:",
                    "Avy",
                    null,
                    char.toString(),
                    null,
                ) ?: return
            val doc = editor.document
            val ln = parsedLineNumber(input, doc.lineCount) ?: return
            jump(editor, doc.getLineStartOffset(ln))
            return
        }
        val node = session.node ?: return
        when (val child = node.children.firstOrNull { it.first == char }?.second) {
            is Leaf -> {
                cancel(editor, state)
                jump(editor, child.offset)
            }

            is Branch -> {
                session.node = child
                paintLabels(editor, session)
            }

            null -> {
                Ide.hint(editor, "No such candidate: $char")
            }
        }
    }

    private fun jump(
        editor: Editor,
        offset: Int,
    ) {
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            val anchor = Selections.mark(editor)
            editor.caretModel.moveToOffset(offset)
            selectionModel.setSelection(minOf(anchor, offset), maxOf(anchor, offset))
        } else {
            editor.caretModel.moveToOffset(offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    fun cancel(
        editor: Editor,
        state: MeowState,
    ) {
        state.avy?.let { session ->
            session.timer?.stop()
            session.timer = null
            clearVisuals(editor, session)
        }
        state.avy = null
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
            graphics: Graphics2D,
            metrics: FontMetrics,
        ) {
            val text = editor.document.charsSequence
            for ((offset, label) in labels) {
                val origin = editor.offsetToXY(offset, true, false)
                var covered = 0
                var end = offset
                while (covered < label.length && end < text.length && text[end] != '\n') {
                    end++
                    covered++
                }
                val right = if (end > offset) editor.offsetToXY(end, true, false) else null
                val width =
                    if (right != null && right.y == origin.y && right.x > origin.x) {
                        maxOf(right.x - origin.x, metrics.stringWidth(label) + Overlay.LABEL_PADDING)
                    } else {
                        metrics.stringWidth(label) + Overlay.LABEL_PADDING
                    }
                graphics.color = LEAD_BG
                graphics.fillRect(origin.x, origin.y, width, editor.lineHeight)
                graphics.color = LEAD_FG
                graphics.drawString(label, origin.x + 1, origin.y + editor.ascent)
            }
        }
    }
}
