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

class SelectionSpec : MeowSpec() {
    fun `test given caret on a word when w then the word is marked and caret sits at its end`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        thenSelType(SelType.WORD)
        thenCaretAtSelectionEnd()
    }

    fun `test given caret between words when w then the next word is marked`() {
        given("gap between words", "hello <caret> world")
        whenKeys("w")
        thenSelection("world")
    }

    fun `test given a symbol with underscore when W then the whole symbol is marked`() {
        given("snake case", "<caret>foo_bar baz")
        whenKeys("W")
        thenSelection("foo_bar")
        thenSelType(SelType.SYMBOL)
    }

    fun `test given w then W distinction - w stops at underscore boundary chars`() {
        given("snake case", "<caret>foo_bar baz")
        whenKeys("w")
        thenSelection("foo")
    }

    fun `test given a bare e when pressed twice then it steps word by word (non-expandable)`() {
        given("three words", "<caret>one two three")
        whenKeys("e")
        thenSelection("one")
        whenKeys("e")
        thenSelection("two")
    }

    fun `test given words separated by punctuation when e e e then each selection is one bare word`() {
        given("comma separated", "<caret>word1, word2 word3")
        whenKeys("ee")
        thenSelection("word2")
        whenKeys("e")
        thenSelection("word3")
        thenCaretAtSelectionEnd()
    }

    fun `test given b b b from the end then each selection is one bare word`() {
        given("comma separated", "word1, word2 word3<caret>")
        whenKeys("b")
        thenSelection("word3")
        thenCaretAtSelectionStart()
        whenKeys("bb")
        thenSelection("word1")
    }

    fun `test given e then b then the same word is re-selected backward`() {
        given("comma separated", "<caret>word1, word2 word3")
        whenKeys("eb")
        thenSelection("word1")
        thenCaretAtSelectionStart()
    }

    fun `test given a selection of another type when e then the history restarts at the cancel`() {
        given("two lines", "<caret>hello world\nnext line")
        whenKeys("x")
        thenSelection("hello world")
        whenKeys("e")
        thenSelection("next")
        whenKeys("z")
        thenNoSelection()
        thenCaretAt(11)
    }

    fun `test given w first when e then the word selection extends (meow expand-word rule)`() {
        given("three words", "<caret>one two three")
        whenKeys("we")
        thenSelection("one two")
        whenKeys("e")
        thenSelection("one two three")
    }

    fun `test given w then b extends the selection backward anchored at the word end`() {
        given("three words", "one t<caret>wo three")
        whenKeys("w")
        thenSelection("two")
        whenKeys("b")
        thenSelection("one two")
        thenCaretAtSelectionStart()
    }

    fun `test given w b then e re-normalizes forward and extends to the right`() {
        given("three words", "one t<caret>wo three")
        whenKeys("wbe")
        thenSelection("one two three")
        thenCaretAtSelectionEnd()
    }

    fun `test given W then B extends the symbol selection backward`() {
        given("symbols", "foo_a bar_b<caret> baz_c")
        whenKeys("W")
        thenSelection("bar_b")
        whenKeys("B")
        thenSelection("foo_a bar_b")
        thenCaretAtSelectionStart()
    }

    fun `test given caret at end when b then selects back to word beginning`() {
        given("two words", "hello world<caret>")
        whenKeys("b")
        thenSelection("world")
        thenCaretAtSelectionStart()
    }

    fun `test given negative argument when - e then selects backward like b`() {
        given("two words", "hello<caret> world")
        whenKeys("-e")
        thenSelection("hello")
        thenCaretAtSelectionStart()
    }

    fun `test given E and B then symbol variants honor underscores`() {
        given("snake case", "<caret>foo_bar baz")
        whenKeys("E")
        thenSelection("foo_bar")
        thenSelType(SelType.SYMBOL)
    }

    fun `test given x then the current line is selected without the newline`() {
        given("two lines", "li<caret>ne one\nline two")
        whenKeys("x")
        thenSelection("line one")
        thenSelType(SelType.LINE)
        thenCaretAtSelectionEnd()
    }

    fun `test given a line selection when x again then it extends one line down`() {
        given("three lines", "<caret>one\ntwo\nthree")
        whenKeys("xx")
        thenSelection("one\ntwo")
    }

    fun `test given a reversed line selection when x then it extends upward`() {
        given("three lines", "one\ntwo\nth<caret>ree")
        whenKeys("x;x")
        thenSelection("two\nthree")
        thenCaretAtSelectionStart()
    }

    fun `test given a selection then expand hints overlay the text without inserting inlays`() {
        given("three words", "<caret>hello world again")
        whenKeys("w")
        assertTrue(
            "no inline inlays may shift the text",
            ed.inlayModel.getInlineElementsInRange(0, doc.textLength).isEmpty(),
        )
        assertNotNull("hint canvas is painted above the editor", st.hintOverlay)
        assertSame(ed.contentComponent, st.hintOverlay!!.parent)
        whenKeys("g")
        assertNull(st.hintOverlay)
    }

    fun `test given a find selection when the target char sits at the caret then the first hint marks it`() {
        given("chars", "<caret>aXX")
        whenKeys("fX")
        assertNotNull("a digit hint must paint over the X at the caret", st.hintOverlay)
    }

    fun `test given digits after w then the selection expands by that many words`() {
        given("five words", "<caret>one two three four five")
        whenKeys("w2")
        thenSelection("one two three")
    }

    fun `test given 0 after a word mark then the selection expands by ten units`() {
        given("twelve words", "<caret>a b c d e f g h i j k l")
        whenKeys("w0")
        thenSelection("a b c d e f g h i j k")
    }

    fun `test given digits after x then the selection expands by lines`() {
        given("three lines", "<caret>one\ntwo\nthree")
        whenKeys("x2")
        thenSelection("one\ntwo\nthree")
    }

    fun `test given a reversed selection when digit then it expands backward`() {
        given("three lines", "one\ntwo\nthr<caret>ee")
        whenKeys("x;1")
        thenSelection("two\nthree")
    }

    fun `test given semicolon then point and mark swap (meow-reverse)`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenCaretAtSelectionEnd()
        whenKeys(";")
        thenSelection("hello")
        thenCaretAtSelectionStart()
        whenKeys(";")
        thenCaretAtSelectionEnd()
    }

    fun `test given goto line via minibuffer then that line is selected (meow-goto-line expands line selection)`() {
        given("three lines", "<caret>one\ntwo\nthree")
        givenMinibufferAnswers("2")
        whenKeys("X")
        thenSelection("two")
        thenSelType(SelType.LINE)
    }

    fun `test given Q then goto-line as well (QWERTY binds both Q and X)`() {
        given("three lines", "<caret>one\ntwo\nthree")
        givenRc("nmap Q meow-goto-line")
        givenMinibufferAnswers("3")
        whenKeys("Q")
        thenSelection("three")
    }

    fun `test given a selection history when z then the previous selection is restored with its type`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        whenKeys("x")
        whenKeys("z")
        thenSelection("hello")
        thenSelType(SelType.WORD)
        thenCaretAtSelectionEnd()
    }

    fun `test given w then z then the caret returns to where the chain started (null placeholder)`() {
        given("two words", "he<caret>llo world")
        whenKeys("w")
        thenSelection("hello")
        whenKeys("z")
        thenNoSelection()
        thenCaretAt(2)
    }

    fun `test given g then the selection history is cleared (meow--cancel-selection)`() {
        given("two words", "<caret>hello world")
        whenKeys("wxg")
        whenKeys("z")
        thenNoSelection()
    }

    fun `test given a digit expand then the selection is demoted to select type`() {
        given("five words", "<caret>one two three four five")
        whenKeys("w2")
        thenSelection("one two three")
        whenKeys("e")
        thenSelection("four")
    }

    fun `test given x 2 then x re-selects the current line instead of extending`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        whenKeys("x2")
        thenSelection("one\ntwo\nthree")
        whenKeys("x")
        thenSelection("three")
    }

    fun `test given no history but a grab when z then the grab becomes the selection (meow-pop-grab fallback)`() {
        given("two words", "<caret>hello world")
        whenKeys("wG")
        st.selectionHistory.clear()
        whenKeys("z")
        thenSelection("hello")
        assertNull("grab is consumed by pop", st.grab)
    }

    fun `test given g then the selection is cancelled`() {
        given("two words", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenKeys("g")
        thenNoSelection()
    }
}
