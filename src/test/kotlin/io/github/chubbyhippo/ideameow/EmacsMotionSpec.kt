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

class EmacsMotionSpec : MeowSpec() {
    fun `test given no selection when forward-char then the caret moves right without selecting`() {
        given("plain text", "<caret>hello")
        whenCommand("forward-char")
        thenCaretAt(1)
        thenNoSelection()
    }

    fun `test given no selection when backward-char then the caret moves left without selecting`() {
        given("plain text", "he<caret>llo")
        whenCommand("backward-char")
        thenCaretAt(1)
        thenNoSelection()
    }

    fun `test given no selection when next-line then the caret moves down without selecting`() {
        given("two lines", "<caret>one\ntwo")
        whenCommand("next-line")
        assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
        thenNoSelection()
    }

    fun `test given no selection when previous-line then the caret moves up without selecting`() {
        given("two lines", "one\nt<caret>wo")
        whenCommand("previous-line")
        assertEquals(0, doc.getLineNumber(ed.caretModel.offset))
        thenNoSelection()
    }

    fun `test given no selection when move-beginning-of-line then the caret goes to column zero`() {
        given("indented line", "hel<caret>lo world")
        whenCommand("move-beginning-of-line")
        thenCaretAt(0)
        thenNoSelection()
    }

    fun `test given no selection when move-end-of-line then the caret goes to eol`() {
        given("plain text", "he<caret>llo")
        whenCommand("move-end-of-line")
        thenCaretAt(5)
        thenNoSelection()
    }

    fun `test given no selection when forward-word then the caret lands at the end of the next word`() {
        given("comma separated", "<caret>word1, word2")
        whenCommand("forward-word")
        thenCaretAt(5)
        thenNoSelection()
    }

    fun `test given no selection when backward-word then the caret lands at the start of the word`() {
        given("two words", "hello world<caret>")
        whenCommand("backward-word")
        thenCaretAt(6)
        thenNoSelection()
    }

    fun `test given no selection when forward-sentence then the caret lands past the sentence`() {
        given("three sentences", "<caret>One. Two. Three.")
        whenCommand("forward-sentence")
        thenCaretAt(5)
        thenNoSelection()
    }

    fun `test given no selection when backward-sentence then the caret lands at the sentence start`() {
        given("three sentences", "One. Two. Thr<caret>ee.")
        whenCommand("backward-sentence")
        thenCaretAt(10)
        thenNoSelection()
    }

    fun `test given w then forward-char extends the selection one char forward`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("forward-char")
        thenSelection("hello ")
        thenSelType(SelType.CHAR)
        thenCaretAtSelectionEnd()
    }

    fun `test given w then backward-char shrinks the selection from its end`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("backward-char")
        thenSelection("hell")
    }

    fun `test given w then next-line extends the selection down`() {
        given("word then a second line", "<caret>hello\nworld")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("next-line")
        thenSelection("hello\nworld")
    }

    fun `test given w then move-end-of-line extends the selection to eol`() {
        given("three words", "<caret>hello brave world")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("move-end-of-line")
        thenSelection("hello brave world")
    }

    fun `test given caret mid-line when w then move-beginning-of-line extends the selection to bol`() {
        given("three words", "hello <caret>brave world")
        whenKeys("w")
        thenSelection("brave")
        whenCommand("move-beginning-of-line")
        thenSelection("hello ")
        thenCaretAtSelectionStart()
    }

    fun `test given w then forward-word extends the word selection (chains with e)`() {
        given("three words", "<caret>one two three")
        whenKeys("w")
        thenSelection("one")
        whenCommand("forward-word")
        thenSelection("one two")
        thenSelType(SelType.WORD)
        whenKeys("e")
        thenSelection("one two three")
    }

    fun `test given w then forward-sentence extends the selection through the next sentence`() {
        given("two sentences", "<caret>One. Two.")
        whenKeys("w")
        thenSelection("One")
        whenCommand("forward-sentence")
        thenSelection("One. ")
        whenCommand("forward-sentence")
        thenSelection("One. Two.")
    }

    fun `test given w then semicolon then forward-char shrinks from the start (reversed anchor)`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        thenCaretAtSelectionEnd()
        whenKeys(";")
        thenCaretAtSelectionStart()
        whenCommand("forward-char")
        thenSelection("ello")
        thenCaretAtSelectionStart()
    }

    fun `test given w then semicolon then backward-char extends past the start`() {
        given("leading padding then two words", " <caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenKeys(";")
        thenCaretAtSelectionStart()
        whenCommand("backward-char")
        thenSelection(" hello")
        thenCaretAtSelectionStart()
    }

    fun `test given a reversed line selection then previous-line extends further up`() {
        given("three lines", "one\ntwo\nth<caret>ree")
        whenKeys("x")
        whenKeys(";")
        thenCaretAtSelectionStart()
        whenCommand("previous-line")
        thenSelection("two\nthree")
    }

    fun `test given beacon carets when forward-char then every caret extends its own selection`() {
        given("repeats with identical trailing context", "<caret>foo. foo. foo.")
        whenKeys(",bG")
        givenCaretAt(0)
        whenKeys("w")
        thenCaretCount(3)
        whenCommand("forward-char")
        assertEquals(
            listOf(4, 9, 14),
            ed.caretModel.allCarets
                .map { it.offset }
                .sorted(),
        )
    }

    fun `test given no selection when beginning-of-buffer then the caret goes to point-min`() {
        given("two lines", "one\nt<caret>wo")
        whenCommand("beginning-of-buffer")
        thenCaretAt(0)
        thenNoSelection()
    }

    fun `test given no selection when end-of-buffer then the caret goes to point-max`() {
        given("two lines", "on<caret>e\ntwo")
        whenCommand("end-of-buffer")
        thenCaretAt(7)
        thenNoSelection()
    }

    fun `test given w then end-of-buffer extends the selection to point-max`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("end-of-buffer")
        thenSelection("hello world")
        thenCaretAtSelectionEnd()
    }

    fun `test given w then beginning-of-buffer extends the selection back to point-min`() {
        given("prefixed word", "ab <caret>hello")
        whenKeys("w")
        thenSelection("hello")
        whenCommand("beginning-of-buffer")
        thenSelection("ab ")
        thenCaretAtSelectionStart()
    }

    fun `test given a count when beginning-of-buffer then the caret lands at the next line start past that tenth`() {
        given("five ten-char lines", "<caret>0123456789\n0123456789\n0123456789\n0123456789\n0123456789")
        whenKeys("3")
        whenCommand("beginning-of-buffer")
        thenCaretAt(22)
        thenNoSelection()
    }

    fun `test given a count when end-of-buffer then the caret lands a tenth back at the next line start`() {
        given("five ten-char lines", "<caret>0123456789\n0123456789\n0123456789\n0123456789\n0123456789")
        whenKeys("3")
        whenCommand("end-of-buffer")
        thenCaretAt(44)
        thenNoSelection()
    }
}
