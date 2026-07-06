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
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.ln

/**
 * A native port of avy's two jumps from init.el — no AceJump plugin needed.
 * Every behavior below was read out of avy 0.5.0's source (avy.el), not
 * guessed:
 *
 * - `avy-goto-char-timer`: the first char waits indefinitely; each further
 *   char must arrive within the timeout (init.el sets 0.25 s) and restarts
 *   it; matches highlight live while typing (avy-goto-char-timer-face =
 *   the theme highlight); matching is literal and case-insensitive
 *   (avy-case-fold-search t); zero candidates ends with a message; exactly
 *   one jumps immediately (avy-single-candidate-jump t).
 * - Labeling uses avy-tree/avy-subdiv over avy-keys (a s d f g h j k l):
 *   with more candidates than keys the FIRST keys stay single-char and the
 *   last keys host subtrees; picking a branch key relabels with the shorter
 *   remaining paths (avy-style 'at-full: the full remaining label is painted
 *   OVER the text at the candidate). An unknown key just messages
 *   "No such candidate" and stays; ESC exits.
 * - `avy-goto-line`: labels every visible line beginning; typing a DIGIT
 *   switches to a "Goto line: " number prompt seeded with that digit.
 * - The jump is avy-action-goto = plain goto-char: an active selection
 *   extends to the target, a bare caret just moves.
 */
object Avy {

    /** avy-keys default. */
    private const val KEYS = "asdfghjkl"

    /** init.el: (avy-timeout-seconds 0.25). */
    private const val TIMEOUT_MS = 250

    /** avy-lead-face: white on amaranth, both themes. */
    private val LEAD_FG = JBColor(Color.WHITE, Color.WHITE)
    private val LEAD_BG = JBColor(Color(0xE5, 0x2B, 0x50), Color(0xE5, 0x2B, 0x50))

    val commands: Map<String, MeowCommand> = mapOf(
        "avy-goto-char-timer" to MeowCommand { ed, st, _ -> startCharTimer(ed, st) },
        "avy-goto-line" to MeowCommand { ed, st, _ -> startGotoLine(ed, st) },
    )

    enum class Phase { COLLECTING, SELECTING }

    class Session(val gotoLine: Boolean) {
        var phase = Phase.COLLECTING
        var input = ""
        var node: Branch? = null
        var timer: Timer? = null
        var canvas: JComponent? = null
        val matchHighlights = mutableListOf<RangeHighlighter>()
    }

    // ------------------------------------------------------------- the tree

    sealed class Node
    class Leaf(val offset: Int) : Node()
    class Branch(val children: List<Pair<Char, Node>>) : Node()

    /** avy-subdiv: distribute N candidates over B keys in a balanced way. */
    fun subdiv(n: Int, b: Int): List<Int> {
        val p = kotlin.math.floor(ln(n.toDouble()) / ln(b.toDouble()) + 1e-6).toInt() - 1
        var x1 = 1
        repeat(p) { x1 *= b }
        val x2 = b * x1
        val delta = n - x2
        val n2 = delta / (x2 - x1)
        val n1 = b - n2 - 1
        return List(n1) { x1 } + listOf(n - n1 * x1 - n2 * x2) + List(n2) { x2 }
    }

    /** avy-tree: fewer candidates than keys pair up 1:1; otherwise the
     *  subdiv sizes decide which keys are leaves and which host subtrees. */
    fun tree(candidates: List<Int>, keys: String = KEYS): Branch {
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

    /** Every leaf with its remaining label path from [node]. */
    fun labels(node: Branch): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        fun walk(n: Node, path: String) {
            when (n) {
                is Leaf -> out.add(n.offset to path)
                is Branch -> n.children.forEach { (k, child) -> walk(child, path + k) }
            }
        }
        walk(node, "")
        return out
    }

    // ------------------------------------------------------------- sessions

    private fun startCharTimer(editor: Editor, st: MeowState) {
        cancel(editor, st)
        st.avy = Session(gotoLine = false)
    }

    private fun startGotoLine(editor: Editor, st: MeowState) {
        cancel(editor, st)
        val session = Session(gotoLine = true)
        st.avy = session
        val doc = editor.document
        val (first, last) = visibleLines(editor)
        val candidates = (first..last).map { doc.getLineStartOffset(it) }
        toSelecting(editor, st, session, candidates)
    }

    /** One key of an active session; printable keys only reach us. */
    fun key(editor: Editor, st: MeowState, c: Char) {
        val session = st.avy ?: return
        when (session.phase) {
            Phase.COLLECTING -> collect(editor, st, session, c)
            Phase.SELECTING -> select(editor, st, session, c)
        }
    }

    private fun collect(editor: Editor, st: MeowState, session: Session, c: Char) {
        session.input += c
        session.timer?.stop()
        session.timer = Timer(TIMEOUT_MS) { finishInput(editor, st) }.apply {
            isRepeats = false
            start()
        }
        highlightMatches(editor, session)
    }

    /** The avy-timeout-seconds pause ended: label (or jump, or give up). */
    fun finishInput(editor: Editor, st: MeowState) {
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
                // avy-single-candidate-jump
                cancel(editor, st)
                jump(editor, st, candidates[0])
            }
            else -> toSelecting(editor, st, session, candidates)
        }
    }

    private fun toSelecting(editor: Editor, st: MeowState, session: Session, candidates: List<Int>) {
        clearVisuals(editor, session)
        session.phase = Phase.SELECTING
        session.node = tree(candidates)
        paintLabels(editor, session)
    }

    private fun select(editor: Editor, st: MeowState, session: Session, c: Char) {
        // avy-goto-line: a digit switches to plain goto-line by number
        if (session.gotoLine && c.isDigit()) {
            cancel(editor, st)
            val input = Messages.showInputDialog(
                editor.project, "Goto line:", "Avy", null, c.toString(), null,
            ) ?: return
            val doc = editor.document
            val ln = ((input.trim().toIntOrNull() ?: return) - 1).coerceIn(0, doc.lineCount - 1)
            jump(editor, st, doc.getLineStartOffset(ln))
            return
        }
        val node = session.node ?: return
        when (val child = node.children.firstOrNull { it.first == c }?.second) {
            is Leaf -> {
                cancel(editor, st)
                jump(editor, st, child.offset)
            }
            is Branch -> {
                session.node = child
                paintLabels(editor, session)
            }
            null -> Ide.hint(editor, "No such candidate: $c") // avy-handler-default: stay
        }
    }

    /** avy-action-goto: plain goto-char — an active selection extends. */
    private fun jump(editor: Editor, st: MeowState, offset: Int) {
        val sm = editor.selectionModel
        if (sm.hasSelection()) {
            val anchor = Selections.mark(editor)
            editor.caretModel.moveToOffset(offset)
            sm.setSelection(minOf(anchor, offset), maxOf(anchor, offset))
        } else {
            editor.caretModel.moveToOffset(offset)
        }
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.RELATIVE)
    }

    fun cancel(editor: Editor, st: MeowState) {
        st.avy?.let { session ->
            session.timer?.stop()
            session.timer = null
            clearVisuals(editor, session)
        }
        st.avy = null
    }

    // ----------------------------------------------------------- candidates

    private fun visibleLines(editor: Editor): Pair<Int, Int> {
        val doc = editor.document
        val area = editor.scrollingModel.visibleArea
        if (doc.textLength == 0) return 0 to 0
        val last = (doc.lineCount - 1).coerceAtLeast(0)
        if (area.height <= 0) return 0 to last // headless: the whole buffer
        val top = editor.xyToLogicalPosition(Point(0, area.y)).line.coerceIn(0, last)
        val bottom = editor.xyToLogicalPosition(Point(0, area.y + area.height)).line.coerceIn(0, last)
        return top to bottom
    }

    /** Literal, case-insensitive, non-overlapping matches in the visible
     *  region (avy--read-candidates with regexp-quote + case folding). */
    private fun matches(editor: Editor, input: String): List<Int> {
        if (input.isEmpty()) return emptyList()
        val doc = editor.document
        val (first, last) = visibleLines(editor)
        val from = doc.getLineStartOffset(first)
        val to = doc.getLineEndOffset(last)
        val text = doc.charsSequence
        val out = mutableListOf<Int>()
        var i = from
        while (i <= to - input.length) {
            if (text.regionMatches(i, input, 0, input.length, ignoreCase = true)) {
                out.add(i)
                i += input.length // re-search-forward: non-overlapping
            } else {
                i++
            }
        }
        return out
    }

    // -------------------------------------------------------------- visuals

    private fun highlightMatches(editor: Editor, session: Session) {
        session.matchHighlights.forEach { editor.markupModel.removeHighlighter(it) }
        session.matchHighlights.clear()
        val attrs = editor.colorsScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
        for (offset in matches(editor, session.input)) {
            session.matchHighlights.add(
                editor.markupModel.addRangeHighlighter(
                    offset, offset + session.input.length,
                    HighlighterLayer.SELECTION + 1, attrs, HighlighterTargetArea.EXACT_RANGE,
                ),
            )
        }
    }

    private fun paintLabels(editor: Editor, session: Session) {
        clearVisuals(editor, session)
        val labels = session.node?.let { labels(it) } ?: return
        val host = editor.contentComponent
        val canvas = LabelsCanvas(editor, labels)
        canvas.isOpaque = false
        canvas.bounds = Rectangle(0, 0, host.width, host.height)
        host.add(canvas)
        host.repaint()
        session.canvas = canvas
    }

    private fun clearVisuals(editor: Editor, session: Session) {
        session.canvas?.let { canvas ->
            canvas.parent?.let { parent ->
                parent.remove(canvas)
                parent.repaint()
            }
        }
        session.canvas = null
        session.matchHighlights.forEach { editor.markupModel.removeHighlighter(it) }
        session.matchHighlights.clear()
    }

    /** avy-style 'at-full: the remaining label path painted OVER the text at
     *  the candidate, covering one char cell per label char (clamped at the
     *  line end) — same paint-over technique as the expand hints. */
    private class LabelsCanvas(
        private val editor: Editor,
        private val labels: List<Pair<Int, String>>,
    ) : JComponent() {

        override fun paintComponent(g: Graphics) {
            if (editor.isDisposed) return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
            val metrics = editor.contentComponent.getFontMetrics(font)
            val text = editor.document.charsSequence
            g2.font = font
            for ((offset, label) in labels) {
                val p = editor.offsetToXY(offset, true, false)
                // cover up to label-length chars, but never past the line end
                var covered = 0
                var end = offset
                while (covered < label.length && end < text.length && text[end] != '\n') {
                    end++
                    covered++
                }
                val right = if (end > offset) editor.offsetToXY(end, true, false) else null
                val width = if (right != null && right.y == p.y && right.x > p.x) {
                    maxOf(right.x - p.x, metrics.stringWidth(label) + 2)
                } else {
                    metrics.stringWidth(label) + 2
                }
                g2.color = LEAD_BG
                g2.fillRect(p.x, p.y, width, editor.lineHeight)
                g2.color = LEAD_FG
                g2.drawString(label, p.x + 1, p.y + editor.ascent)
            }
        }
    }
}
