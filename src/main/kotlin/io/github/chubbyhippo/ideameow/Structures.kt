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
            "meow-inner-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.INNER) },
            "meow-bounds-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.BOUNDS) },
            "meow-beginning-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.BEGIN) },
            "meow-end-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.END) },
            "meow-block" to MeowCommand { ed, st, _ -> block(ed, st) },
            "meow-to-block" to MeowCommand { ed, st, _ -> toBlock(ed, st) },
            "meow-join" to MeowCommand { ed, st, _ -> join(ed, st) },
        )

    private fun pendThing(
        editor: Editor,
        st: MeowState,
        p: Pending,
    ) {
        st.pending = p
        WhichKey.scheduleThings(editor)
    }

    fun thingSelect(
        editor: Editor,
        st: MeowState,
        kind: Pending,
        ch: Char,
    ) {
        val off = editor.caretModel.offset
        val b =
            when (kind) {
                Pending.BOUNDS -> Things.bounds(editor, ch, off)
                else -> Things.inner(editor, ch, off)
            } ?: run {
                Ide.hint(editor, "No thing '$ch' here")
                return
            }
        val (mark, point) =
            when (kind) {
                Pending.INNER -> b.start to b.end
                Pending.BOUNDS -> b.end to b.start
                Pending.BEGIN -> off to b.start
                Pending.END -> off to b.end
                else -> return
            }
        Selections.select(editor, st, SelType.TRANSIENT, mark, point, expand = false)
    }

    private data class PairRange(
        val open: Int,
        val close: Int,
    )

    private fun enclosingPair(
        text: CharSequence,
        s: Int,
        e: Int,
    ): PairRange? {
        val opens = "([{"
        val closes = ")]}"
        val stack = ArrayDeque<Int>()
        var best: PairRange? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"' || c == '\'' || c == '`') {
                var j = i + 1
                while (j < text.length && text[j] != c && text[j] != '\n') {
                    if (text[j] == '\\') j++
                    j++
                }
                if (j < text.length && text[j] == c) {
                    i = j + 1
                    continue
                }
            }
            when (c) {
                in opens -> {
                    stack.addLast(i)
                }

                in closes -> {
                    val kind = closes.indexOf(c)
                    while (stack.isNotEmpty()) {
                        val o = stack.removeLast()
                        if (opens.indexOf(text[o]) == kind) {
                            if (o < s && i + 1 >= e && (best == null || i - o < best.close - best.open)) {
                                best = PairRange(o, i)
                            }
                            break
                        }
                    }
                }
            }
            i++
        }
        return best
    }

    private fun block(
        editor: Editor,
        st: MeowState,
    ) {
        val sm = editor.selectionModel
        val text = editor.document.charsSequence
        val back = Selections.backwardP(editor) != (st.takeCount(1) < 0)
        val (s, e) =
            if (st.selType == SelType.BLOCK && sm.hasSelection()) {
                sm.selectionStart to sm.selectionEnd
            } else {
                editor.caretModel.offset to editor.caretModel.offset
            }
        val p =
            enclosingPair(text, s, e) ?: run {
                Ide.hint(editor, "No enclosing block")
                return
            }
        if (back) {
            Selections.select(editor, st, SelType.BLOCK, p.close + 1, p.open, expand = true)
        } else {
            Selections.select(editor, st, SelType.BLOCK, p.open, p.close + 1, expand = true)
        }
    }

    private fun toBlock(
        editor: Editor,
        st: MeowState,
    ) {
        val text = editor.document.charsSequence
        val back = (st.selType == SelType.BLOCK && Selections.backwardP(editor)) || st.takeCount(1) < 0
        val caret = editor.caretModel.offset
        val p =
            enclosingPair(text, caret, caret) ?: run {
                Ide.hint(editor, "No enclosing block")
                return
            }
        Selections.select(editor, st, SelType.BLOCK, caret, if (back) p.open else p.close + 1, expand = true)
    }

    private fun join(
        editor: Editor,
        st: MeowState,
    ) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val n = st.takeCount(1)
        val text = doc.charsSequence

        fun blank(l: Int) = text.subSequence(doc.getLineStartOffset(l), doc.getLineEndOffset(l)).isBlank()

        val ln = doc.getLineNumber(editor.caretModel.offset)
        val markLine: Int
        val pointLine: Int
        if (n >= 0) {
            var pl = ln - 1
            while (pl >= 0 && blank(pl)) pl--
            if (pl < 0) return
            markLine = pl
            pointLine = ln
        } else {
            var nl = ln + 1
            while (nl <= doc.lineCount - 1 && blank(nl)) nl++
            if (nl > doc.lineCount - 1) return
            markLine = ln
            pointLine = nl
        }
        val m = doc.getLineEndOffset(markLine)
        var p = doc.getLineStartOffset(pointLine)
        val eol = doc.getLineEndOffset(pointLine)
        while (p < eol && text[p].isWhitespace()) p++
        Selections.select(editor, st, SelType.JOIN, m, p, expand = true)
    }
}
