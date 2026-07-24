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

internal object Structures {
    val commands: Map<String, MeowCommand> =
        mapOf(
            "meow-inner-of-thing" to MeowCommand { editor, state -> pendThing(editor, state, Pending.INNER) },
            "meow-bounds-of-thing" to MeowCommand { editor, state -> pendThing(editor, state, Pending.BOUNDS) },
            "meow-beginning-of-thing" to MeowCommand { editor, state -> pendThing(editor, state, Pending.BEGIN) },
            "meow-end-of-thing" to MeowCommand { editor, state -> pendThing(editor, state, Pending.END) },
            "meow-block" to MeowCommand { editor, state -> block(editor, state) },
            "meow-to-block" to MeowCommand { editor, state -> toBlock(editor, state) },
            "meow-join" to MeowCommand { editor, state -> join(editor, state) },
        )

    private fun pendThing(
        editor: Editor,
        state: MeowState,
        pending: Pending,
    ) {
        state.pending = pending
        WhichKey.scheduleThings(editor)
    }

    fun thingSelect(
        editor: Editor,
        state: MeowState,
        kind: Pending,
        char: Char,
    ) {
        val offset = editor.caretModel.offset
        val bounds =
            when (kind) {
                Pending.BOUNDS -> Things.bounds(editor, char, offset)
                else -> Things.inner(editor, char, offset)
            } ?: run {
                Ide.hint(editor, "No thing '$char' here")
                return
            }
        val (mark, point) =
            when (kind) {
                Pending.INNER -> bounds.start to bounds.end
                Pending.BOUNDS -> bounds.end to bounds.start
                Pending.BEGIN -> offset to bounds.start
                Pending.END -> offset to bounds.end
                else -> return
            }
        Selections.select(editor, state, SelType.TRANSIENT, mark, point, expand = false)
    }

    private data class PairRange(
        val open: Int,
        val close: Int,
    )

    private fun enclosingPair(
        text: CharSequence,
        start: Int,
        end: Int,
    ): PairRange? {
        val opens = "([{"
        val closes = ")]}"
        val stack = ArrayDeque<Int>()
        var best: PairRange? = null
        var i = 0
        while (i < text.length) {
            val afterQuote = skipQuoted(text, i)
            if (afterQuote != i) {
                i = afterQuote
                continue
            }
            val char = text[i]
            when (char) {
                in opens -> stack.addLast(i)

                in closes -> {
                    val open = popMatchingOpen(text, stack, opens, closes.indexOf(char))
                    if (open >= 0 && encloses(open, i, start, end) && tighterThan(best, open, i)) {
                        best = PairRange(open, i)
                    }
                }
            }
            i++
        }
        return best
    }

    private fun skipQuoted(
        text: CharSequence,
        offset: Int,
    ): Int {
        val quote = text[offset]
        if (quote != '"' && quote != '\'' && quote != '`') return offset
        var j = offset + 1
        while (j < text.length && text[j] != quote && text[j] != '\n') {
            if (text[j] == '\\') j++
            j++
        }
        return if (j < text.length && text[j] == quote) j + 1 else offset
    }

    private fun popMatchingOpen(
        text: CharSequence,
        stack: ArrayDeque<Int>,
        opens: String,
        closeKind: Int,
    ): Int {
        while (stack.isNotEmpty()) {
            val open = stack.removeLast()
            if (opens.indexOf(text[open]) == closeKind) return open
        }
        return -1
    }

    private fun encloses(
        open: Int,
        close: Int,
        start: Int,
        end: Int,
    ) = open < start && close + 1 >= end

    private fun tighterThan(
        best: PairRange?,
        open: Int,
        close: Int,
    ) = best == null || close - open < best.close - best.open

    private fun block(
        editor: Editor,
        state: MeowState,
    ) {
        val selectionModel = editor.selectionModel
        val text = editor.document.charsSequence
        val back = Selections.backwardP(editor) != (state.takeCount(1) < 0)
        val (start, end) =
            if (state.selType == SelType.BLOCK && selectionModel.hasSelection()) {
                selectionModel.selectionStart to selectionModel.selectionEnd
            } else {
                editor.caretModel.offset to editor.caretModel.offset
            }
        val pair =
            enclosingPair(text, start, end) ?: run {
                Ide.hint(editor, "No enclosing block")
                return
            }
        if (back) {
            Selections.select(editor, state, SelType.BLOCK, pair.close + 1, pair.open, expand = true)
        } else {
            Selections.select(editor, state, SelType.BLOCK, pair.open, pair.close + 1, expand = true)
        }
    }

    private fun toBlock(
        editor: Editor,
        state: MeowState,
    ) {
        val text = editor.document.charsSequence
        val back = (state.selType == SelType.BLOCK && Selections.backwardP(editor)) || state.takeCount(1) < 0
        val caret = editor.caretModel.offset
        val pair =
            enclosingPair(text, caret, caret) ?: run {
                Ide.hint(editor, "No enclosing block")
                return
            }
        Selections.select(editor, state, SelType.BLOCK, caret, if (back) pair.open else pair.close + 1, expand = true)
    }

    private fun join(
        editor: Editor,
        state: MeowState,
    ) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val count = state.takeCount(1)
        val text = doc.charsSequence

        fun blank(line: Int) = text.subSequence(doc.getLineStartOffset(line), doc.getLineEndOffset(line)).isBlank()

        val ln = doc.getLineNumber(editor.caretModel.offset)
        val markLine: Int
        val pointLine: Int
        if (count >= 0) {
            var previousLine = ln - 1
            while (previousLine >= 0 && blank(previousLine)) previousLine--
            if (previousLine < 0) return
            markLine = previousLine
            pointLine = ln
        } else {
            var nextLine = ln + 1
            while (nextLine <= doc.lineCount - 1 && blank(nextLine)) nextLine++
            if (nextLine > doc.lineCount - 1) return
            markLine = ln
            pointLine = nextLine
        }
        val mark = doc.getLineEndOffset(markLine)
        var point = doc.getLineStartOffset(pointLine)
        val lineEnd = doc.getLineEndOffset(pointLine)
        while (point < lineEnd && text[point].isWhitespace()) point++
        Selections.select(editor, state, SelType.JOIN, mark, point, expand = true)
    }
}
