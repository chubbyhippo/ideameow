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

import com.intellij.codeInsight.hint.HintManager
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import kotlin.math.abs

/**
 * The meow QWERTY command set. Selection-first: commands act on the active
 * selection or fall back per meow-selection-command-fallback.
 */
object Engine {

    private val EXPANDABLE = setOf(
        SelType.CHAR, SelType.WORD, SelType.SYMBOL, SelType.LINE, SelType.FIND, SelType.TILL
    )

    private val GRAB_BG = JBColor(Color(0xCD, 0xE8, 0xCD), Color(0x2F, 0x47, 0x2F))

    // ------------------------------------------------------------------ entry

    /** @return true when the key was consumed (typed handler skips insertion). */
    fun handleChar(editor: Editor, c: Char, ctx: DataContext?): Boolean {
        val st = Meow.state(editor) ?: return false
        if (st.mode == MeowMode.INSERT) return false
        if (st.mode == MeowMode.KEYPAD) {
            Keypad.key(editor, st, c, ctx)
            Meow.updateWidgets()
            return true
        }

        WhichKey.hide()
        ExpandHints.clear(editor, st)

        if (!st.replaying && c != '\'') {
            if (st.pending == null && st.pendingCount == 0 && !st.negative) st.unit.clear()
            st.unit.add(c)
        }

        val pend = st.pending
        val motionish = st.mode == MeowMode.MOTION || editor.isViewer || !editor.document.isWritable
        when {
            pend != null -> {
                st.pending = null
                resolvePending(editor, st, pend, c, ctx)
            }
            motionish -> motionKey(editor, st, c)
            else -> normalKey(editor, st, c, ctx)
        }

        val prefixy = st.pending != null ||
                (c.isDigit() && st.pendingCount != 0) ||
                (c == '-' && st.negative) ||
                c == ' '
        if (!st.replaying && c != '\'' && !prefixy) st.lastKeys = st.unit.toList()

        Meow.updateWidgets()
        return true
    }

    private fun motionKey(editor: Editor, st: MeowState, c: Char) {
        when (c) {
            'j' -> moveLine(editor, st, st.takeCount(1))
            'k' -> moveLine(editor, st, -st.takeCount(1))
            ' ' -> Meow.setMode(editor, st, MeowMode.KEYPAD)
            else -> {}
        }
    }

    private fun normalKey(editor: Editor, st: MeowState, c: Char, ctx: DataContext?) {
        // ~/.ideameowrc nmap overrides come first; a noremap RHS bypasses them
        if (st.noremapDepth == 0) {
            val override = Rc.cfg().normal[c]
            if (override != null) {
                runBinding(editor, st, override, ctx)
                return
            }
        }
        when (c) {
            in '0'..'9' -> {
                if (editor.selectionModel.hasSelection() && st.selType in EXPANDABLE) {
                    expand(editor, st, if (c == '0') 10 else c - '0')
                } else {
                    // meow-expand falls back to meow-digit-argument
                    st.pendingCount = st.pendingCount * 10 + (c - '0')
                }
            }
            '-' -> st.negative = true
            ';' -> reverse(editor)
            ',' -> { st.pending = Pending.INNER; WhichKey.scheduleThings(editor) }
            '.' -> { st.pending = Pending.BOUNDS; WhichKey.scheduleThings(editor) }
            '[', '<' -> { st.pending = Pending.BEGIN; WhichKey.scheduleThings(editor) }
            ']', '>' -> { st.pending = Pending.END; WhichKey.scheduleThings(editor) }
            'a' -> append(editor, st)
            'A' -> openBelow(editor, st, ctx)
            'b' -> backWord(editor, st, symbol = false)
            'B' -> backWord(editor, st, symbol = true)
            'c' -> change(editor, st)
            'd' -> delete(editor, st)
            'D' -> backwardDelete(editor, st)
            'e' -> nextWord(editor, st, symbol = false)
            'E' -> nextWord(editor, st, symbol = true)
            'f' -> st.pending = Pending.FIND
            't' -> st.pending = Pending.TILL
            'g' -> cancelAll(editor, st)
            'G' -> grab(editor, st)
            'h' -> moveChar(editor, st, -st.takeCount(1))
            'H' -> moveExpand(editor, st, -st.takeCount(1), 0)
            'i' -> insert(editor, st)
            'I' -> openAbove(editor, st, ctx)
            'j' -> moveLine(editor, st, st.takeCount(1))
            'J' -> moveExpand(editor, st, 0, st.takeCount(1))
            'k' -> moveLine(editor, st, -st.takeCount(1))
            'K' -> moveExpand(editor, st, 0, -st.takeCount(1))
            'l' -> moveChar(editor, st, st.takeCount(1))
            'L' -> moveExpand(editor, st, st.takeCount(1), 0)
            'm' -> join(editor, st)
            'n' -> search(editor, st)
            'o' -> block(editor, st)
            'O' -> toBlock(editor, st)
            'p' -> yank(editor, st)
            'q' -> act(editor, ctx, "CloseContent")
            'Q' -> gotoLine(editor, st)
            'r' -> replace(editor, st)
            'R' -> swapGrab(editor, st)
            's' -> kill(editor, st, ctx)
            'u' -> undo(editor, st, ctx)
            'U' -> undo(editor, st, ctx) // undo-in-selection: no IDE analog, see README
            'v' -> visit(editor, st)
            'w' -> markWord(editor, st, symbol = false)
            'W' -> markWord(editor, st, symbol = true)
            'x' -> line(editor, st)
            'X' -> gotoLine(editor, st)
            'y' -> save(editor, ctx)
            'Y' -> syncGrab(editor, st)
            'z' -> popSelection(editor, st)
            '\'' -> repeatLast(editor, st, ctx)
            ' ' -> {
                Meow.setMode(editor, st, MeowMode.KEYPAD)
                WhichKey.scheduleKeypad(editor, "")
            }
            else -> {} // undefined in NORMAL: swallow, never self-insert
        }
    }

    private fun resolvePending(editor: Editor, st: MeowState, p: Pending, c: Char, ctx: DataContext?) {
        when (p) {
            Pending.FIND -> findTill(editor, st, c, till = false)
            Pending.TILL -> findTill(editor, st, c, till = true)
            Pending.INNER, Pending.BOUNDS, Pending.BEGIN, Pending.END -> thingSelect(editor, st, p, c)
            else -> {}
        }
    }

    // ------------------------------------------------------------- selection

    fun hint(editor: Editor, text: String) {
        // best-effort: hints cannot render in headless (test) mode
        runCatching { HintManager.getInstance().showInformationHint(editor, text) }
    }

    private fun backwardP(editor: Editor): Boolean {
        val sm = editor.selectionModel
        return sm.hasSelection() && editor.caretModel.offset <= sm.selectionStart
    }

    private fun mark(editor: Editor): Int {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) return editor.caretModel.offset
        return if (backwardP(editor)) sm.selectionEnd else sm.selectionStart
    }

    private fun select(
        editor: Editor, st: MeowState, type: SelType,
        mark: Int, point: Int, expand: Boolean, push: Boolean = true,
    ) {
        val len = editor.document.textLength
        val m = mark.coerceIn(0, len)
        val p = point.coerceIn(0, len)
        val sm = editor.selectionModel
        if (push && sm.hasSelection()) {
            st.selectionHistory.addLast(sm.selectionStart to sm.selectionEnd)
            while (st.selectionHistory.size > 200) st.selectionHistory.removeFirst()
        }
        st.selType = type
        st.selExpand = expand
        editor.caretModel.moveToOffset(p)
        sm.setSelection(minOf(m, p), maxOf(m, p))
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        maybeBeacon(editor, st)
        ExpandHints.show(editor, st)
    }

    private fun cancel(editor: Editor, st: MeowState) {
        editor.selectionModel.removeSelection()
        st.selType = SelType.NONE
        st.selExpand = false
    }

    private fun cancelAll(editor: Editor, st: MeowState) {
        if (editor.caretModel.caretCount > 1) editor.caretModel.removeSecondaryCarets()
        cancel(editor, st)
    }

    private fun reverse(editor: Editor) {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) return
        val s = sm.selectionStart
        val e = sm.selectionEnd
        val newPoint = if (editor.caretModel.offset <= s) e else s
        editor.caretModel.moveToOffset(newPoint)
        sm.setSelection(s, e)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    // ------------------------------------------------------------- movement

    private fun moveChar(editor: Editor, st: MeowState, dx: Int) {
        if (st.selType == SelType.CHAR && editor.selectionModel.hasSelection()) {
            editor.caretModel.moveCaretRelatively(dx, 0, true, false, true)
        } else {
            cancel(editor, st)
            editor.caretModel.moveCaretRelatively(dx, 0, false, false, true)
        }
    }

    private fun moveLine(editor: Editor, st: MeowState, dy: Int) {
        if (st.selType == SelType.CHAR && editor.selectionModel.hasSelection()) {
            editor.caretModel.moveCaretRelatively(0, dy, true, false, true)
        } else {
            cancel(editor, st)
            editor.caretModel.moveCaretRelatively(0, dy, false, false, true)
        }
    }

    private fun moveExpand(editor: Editor, st: MeowState, dx: Int, dy: Int) {
        editor.caretModel.moveCaretRelatively(dx, dy, true, false, true)
        st.selType = SelType.CHAR
        st.selExpand = true
        maybeBeacon(editor, st)
    }

    // ------------------------------------------------------- words / symbols

    private fun pred(symbol: Boolean): (Char) -> Boolean =
        if (symbol) Things::isSymbolChar else Things::isWordChar

    private fun wordType(symbol: Boolean) = if (symbol) SelType.SYMBOL else SelType.WORD

    private fun nextWord(editor: Editor, st: MeowState, symbol: Boolean) =
        wordMotion(editor, st, symbol, st.takeCount(1))

    private fun backWord(editor: Editor, st: MeowState, symbol: Boolean) =
        wordMotion(editor, st, symbol, -st.takeCount(1)) // meow-back-word = next-thing with -N

    /**
     * meow-next-thing for word/symbol: when the current selection is the
     * matching (expand . type), the selection direction is normalized to the
     * motion FIRST (meow--direction-forward/-backward) — so after `w`, `e`
     * extends from the right end and `b` extends from the left end, anchored
     * at the opposite end (meow--make-selection keeps min/max of the original
     * region as the mark). Without a matching selection: fresh (select . type)
     * from point. No motion -> no selection change.
     */
    private fun wordMotion(editor: Editor, st: MeowState, symbol: Boolean, n: Int) {
        if (n == 0) return
        val text = editor.document.charsSequence
        val type = wordType(symbol)
        val sm = editor.selectionModel
        val extend = st.selExpand && st.selType == type && sm.hasSelection()
        val from = when {
            extend && n < 0 -> sm.selectionStart
            extend -> sm.selectionEnd
            else -> editor.caretModel.offset
        }
        val anchor = when {
            extend && n < 0 -> sm.selectionEnd
            extend -> sm.selectionStart
            else -> from
        }
        val target =
            if (n > 0) Words.nextEnd(text, from, n, pred(symbol))
            else Words.prevStart(text, from, -n, pred(symbol))
        if (target == from) return
        select(editor, st, type, anchor, target, expand = extend)
    }

    private fun markWord(editor: Editor, st: MeowState, symbol: Boolean) {
        val neg = st.takeCount(1) < 0
        val text = editor.document.charsSequence
        val b = Words.boundsAt(text, editor.caretModel.offset, pred(symbol))
            ?: run { hint(editor, "No word here"); return }
        val (s, e) = b
        if (neg) select(editor, st, wordType(symbol), e, s, expand = true)
        else select(editor, st, wordType(symbol), s, e, expand = true)
        pushSearch(st, Regex("\\b" + Regex.escape(text.subSequence(s, e).toString()) + "\\b"))
    }

    // ----------------------------------------------------------------- lines

    private fun line(editor: Editor, st: MeowState) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val n = st.takeCount(1)
        val sm = editor.selectionModel
        val lastLine = doc.lineCount - 1
        if (st.selType == SelType.LINE && sm.hasSelection()) {
            val caretLn = doc.getLineNumber(editor.caretModel.offset)
            if (backwardP(editor)) {
                val ln = (caretLn - abs(n)).coerceAtLeast(0)
                select(editor, st, SelType.LINE, mark(editor), doc.getLineStartOffset(ln), expand = true)
            } else {
                val ln = (caretLn + abs(n)).coerceAtMost(lastLine)
                select(editor, st, SelType.LINE, mark(editor), doc.getLineEndOffset(ln), expand = true)
            }
            return
        }
        val ln = doc.getLineNumber(editor.caretModel.offset)
        if (n < 0) {
            val startLn = (ln + n + 1).coerceAtLeast(0)
            select(editor, st, SelType.LINE, doc.getLineEndOffset(ln), doc.getLineStartOffset(startLn), expand = true)
        } else {
            val endLn = (ln + n - 1).coerceAtMost(lastLine)
            select(editor, st, SelType.LINE, doc.getLineStartOffset(ln), doc.getLineEndOffset(endLn), expand = true)
        }
    }

    private fun gotoLine(editor: Editor, st: MeowState) {
        val input = Messages.showInputDialog(editor.project, "Goto line:", "Meow", null) ?: return
        val doc = editor.document
        if (doc.textLength == 0) return
        val ln = ((input.trim().toIntOrNull() ?: return) - 1).coerceIn(0, doc.lineCount - 1)
        select(editor, st, SelType.LINE, doc.getLineStartOffset(ln), doc.getLineEndOffset(ln), expand = true)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    // ------------------------------------------------------------ find / till

    private fun findTill(editor: Editor, st: MeowState, ch: Char, till: Boolean) {
        val n = st.takeCount(1)
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        var found = -1
        if (n >= 0) {
            var from = if (till) caret + 1 else caret
            repeat(n) {
                found = text.indexOfChar(ch, from)
                if (found < 0) return@repeat
                from = found + 1
            }
            if (found < 0) { hint(editor, "char not found: $ch"); return }
            val target = if (till) found else found + 1
            select(editor, st, if (till) SelType.TILL else SelType.FIND, caret, target, expand = false)
        } else {
            var from = if (till) caret - 2 else caret - 1
            repeat(-n) {
                found = text.lastIndexOfChar(ch, from)
                if (found < 0) return@repeat
                from = found - 1
            }
            if (found < 0) { hint(editor, "char not found: $ch"); return }
            val target = if (till) found + 1 else found
            select(editor, st, if (till) SelType.TILL else SelType.FIND, caret, target, expand = false)
        }
        st.lastFind = ch
        st.lastIsTill = till
    }

    // ---------------------------------------------------------------- expand

    private fun expand(editor: Editor, st: MeowState, n: Int) {
        val text = editor.document.charsSequence
        val doc = editor.document
        val back = backwardP(editor)
        val caret = editor.caretModel.offset
        val target: Int = when (st.selType) {
            SelType.CHAR -> caret + if (back) -n else n
            SelType.WORD, SelType.SYMBOL -> {
                val p = pred(st.selType == SelType.SYMBOL)
                if (back) Words.prevStart(text, caret, n, p) else Words.nextEnd(text, caret, n, p)
            }
            SelType.LINE -> {
                val ln = doc.getLineNumber(caret)
                if (back) doc.getLineStartOffset((ln - n).coerceAtLeast(0))
                else doc.getLineEndOffset((ln + n).coerceAtMost(doc.lineCount - 1))
            }
            SelType.FIND, SelType.TILL -> {
                val ch = st.lastFind ?: return
                val till = st.selType == SelType.TILL
                var found = -1
                var from = if (back) (if (till) caret - 2 else caret - 1) else (if (till) caret + 1 else caret)
                repeat(n) {
                    found = if (back) text.lastIndexOfChar(ch, from) else text.indexOfChar(ch, from)
                    if (found < 0) return@repeat
                    from = if (back) found - 1 else found + 1
                }
                if (found < 0) return
                if (back) (if (till) found + 1 else found) else (if (till) found else found + 1)
            }
            else -> return
        }
        select(editor, st, st.selType, mark(editor), target, expand = st.selExpand)
    }

    // -------------------------------------------------------- search / visit

    private fun pushSearch(st: MeowState, re: Regex) {
        st.searchHistory.removeAll { it.pattern == re.pattern }
        st.searchHistory.addLast(re)
        while (st.searchHistory.size > 50) st.searchHistory.removeFirst()
    }

    private fun search(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        var re = st.searchHistory.lastOrNull()
        if (sm.hasSelection()) {
            val selText = editor.document.charsSequence
                .subSequence(sm.selectionStart, sm.selectionEnd).toString()
            if (selText.isNotEmpty() && (re == null || !re.matches(selText))) {
                re = Regex(Regex.escape(selText))
                pushSearch(st, re)
            }
        }
        if (re == null) { hint(editor, "No search pattern"); return }
        searchWith(editor, st, re, backward = st.takeCount(1) < 0 || backwardP(editor))
    }

    private fun visit(editor: Editor, st: MeowState) {
        val backward = st.takeCount(1) < 0
        val input = Messages.showInputDialog(editor.project, "Visit (regexp):", "Meow Visit", null)
        if (input.isNullOrEmpty()) return
        val re = try { Regex(input) } catch (_: Exception) { Regex(Regex.escape(input)) }
        pushSearch(st, re)
        searchWith(editor, st, re, backward)
    }

    private fun searchWith(editor: Editor, st: MeowState, re: Regex, backward: Boolean) {
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val m: MatchResult? = if (!backward) {
            re.find(text, caret) ?: re.find(text, 0)
        } else {
            var last: MatchResult? = null
            var cur = re.find(text, 0)
            while (cur != null && cur.range.last + 1 <= caret) { last = cur; cur = cur.next() }
            if (last == null) { // wrap to the end of the buffer
                var tail: MatchResult? = cur
                while (true) { val nx = tail?.next() ?: break; tail = nx }
                last = tail
            }
            last
        }
        if (m == null || m.value.isEmpty()) { hint(editor, "No match: ${re.pattern}"); return }
        if (!backward) select(editor, st, SelType.VISIT, m.range.first, m.range.last + 1, expand = false)
        else select(editor, st, SelType.VISIT, m.range.last + 1, m.range.first, expand = false)
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
            when {
                c in opens -> stack.addLast(i)
                c in closes -> {
                    val kind = closes.indexOf(c)
                    while (stack.isNotEmpty()) {
                        val o = stack.removeLast()
                        if (opens.indexOf(text[o]) == kind) {
                            if (o < s && i + 1 >= e && (best == null || i - o < best!!.close - best!!.open)) {
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

    private fun block(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        val text = editor.document.charsSequence
        val (s, e) =
            if (st.selType == SelType.BLOCK && sm.hasSelection()) sm.selectionStart to sm.selectionEnd
            else editor.caretModel.offset to editor.caretModel.offset
        val p = enclosingPair(text, s, e) ?: run { hint(editor, "No enclosing block"); return }
        select(editor, st, SelType.BLOCK, p.open, p.close + 1, expand = true)
    }

    private fun toBlock(editor: Editor, st: MeowState) {
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val p = enclosingPair(text, caret, caret) ?: run { hint(editor, "No enclosing block"); return }
        select(editor, st, SelType.BLOCK, caret, p.close + 1, expand = true)
    }

    // ------------------------------------------------------------------ join

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
            select(editor, st, SelType.JOIN, m, p, expand = true)
        } else {
            var nl = ln + 1
            while (nl <= doc.lineCount - 1 && blank(nl)) nl++
            if (nl > doc.lineCount - 1) return
            val m = doc.getLineEndOffset(ln)
            var p = doc.getLineStartOffset(nl)
            val eol = doc.getLineEndOffset(nl)
            while (p < eol && text[p].isWhitespace()) p++
            select(editor, st, SelType.JOIN, m, p, expand = true)
        }
    }

    private fun joinKill(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        val s = sm.selectionStart
        val e = sm.selectionEnd
        runWrite(editor, "Meow Join") {
            editor.document.deleteString(s, e)
            val text = editor.document.charsSequence
            val before = if (s > 0) text[s - 1] else '\n'
            val after = if (s < text.length) text[s] else '\n'
            // fixup-whitespace: keep one space, none at line edges or brackets
            if (before != '\n' && after != '\n' && !before.isWhitespace() && !after.isWhitespace() &&
                after !in ")]}.,;:" && before !in "([{"
            ) editor.document.insertString(s, " ")
            editor.caretModel.moveToOffset(s)
        }
        cancel(editor, st)
    }

    // ---------------------------------------------------------------- things

    private fun thingSelect(editor: Editor, st: MeowState, kind: Pending, ch: Char) {
        val off = editor.caretModel.offset
        val b = when (kind) {
            Pending.BOUNDS -> Things.bounds(editor, ch, off)
            else -> Things.inner(editor, ch, off)
        } ?: run { hint(editor, "No thing '$ch' here"); return }
        when (kind) {
            Pending.INNER, Pending.BOUNDS -> select(editor, st, SelType.TRANSIENT, b.start, b.end, expand = false)
            Pending.BEGIN -> select(editor, st, SelType.TRANSIENT, off, b.start, expand = false)
            Pending.END -> select(editor, st, SelType.TRANSIENT, off, b.end, expand = false)
            else -> {}
        }
    }

    // --------------------------------------------------------- grab / beacon

    private fun setGrabRange(editor: Editor, st: MeowState, s: Int, e: Int) {
        st.grab = editor.document.createRangeMarker(s, e)
        if (e > s) {
            val attrs = TextAttributes(null, GRAB_BG, null, null, Font.PLAIN)
            st.grabHighlighter = editor.markupModel.addRangeHighlighter(
                s, e, HighlighterLayer.SELECTION - 1, attrs, HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    fun clearGrab(editor: Editor, st: MeowState) {
        st.grab?.dispose()
        st.grab = null
        st.grabHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        st.grabHighlighter = null
    }

    private fun grab(editor: Editor, st: MeowState) {
        clearGrab(editor, st)
        val sm = editor.selectionModel
        val s = if (sm.hasSelection()) sm.selectionStart else editor.caretModel.offset
        val e = if (sm.hasSelection()) sm.selectionEnd else editor.caretModel.offset
        setGrabRange(editor, st, s, e)
        cancel(editor, st)
    }

    private fun syncGrab(editor: Editor, st: MeowState) {
        val sm = editor.selectionModel
        if (!sm.hasSelection()) { hint(editor, "meow-sync-grab needs a selection"); return }
        clearGrab(editor, st)
        setGrabRange(editor, st, sm.selectionStart, sm.selectionEnd)
        cancel(editor, st)
    }

    private fun swapGrab(editor: Editor, st: MeowState) {
        val g = st.grab
        val sm = editor.selectionModel
        if (g == null || !g.isValid) { hint(editor, "No grab"); return }
        if (!sm.hasSelection()) { hint(editor, "meow-swap-grab needs a selection"); return }
        val gs = g.startOffset
        val ge = g.endOffset
        val ss = sm.selectionStart
        val se = sm.selectionEnd
        if (maxOf(gs, ss) < minOf(ge, se) && !(gs == ss && ge == se)) {
            hint(editor, "Selection overlaps the grab"); return
        }
        val text = editor.document.charsSequence
        val grabText = text.subSequence(gs, ge).toString()
        val selText = text.subSequence(ss, se).toString()
        runWrite(editor, "Meow Swap Grab") {
            clearGrab(editor, st)
            if (gs <= ss) {
                editor.document.replaceString(ss, se, grabText)
                editor.document.replaceString(gs, ge, selText)
                val delta = selText.length - (ge - gs)
                setGrabRange(editor, st, gs, gs + selText.length)
                editor.caretModel.moveToOffset(ss + delta + grabText.length)
            } else {
                editor.document.replaceString(gs, ge, selText)
                editor.document.replaceString(ss, se, grabText)
                val delta = grabText.length - (se - ss)
                setGrabRange(editor, st, gs + delta, gs + delta + selText.length)
                editor.caretModel.moveToOffset(ss + grabText.length)
            }
            sm.removeSelection()
            st.selType = SelType.NONE
        }
    }

    private fun popGrab(editor: Editor, st: MeowState): Boolean {
        val g = st.grab ?: return false
        if (!g.isValid) return false
        val s = g.startOffset
        val e = g.endOffset
        clearGrab(editor, st)
        select(editor, st, SelType.TRANSIENT, s, e, expand = false)
        return true
    }

    private fun popSelection(editor: Editor, st: MeowState) {
        if (st.selectionHistory.isNotEmpty()) {
            val (s, e) = st.selectionHistory.removeLast()
            select(editor, st, SelType.TRANSIENT, s, e, expand = false, push = false)
        } else if (!popGrab(editor, st)) {
            hint(editor, "No previous selection")
        }
    }

    /**
     * BEACON: with a grab active, creating a selection inside it drops a
     * caret+selection on every similar range in the grab — native multiple
     * carets stand in for meow's kmacro replay.
     */
    private fun maybeBeacon(editor: Editor, st: MeowState) {
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

    // --------------------------------------------------------------- editing

    private fun clipboard(): String? =
        CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)

    private fun runWrite(editor: Editor, name: String, body: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(editor.project, name, "meow", Runnable { body() })
    }

    fun act(editor: Editor, ctx: DataContext?, id: String) {
        val action = ActionManager.getInstance().getAction(id)
            ?: run { hint(editor, "Unknown action: $id"); return }
        val dc = ctx ?: DataManager.getInstance().getDataContext(editor.contentComponent)
        val event = AnActionEvent.createEvent(action, dc, null, "MeowPlugin", ActionUiKind.NONE, null)
        ActionUtil.performAction(action, event)
    }

    private fun kill(editor: Editor, st: MeowState, ctx: DataContext?) {
        val sm = editor.selectionModel
        if (st.selType == SelType.JOIN && sm.hasSelection()) { joinKill(editor, st); return }
        if (sm.hasSelection()) {
            act(editor, ctx, IdeActions.ACTION_EDITOR_CUT)
            st.selType = SelType.NONE
            return
        }
        // fallback meow-C-k: kill to end of line, or the newline when at eol
        val doc = editor.document
        val caret = editor.caretModel.offset
        if (doc.textLength == 0) return
        val eol = doc.getLineEndOffset(doc.getLineNumber(caret))
        val end = if (caret == eol) (eol + 1).coerceAtMost(doc.textLength) else eol
        if (end > caret) {
            sm.setSelection(caret, end)
            act(editor, ctx, IdeActions.ACTION_EDITOR_CUT)
        }
    }

    private fun save(editor: Editor, ctx: DataContext?) {
        if (editor.selectionModel.hasSelection()) act(editor, ctx, IdeActions.ACTION_EDITOR_COPY)
    }

    private fun yank(editor: Editor, st: MeowState) {
        val clip = clipboard() ?: return
        runWrite(editor, "Meow Yank") {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) {
                val off = caret.offset
                editor.document.insertString(off, clip)
                caret.moveToOffset(off + clip.length)
            }
        }
    }

    private fun replace(editor: Editor, st: MeowState) {
        if (!editor.selectionModel.hasSelection()) return
        val clip = (clipboard() ?: return).trimEnd('\n')
        runWrite(editor, "Meow Replace") {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) {
                if (!caret.hasSelection()) continue
                val s = caret.selectionStart
                editor.document.replaceString(s, caret.selectionEnd, clip)
                caret.removeSelection()
                caret.moveToOffset(s + clip.length)
            }
        }
        st.selType = SelType.NONE
    }

    private fun delete(editor: Editor, st: MeowState) {
        runWrite(editor, "Meow Delete") {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) {
                if (caret.hasSelection()) {
                    editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                    caret.removeSelection()
                } else {
                    val o = caret.offset
                    if (o < editor.document.textLength) editor.document.deleteString(o, o + 1)
                }
            }
        }
        st.selType = SelType.NONE
    }

    private fun backwardDelete(editor: Editor, st: MeowState) {
        runWrite(editor, "Meow Backward Delete") {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) {
                if (caret.hasSelection()) {
                    editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                    caret.removeSelection()
                } else {
                    val o = caret.offset
                    if (o > 0) editor.document.deleteString(o - 1, o)
                }
            }
        }
        st.selType = SelType.NONE
    }

    private fun change(editor: Editor, st: MeowState) {
        runWrite(editor, "Meow Change") {
            for (caret in editor.caretModel.allCarets.sortedByDescending { it.offset }) {
                if (caret.hasSelection()) {
                    editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                    caret.removeSelection()
                } else {
                    // fallback meow-change-char
                    val o = caret.offset
                    if (o < editor.document.textLength && editor.document.charsSequence[o] != '\n') {
                        editor.document.deleteString(o, o + 1)
                    }
                }
            }
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun insert(editor: Editor, st: MeowState) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionStart)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun append(editor: Editor, st: MeowState) {
        for (caret in editor.caretModel.allCarets) {
            if (caret.hasSelection()) caret.moveToOffset(caret.selectionEnd)
            caret.removeSelection()
        }
        st.selType = SelType.NONE
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openBelow(editor: Editor, st: MeowState, ctx: DataContext?) {
        cancel(editor, st)
        act(editor, ctx, "EditorStartNewLine")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun openAbove(editor: Editor, st: MeowState, ctx: DataContext?) {
        cancel(editor, st)
        act(editor, ctx, "EditorStartNewLineBefore")
        Meow.setMode(editor, st, MeowMode.INSERT)
    }

    private fun undo(editor: Editor, st: MeowState, ctx: DataContext?) {
        cancel(editor, st)
        act(editor, ctx, IdeActions.ACTION_UNDO)
    }

    private fun repeatLast(editor: Editor, st: MeowState, ctx: DataContext?) {
        val keys = st.lastKeys
        if (keys.isEmpty()) return
        st.replaying = true
        try {
            for (k in keys) handleChar(editor, k, ctx)
        } finally {
            st.replaying = false
        }
    }

    /** Run an ~/.ideameowrc binding: an IDE action, or meow keys replayed
     *  through the engine (noremap bindings skip user nmaps while replaying). */
    fun runBinding(editor: Editor, st: MeowState, b: Rc.Binding, ctx: DataContext?) {
        val actionId = b.action
        if (actionId != null) {
            act(editor, ctx, actionId)
            return
        }
        val keys = b.keys ?: return
        if (st.replayDepth >= 8) {
            hint(editor, "ideameow: mapping recursion is too deep")
            return
        }
        val savedReplaying = st.replaying
        st.replaying = true // inner keys must not clobber the ' (repeat) unit
        st.replayDepth++
        if (!b.recursive) st.noremapDepth++
        try {
            for (k in keys) handleChar(editor, k, ctx)
        } finally {
            if (!b.recursive) st.noremapDepth--
            st.replayDepth--
            st.replaying = savedReplaying
        }
    }
}
