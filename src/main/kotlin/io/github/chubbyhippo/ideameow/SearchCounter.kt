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
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D

// Meow's own search indicator (meow-visual.el: meow--highlight-regexp-in-buffer /
// meow--show-indicator, NOT anzu.el, which meow never depends on): whenever the
// current selection came from meow-mark-word/meow-mark-symbol (w/W), meow-search
// (n) or meow-visit (v), every other match of the ACTIVE search regex
// (`regexp-search-ring`'s head, ported here as `state.searchHistory`) is
// highlighted and a "[idx/cnt]" counter is drawn past the end of the line the
// current match sits on. Crucially this counts matches of the regex itself, not
// a freshly-escaped plain substring, so a word-mark's `\<...\>` boundary (or a
// symbol-mark's `\_<..._>` one) is honored exactly like real meow: marking "go"
// only counts standalone "go", not "goto"/"mango"/"ago".
internal object SearchCounter {
    private const val MAX_MATCHES = 2000

    private val COUNTABLE = setOf(SelType.WORD, SelType.SYMBOL, SelType.VISIT)

    fun update(
        editor: Editor,
        state: MeowState,
    ) {
        val selectionModel = editor.selectionModel
        val regex = state.searchHistory.lastOrNull()
        if (state.selType !in COUNTABLE || !selectionModel.hasSelection() || regex == null) {
            clear(editor, state)
            return
        }
        show(editor, state, regex, selectionModel.selectionEnd)
    }

    fun clear(
        editor: Editor,
        state: MeowState,
    ) {
        state.searchMatchHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        state.searchMatchHighlighters.clear()
        Overlay.detach(state.searchCounterOverlay)
        state.searchCounterOverlay = null
    }

    private fun show(
        editor: Editor,
        state: MeowState,
        regex: Regex,
        pos: Int,
    ) {
        clear(editor, state)
        val text = editor.document.charsSequence
        val ranges = mutableListOf<IntRange>()
        for (match in regex.findAll(text)) {
            if (match.value.isEmpty()) continue
            ranges.add(match.range.first until match.range.last + 1)
            if (ranges.size >= MAX_MATCHES) break
        }
        val currentIndex = ranges.indexOfFirst { pos in it.first..(it.last + 1) }
        if (ranges.size <= 1 || currentIndex < 0) return
        highlightOthers(editor, state, ranges, currentIndex)
        paintCounter(editor, state, pos, currentIndex + 1, ranges.size)
    }

    private fun highlightOthers(
        editor: Editor,
        state: MeowState,
        ranges: List<IntRange>,
        currentIndex: Int,
    ) {
        val attrs = TextAttributes(null, RcColors.searchMatchColor(), null, null, Font.PLAIN)
        ranges.forEachIndexed { index, range ->
            if (index == currentIndex) return@forEachIndexed
            state.searchMatchHighlighters.add(
                editor.markupModel.addRangeHighlighter(
                    range.first,
                    range.last + 1,
                    HighlighterLayer.SELECTION - 1,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE,
                ),
            )
        }
    }

    private fun paintCounter(
        editor: Editor,
        state: MeowState,
        matchEnd: Int,
        index: Int,
        total: Int,
    ) {
        val doc = editor.document
        val lineEnd = doc.getLineEndOffset(doc.getLineNumber(matchEnd))
        val canvas = CounterCanvas(editor, lineEnd, "[$index/$total]")
        Overlay.attach(editor, canvas)
        state.searchCounterOverlay = canvas
    }

    private class CounterCanvas(
        editor: Editor,
        private val offset: Int,
        private val label: String,
    ) : Overlay.Canvas(editor) {
        override fun paintLabels(
            graphics: Graphics2D,
            metrics: FontMetrics,
        ) {
            val point = editor.offsetToXY(offset, true, false)
            graphics.color = RcColors.searchCounterColor()
            graphics.drawString(" $label", point.x, point.y + editor.ascent)
        }
    }
}
