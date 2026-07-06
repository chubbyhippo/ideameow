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
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

/**
 * meow-grab / swap-grab / sync-grab — the secondary-selection stand-in — plus
 * the BEACON approximation: while a grab is active, a selection created
 * inside it drops a caret on every similar range, so native multiple carets
 * do the job of meow's kmacro replay.
 */
internal object Grab {

    private val BG = JBColor(Color(0xCD, 0xE8, 0xCD), Color(0x2F, 0x47, 0x2F))

    val commands: Map<String, MeowCommand> = mapOf(
        "meow-grab" to MeowCommand { ed, st, _ -> grab(ed, st) },
        "meow-sync-grab" to MeowCommand { ed, st, _ -> sync(ed, st) },
        "meow-swap-grab" to MeowCommand { ed, st, _ -> swap(ed, st) },
    )

    fun clear(editor: Editor, st: MeowState) {
        st.grab?.dispose()
        st.grab = null
        st.grabHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        st.grabHighlighter = null
    }

    private fun set(editor: Editor, st: MeowState, s: Int, e: Int) {
        st.grab = editor.document.createRangeMarker(s, e)
        if (e > s) {
            val attrs = TextAttributes(null, BG, null, null, Font.PLAIN)
            st.grabHighlighter = editor.markupModel.addRangeHighlighter(
                s, e, HighlighterLayer.SELECTION - 1, attrs, HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    /** meow-grab: region -> secondary selection (a marker at point with none). */
    private fun grab(editor: Editor, st: MeowState) {
        clear(editor, st)
        val sm = editor.selectionModel
        val s = if (sm.hasSelection()) sm.selectionStart else editor.caretModel.offset
        val e = if (sm.hasSelection()) sm.selectionEnd else editor.caretModel.offset
        set(editor, st, s, e)
        Selections.cancel(editor, st)
    }

    /** meow-sync-grab: secondary := region; selection cancelled. */
    private fun sync(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) { Ide.hint(editor, "meow-sync-grab needs a selection"); return }
        clear(editor, st)
        set(editor, st, sm.selectionStart, sm.selectionEnd)
        Selections.cancel(editor, st)
    }

    /** meow-swap-grab: exchange region and secondary text; the secondary stays
     *  at its location holding the swapped-in text. */
    private fun swap(editor: Editor, st: MeowState) {
        val g = st.grab
        val sm = editor.selectionModel
        if (g == null || !g.isValid) { Ide.hint(editor, "No grab"); return }
        if (!sm.hasSelection()) { Ide.hint(editor, "meow-swap-grab needs a selection"); return }
        val gs = g.startOffset
        val ge = g.endOffset
        val ss = sm.selectionStart
        val se = sm.selectionEnd
        if (maxOf(gs, ss) < minOf(ge, se) && !(gs == ss && ge == se)) {
            Ide.hint(editor, "Selection overlaps the grab"); return
        }
        val text = editor.document.charsSequence
        val grabText = text.subSequence(gs, ge).toString()
        val selText = text.subSequence(ss, se).toString()
        Ide.runWrite(editor, "Meow Swap Grab") {
            clear(editor, st)
            if (gs <= ss) {
                editor.document.replaceString(ss, se, grabText)
                editor.document.replaceString(gs, ge, selText)
                val delta = selText.length - (ge - gs)
                set(editor, st, gs, gs + selText.length)
                editor.caretModel.moveToOffset(ss + delta + grabText.length)
            } else {
                editor.document.replaceString(gs, ge, selText)
                editor.document.replaceString(ss, se, grabText)
                val delta = grabText.length - (se - ss)
                set(editor, st, gs + delta, gs + delta + selText.length)
                editor.caretModel.moveToOffset(ss + grabText.length)
            }
            sm.removeSelection()
            st.selType = SelType.NONE
        }
    }

    /** meow-pop-grab, the pop-selection fallback: grab becomes the selection. */
    fun pop(editor: Editor, st: MeowState): Boolean {
        val g = st.grab ?: return false
        if (!g.isValid) return false
        val s = g.startOffset
        val e = g.endOffset
        clear(editor, st)
        Selections.select(editor, st, SelType.TRANSIENT, s, e, expand = false)
        return true
    }

    /**
     * BEACON: with a grab active, creating a selection inside it drops a
     * caret+selection on every similar range in the grab. Invoked from the
     * selection primitive, so every selecting command participates.
     */
    fun beacon(editor: Editor, st: MeowState) {
        val g = st.grab ?: return
        if (!g.isValid || g.endOffset <= g.startOffset) return
        val sm = editor.selectionModel
        if (!sm.hasSelection()) return
        val ss = sm.selectionStart
        val se = sm.selectionEnd
        if (ss < g.startOffset || se > g.endOffset || se == ss) return
        val text = editor.document.charsSequence
        val states = mutableListOf<CaretState>()
        fun add(s: Int, e: Int) = states.add(
            CaretState(
                editor.offsetToLogicalPosition(e),
                editor.offsetToLogicalPosition(s),
                editor.offsetToLogicalPosition(e)
            )
        )
        when (st.selType) {
            SelType.WORD, SelType.SYMBOL, SelType.VISIT, SelType.FIND, SelType.TILL, SelType.CHAR -> {
                val sel = text.subSequence(ss, se).toString()
                if (sel.isBlank()) return
                val re =
                    if (st.selType == SelType.WORD || st.selType == SelType.SYMBOL)
                        Regex("\\b" + Regex.escape(sel) + "\\b")
                    else Regex(Regex.escape(sel))
                val region = text.subSequence(g.startOffset, g.endOffset)
                var added = 0
                for (m in re.findAll(region)) {
                    val s0 = g.startOffset + m.range.first
                    val e0 = g.startOffset + m.range.last + 1
                    if (s0 == ss) continue
                    add(s0, e0)
                    if (++added >= 500) break
                }
                if (states.isEmpty()) return
                add(ss, se)
            }
            SelType.LINE -> {
                val doc = editor.document
                val first = doc.getLineNumber(g.startOffset)
                val last = doc.getLineNumber((g.endOffset - 1).coerceAtLeast(g.startOffset))
                if (last <= first) return
                for (ln in first..last) add(doc.getLineStartOffset(ln), doc.getLineEndOffset(ln))
            }
            else -> return
        }
        editor.caretModel.setCaretsAndSelections(states)
    }
}
