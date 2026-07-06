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

/**
 * Structural selections: the char-thing table behind `, . [ ]` (see Things),
 * bracket blocks (meow-block / meow-to-block), and the join region
 * (meow-join). The thing commands park a [Pending] key and let the dispatcher
 * hand the thing char to [thingSelect].
 */
internal object Structures {

    val commands: Map<String, MeowCommand> = mapOf(
        "meow-inner-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.INNER) },
        "meow-bounds-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.BOUNDS) },
        "meow-beginning-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.BEGIN) },
        "meow-end-of-thing" to MeowCommand { ed, st, _ -> pendThing(ed, st, Pending.END) },
        "meow-block" to MeowCommand { ed, st, _ -> block(ed, st) },
        "meow-to-block" to MeowCommand { ed, st, _ -> toBlock(ed, st) },
        "meow-join" to MeowCommand { ed, st, _ -> join(ed, st) },
    )

    private fun pendThing(editor: Editor, st: MeowState, p: Pending) {
        st.pending = p
        WhichKey.scheduleThings(editor)
    }

    /** The second half of the thing commands, once the thing char arrives. */
    fun thingSelect(editor: Editor, st: MeowState, kind: Pending, ch: Char) {
        val off = editor.caretModel.offset
        val b = when (kind) {
            Pending.BOUNDS -> Things.bounds(editor, ch, off)
            else -> Things.inner(editor, ch, off)
        } ?: run { Ide.hint(editor, "No thing '$ch' here"); return }
        when (kind) {
            Pending.INNER, Pending.BOUNDS -> Selections.select(editor, st, SelType.TRANSIENT, b.start, b.end, expand = false)
            Pending.BEGIN -> Selections.select(editor, st, SelType.TRANSIENT, off, b.start, expand = false)
            Pending.END -> Selections.select(editor, st, SelType.TRANSIENT, off, b.end, expand = false)
            else -> {}
        }
    }

    // ---------------------------------------------------------------- blocks

    private data class PairRange(val open: Int, val close: Int)

    /**
     * Smallest bracket pair strictly enclosing [s, e). Same-line quoted runs
     * are skipped — a text approximation of syntax-ppss.
     */
    private fun enclosingPair(text: CharSequence, s: Int, e: Int): PairRange? {
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
                if (j < text.length && text[j] == c) { i = j + 1; continue }
            }
            when (c) {
                in opens -> stack.addLast(i)
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

    /** meow-block: innermost pair INCLUDING delimiters; with an active block
     *  selection it expands to the parent. */
    private fun block(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        val text = editor.document.charsSequence
        val (s, e) =
            if (st.selType == SelType.BLOCK && sm.hasSelection()) sm.selectionStart to sm.selectionEnd
            else editor.caretModel.offset to editor.caretModel.offset
        val p = enclosingPair(text, s, e) ?: run { Ide.hint(editor, "No enclosing block"); return }
        Selections.select(editor, st, SelType.BLOCK, p.open, p.close + 1, expand = true)
    }

    /** meow-to-block: from point to the closing delimiter of the enclosing block. */
    private fun toBlock(editor: Editor, st: MeowState) {
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val p = enclosingPair(text, caret, caret) ?: run { Ide.hint(editor, "No enclosing block"); return }
        Selections.select(editor, st, SelType.BLOCK, caret, p.close + 1, expand = true)
    }

    // ------------------------------------------------------------------ join

    /** meow-join: select (expand . join) — end of the previous non-empty line
     *  through this line's indentation (forward variant with a negative arg);
     *  killing that selection is delete-indentation (see Edits.joinKill). */
    private fun join(editor: Editor, st: MeowState) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val n = st.takeCount(1)
        val text = doc.charsSequence
        fun blank(l: Int) =
            text.subSequence(doc.getLineStartOffset(l), doc.getLineEndOffset(l)).isBlank()

        val ln = doc.getLineNumber(editor.caretModel.offset)
        if (n >= 0) {
            var pl = ln - 1
            while (pl >= 0 && blank(pl)) pl--
            if (pl < 0) return
            val m = doc.getLineEndOffset(pl)
            var p = doc.getLineStartOffset(ln)
            val eol = doc.getLineEndOffset(ln)
            while (p < eol && text[p].isWhitespace()) p++
            Selections.select(editor, st, SelType.JOIN, m, p, expand = true)
        } else {
            var nl = ln + 1
            while (nl <= doc.lineCount - 1 && blank(nl)) nl++
            if (nl > doc.lineCount - 1) return
            val m = doc.getLineEndOffset(ln)
            var p = doc.getLineStartOffset(nl)
            val eol = doc.getLineEndOffset(nl)
            while (p < eol && text[p].isWhitespace()) p++
            Selections.select(editor, st, SelType.JOIN, m, p, expand = true)
        }
    }
}
