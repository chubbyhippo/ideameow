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

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

internal object Grab {
    private const val MAX_BEACON_CARETS = 500

    private val BG = JBColor(Color(0xCD, 0xE8, 0xCD), Color(0x2F, 0x47, 0x2F))

    val commands: Map<String, MeowCommand> =
        mapOf(
            "meow-grab" to MeowCommand { editor, state -> grab(editor, state) },
            "meow-sync-grab" to MeowCommand { editor, state -> sync(editor, state) },
            "meow-swap-grab" to MeowCommand { editor, state -> swap(editor, state) },
        )

    fun clear(
        editor: Editor,
        state: MeowState,
    ) {
        state.grab?.dispose()
        state.grab = null
        state.grabHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        state.grabHighlighter = null
    }

    private fun set(
        editor: Editor,
        state: MeowState,
        start: Int,
        end: Int,
    ) {
        state.grab = editor.document.createRangeMarker(start, end)
        if (end > start) {
            val attrs = TextAttributes(null, BG, null, null, Font.PLAIN)
            state.grabHighlighter =
                editor.markupModel.addRangeHighlighter(
                    start,
                    end,
                    HighlighterLayer.SELECTION - 1,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE,
                )
        }
    }

    private fun grab(
        editor: Editor,
        state: MeowState,
    ) {
        clear(editor, state)
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            set(editor, state, selectionModel.selectionStart, selectionModel.selectionEnd)
        }
        Selections.cancel(editor, state)
    }

    private fun sync(
        editor: Editor,
        state: MeowState,
    ) {
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            Ide.hint(editor, "meow-sync-grab needs a selection")
            return
        }
        clear(editor, state)
        set(editor, state, selectionModel.selectionStart, selectionModel.selectionEnd)
        Selections.cancel(editor, state)
    }

    private fun swap(
        editor: Editor,
        state: MeowState,
    ) {
        if (Edits.blockedReadOnly(editor)) return
        val grabMarker = state.grab
        val selectionModel = editor.selectionModel
        if (grabMarker == null || !grabMarker.isValid) {
            Ide.hint(editor, "No grab")
            return
        }
        if (!selectionModel.hasSelection()) {
            Ide.hint(editor, "meow-swap-grab needs a selection")
            return
        }
        val grabStart = grabMarker.startOffset
        val grabEnd = grabMarker.endOffset
        val selStart = selectionModel.selectionStart
        val selEnd = selectionModel.selectionEnd
        if (maxOf(grabStart, selStart) < minOf(grabEnd, selEnd) && !(grabStart == selStart && grabEnd == selEnd)) {
            Ide.hint(editor, "Selection overlaps the grab")
            return
        }
        val text = editor.document.charsSequence
        val grabText = text.subSequence(grabStart, grabEnd).toString()
        val selectionText = text.subSequence(selStart, selEnd).toString()
        Ide.runWrite(editor, "Meow Swap Grab") {
            clear(editor, state)
            if (grabStart <= selStart) {
                editor.document.replaceString(selStart, selEnd, grabText)
                editor.document.replaceString(grabStart, grabEnd, selectionText)
                val delta = selectionText.length - (grabEnd - grabStart)
                set(editor, state, grabStart, grabStart + selectionText.length)
                editor.caretModel.moveToOffset(selStart + delta + grabText.length)
            } else {
                editor.document.replaceString(grabStart, grabEnd, selectionText)
                editor.document.replaceString(selStart, selEnd, grabText)
                val delta = grabText.length - (selEnd - selStart)
                set(editor, state, grabStart + delta, grabStart + delta + selectionText.length)
                editor.caretModel.moveToOffset(selStart + grabText.length)
            }
            selectionModel.removeSelection()
            state.selType = SelType.NONE
        }
    }

    fun pop(
        editor: Editor,
        state: MeowState,
    ): Boolean {
        val grabMarker = state.grab ?: return false
        if (!grabMarker.isValid) return false
        val start = grabMarker.startOffset
        val end = grabMarker.endOffset
        clear(editor, state)
        Selections.select(editor, state, SelType.TRANSIENT, start, end, expand = false)
        return true
    }

    fun beacon(
        editor: Editor,
        state: MeowState,
    ) {
        val grabMarker = state.grab ?: return
        if (!grabMarker.isValid || grabMarker.endOffset <= grabMarker.startOffset) return
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return
        val selStart = selectionModel.selectionStart
        val selEnd = selectionModel.selectionEnd
        if (selStart < grabMarker.startOffset || selEnd > grabMarker.endOffset || selEnd == selStart) return
        val ranges = beaconRanges(editor, state, grabMarker) ?: return
        editor.caretModel.setCaretsAndSelections(ranges.map { (start, end) -> caretState(editor, start, end) })
    }

    private fun beaconRanges(
        editor: Editor,
        state: MeowState,
        grabMarker: RangeMarker,
    ): List<Pair<Int, Int>>? =
        when (state.selType) {
            SelType.WORD, SelType.SYMBOL, SelType.VISIT, SelType.FIND, SelType.TILL, SelType.CHAR ->
                matchRanges(editor, state, grabMarker)

            SelType.LINE -> lineRanges(editor, grabMarker)
            else -> null
        }

    private fun matchRanges(
        editor: Editor,
        state: MeowState,
        grabMarker: RangeMarker,
    ): List<Pair<Int, Int>>? {
        val selectionModel = editor.selectionModel
        val selStart = selectionModel.selectionStart
        val selEnd = selectionModel.selectionEnd
        val text = editor.document.charsSequence
        val selectionText = text.subSequence(selStart, selEnd).toString()
        if (selectionText.isBlank()) return null
        val regex =
            if (state.selType == SelType.WORD || state.selType == SelType.SYMBOL) {
                Regex("\\b" + Regex.escape(selectionText) + "\\b")
            } else {
                Regex(Regex.escape(selectionText))
            }
        val region = text.subSequence(grabMarker.startOffset, grabMarker.endOffset)
        val ranges = mutableListOf<Pair<Int, Int>>()
        for (match in regex.findAll(region)) {
            val matchStart = grabMarker.startOffset + match.range.first
            val matchEnd = grabMarker.startOffset + match.range.last + 1
            if (matchStart == selStart) continue
            ranges.add(matchStart to matchEnd)
            if (ranges.size >= MAX_BEACON_CARETS) break
        }
        if (ranges.isEmpty()) return null
        ranges.add(selStart to selEnd)
        return ranges
    }

    private fun lineRanges(
        editor: Editor,
        grabMarker: RangeMarker,
    ): List<Pair<Int, Int>>? {
        val doc = editor.document
        val first = doc.getLineNumber(grabMarker.startOffset)
        val last = doc.getLineNumber((grabMarker.endOffset - 1).coerceAtLeast(grabMarker.startOffset))
        if (last <= first) return null
        return (first..last).map { ln -> doc.getLineStartOffset(ln) to doc.getLineEndOffset(ln) }
    }
}

private fun caretState(
    editor: Editor,
    start: Int,
    end: Int,
): CaretState =
    CaretState(
        editor.offsetToLogicalPosition(end),
        editor.offsetToLogicalPosition(start),
        editor.offsetToLogicalPosition(end),
    )
