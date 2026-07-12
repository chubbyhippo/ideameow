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

class EditingSpec : MeowSpec() {
    fun `test given a selection when i then INSERT starts at the selection beginning`() {
        given("word", "<caret>hello world")
        whenKeys("wi")
        thenMode(MeowMode.INSERT)
        thenCaretAt(0)
        thenNoSelection()
    }

    fun `test given a selection when a then INSERT starts at the selection end`() {
        given("word", "<caret>hello world")
        whenKeys("wa")
        thenMode(MeowMode.INSERT)
        thenCaretAt(5)
        thenNoSelection()
    }

    fun `test given no selection when i then INSERT starts at point (no cursor-position hack)`() {
        given("word", "he<caret>llo")
        whenKeys("i")
        thenMode(MeowMode.INSERT)
        thenCaretAt(2)
    }

    fun `test given INSERT mode then printable keys are not intercepted`() {
        given("word", "<caret>hello")
        whenKeys("i")
        assertFalse(
            "typed keys must reach the original handler in INSERT",
            Engine.handleChar(ed, 'z', null),
        )
    }

    fun `test given A then a line opens below and INSERT starts`() {
        given("one line", "ab<caret>cd")
        whenKeys("A")
        thenMode(MeowMode.INSERT)
        assertEquals("a newline was opened", 2, doc.lineCount)
        thenText("abcd\n")
    }

    fun `test given I then a line opens above and INSERT starts`() {
        given("one line", "ab<caret>cd")
        whenKeys("I")
        thenMode(MeowMode.INSERT)
        assertEquals(2, doc.lineCount)
        thenText("\nabcd")
    }

    fun `test given a selection when c then it is killed into INSERT (meow-change)`() {
        given("word", "<caret>hello world")
        whenKeys("wc")
        thenText(" world")
        thenMode(MeowMode.INSERT)
        thenCaretAt(0)
    }

    fun `test given no selection when c then the char at point is changed (meow-change-char fallback)`() {
        given("word", "a<caret>bc")
        whenKeys("c")
        thenText("ac")
        thenMode(MeowMode.INSERT)
    }

    fun `test given the caret on a newline when c then the lines join (change-char takes any char)`() {
        given("two lines", "ab<caret>\ncd")
        whenKeys("c")
        thenText("abcd")
        thenMode(MeowMode.INSERT)
    }

    fun `test given the caret at end of buffer when c then nothing happens, not even INSERT`() {
        given("word", "ab<caret>")
        whenKeys("c")
        thenText("ab")
        thenMode(MeowMode.NORMAL)
    }

    fun `test given U without a selection then nothing happens (undo-in-selection is region-gated)`() {
        given("chars", "<caret>abc")
        whenKeys("dU")
        thenText("bc")
    }

    fun `test given a selection when d then it is deleted without touching the clipboard`() {
        given("word", "<caret>hello world")
        givenClipboard("KEEP")
        whenKeys("wd")
        thenText(" world")
        thenClipboard("KEEP")
        thenMode(MeowMode.NORMAL)
    }

    fun `test given no selection when d then the char at point is deleted (delete-char fallback)`() {
        given("word", "a<caret>bc")
        whenKeys("d")
        thenText("ac")
    }

    fun `test given D then the char before point is deleted (meow-backward-delete)`() {
        given("word", "ab<caret>c")
        whenKeys("D")
        thenText("ac")
        thenCaretAt(1)
    }

    fun `test given a selection when s then it is killed to the clipboard (meow-kill)`() {
        given("word", "<caret>hello world")
        whenKeys("ws")
        thenText(" world")
        thenClipboard("hello")
    }

    fun `test given no selection when s then kill-line takes over (meow-C-k fallback)`() {
        given("two lines", "he<caret>llo\nworld")
        whenKeys("s")
        thenText("he\nworld")
        thenClipboard("llo")
    }

    fun `test given the caret at eol when s then the newline is killed (kill-line joins)`() {
        given("two lines", "he<caret>\nworld")
        whenKeys("s")
        thenText("heworld")
    }

    fun `test given a join selection when s then the lines join with a single space (fixup-whitespace)`() {
        given("indented continuation", "one\n  t<caret>wo")
        whenKeys("ms")
        thenText("one two")
        thenCaretAt(3)
    }

    fun `test given a join before a closing bracket then no space is inserted`() {
        given("hanging paren", "f(x\n  <caret>)")
        whenKeys("ms")
        thenText("f(x)")
    }

    fun `test given y then the selection is copied and cancelled (kill-ring-save deactivates the mark)`() {
        given("word", "<caret>hello world")
        whenKeys("wy")
        thenText("hello world")
        thenClipboard("hello")
        thenNoSelection()
        thenCaretAt(5)
    }

    fun `test given a line selection when y then the newline is copied and the caret lands past it`() {
        given("two lines", "o<caret>ne\ntwo")
        whenKeys("xy")
        thenText("one\ntwo")
        thenClipboard("one\n")
        thenNoSelection()
        thenCaretAt(4)
    }

    fun `test given x x then y then both lines are copied with the trailing newline`() {
        given("three lines", "o<caret>ne\ntwo\nthree")
        whenKeys("xxy")
        thenText("one\ntwo\nthree")
        thenClipboard("one\ntwo\n")
        thenNoSelection()
        thenCaretAt(8)
    }

    fun `test given a line selection when s then the whole line goes including its newline`() {
        given("three lines", "o<caret>ne\ntwo\nthree")
        whenKeys("xs")
        thenText("two\nthree")
        thenClipboard("one\n")
        thenCaretAt(0)
    }

    fun `test given a reversed line selection when s then the newline stays (backward selections kill as-is)`() {
        given("three lines", "one\nt<caret>wo\nthree")
        whenKeys("x;s")
        thenText("one\n\nthree")
        thenClipboard("two")
    }

    fun `test given the last line when s then there is no newline to take`() {
        given("two lines", "one\nt<caret>wo")
        whenKeys("xs")
        thenText("one\n")
        thenClipboard("two")
    }

    fun `test given p then the clipboard is inserted at point with the caret after it (meow-yank)`() {
        given("word", "<caret>hello")
        givenClipboard("XY")
        whenKeys("p")
        thenText("XYhello")
        thenCaretAt(2)
    }

    fun `test given r then the selection is replaced by the clipboard which stays intact (meow-replace)`() {
        given("word", "<caret>hello world")
        givenClipboard("XY")
        whenKeys("wr")
        thenText("XY world")
        thenClipboard("XY")
        thenNoSelection()
    }

    fun `test given r without a selection then nothing happens`() {
        given("word", "<caret>hello")
        givenClipboard("XY")
        whenKeys("r")
        thenText("hello")
    }

    fun `test given u then the selection is cancelled first (meow-undo)`() {
        given("word", "<caret>hello world")
        whenKeys("wu")
        thenNoSelection()
    }

    fun `test given x x then repeated u past the undo stack then nothing blows up`() {
        given("three lines", "<caret>one\ntwo\nthree")
        whenKeys("xx")
        whenKeys("uuuuuu")
        thenMode(MeowMode.NORMAL)
    }

    fun `test given quote then the last command repeats`() {
        given("chars", "<caret>abcdef")
        whenKeys("d")
        thenText("bcdef")
        whenKeys("'")
        thenText("cdef")
    }

    fun `test given quote after a two-key command then the whole unit repeats`() {
        given("markers", "<caret>xaxaxa")
        whenKeys("fa")
        thenSelection("xa")
        whenKeys("'")
        thenSelection("xa")
        assertEquals("repeat replayed f+a from the new point", 2, ed.selectionModel.selectionStart)
    }

    fun `test given quote after finding a quote char then the find repeats`() {
        given("quotes", "<caret>a'b'c")
        whenKeys("f'")
        thenSelection("a'")
        whenKeys("'")
        thenSelection("b'")
    }

    fun `test given a caret mid-word when upcase-word then the rest upcases and the caret moves to word end`() {
        given("mixed-case word", "he<caret>LLo world")
        whenCommand("upcase-word")
        thenText("heLLO world")
        thenCaretAt(5)
    }

    fun `test given a count when upcase-word then that many words upcase`() {
        given("three words", "<caret>hello world foo")
        whenKeys("2")
        whenCommand("upcase-word")
        thenText("HELLO WORLD foo")
        thenCaretAt(11)
    }

    fun `test given a negative count when upcase-word then the previous word upcases and the caret stays`() {
        given("two words", "hello <caret>world")
        whenKeys("-")
        whenCommand("upcase-word")
        thenText("HELLO world")
        thenCaretAt(6)
    }

    fun `test given a caret when downcase-word then the word downcases`() {
        given("upper words", "<caret>HELLO WORLD")
        whenCommand("downcase-word")
        thenText("hello WORLD")
        thenCaretAt(5)
    }

    fun `test given a caret mid-word when capitalize-word then the slice capitalizes as a fresh word`() {
        given("mixed-case word", "he<caret>LLo world")
        whenCommand("capitalize-word")
        thenText("heLlo world")
        thenCaretAt(5)
    }

    fun `test given a count when capitalize-word then each word capitalizes`() {
        given("mixed words", "<caret>heLLO WOrld")
        whenKeys("2")
        whenCommand("capitalize-word")
        thenText("Hello World")
        thenCaretAt(11)
    }

    fun `test given a selection when upcase-word then it upcases from the caret and deactivates it`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("upcase-word")
        thenText("hello WORLD")
        thenNoSelection()
        thenCaretAt(11)
    }

    fun `test given a caret when kill-word then the word kills to the clipboard`() {
        given("two words", "<caret>hello world")
        whenCommand("kill-word")
        thenText(" world")
        thenCaretAt(0)
        thenClipboard("hello")
    }

    fun `test given a negative count when kill-word then the previous word kills backward`() {
        given("two words", "hello world<caret>")
        whenKeys("-")
        whenCommand("kill-word")
        thenText("hello ")
        thenCaretAt(6)
        thenClipboard("world")
    }
}
