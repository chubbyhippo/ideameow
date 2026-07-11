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

class FindSearchSpec : MeowSpec() {
    fun `test given f X then selects from point through the char inclusive`() {
        given("marker text", "<caret>abcXdef")
        whenKeys("fX")
        thenSelection("abcX")
        thenSelType(SelType.FIND)
        thenCaretAtSelectionEnd()
    }

    fun `test given t X then selects up to but excluding the char`() {
        given("marker text", "<caret>abcXdef")
        whenKeys("tX")
        thenSelection("abc")
        thenSelType(SelType.TILL)
    }

    fun `test given w then f X then a fresh find selection runs from the word end through the char`() {
        given("comma separated", "w<caret>ord1, word2 word3")
        whenKeys("w")
        thenSelection("word1")
        whenKeys("f3")
        thenSelection(", word2 word3")
        thenSelType(SelType.FIND)
        thenCaretAtSelectionEnd()
    }

    fun `test given w then t X then the till selection stops before the char`() {
        given("comma separated", "w<caret>ord1, word2 word3")
        whenKeys("wt3")
        thenSelection(", word2 word")
        thenSelType(SelType.TILL)
    }

    fun `test given a count when 2 f a then the second occurrence is reached`() {
        given("repeating", "<caret>xaxaxa")
        whenKeys("2fa")
        thenSelection("xaxa")
    }

    fun `test given a find selection when digit then it expands to the next occurrence`() {
        given("repeating", "<caret>xaxaxa")
        whenKeys("fa1")
        thenSelection("xaxa")
        whenKeys("1")
        thenSelection("xaxaxa")
    }

    fun `test given the char is absent when f then nothing changes`() {
        given("plain", "<caret>hello")
        whenKeys("fZ")
        thenNoSelection()
        thenCaretAt(0)
    }

    fun `test given negative argument when - f then finds backward`() {
        given("repeating", "xabc<caret>def")
        whenKeys("-fa")
        thenSelection("abc")
        thenCaretAtSelectionStart()
    }

    fun `test given w then n repeats the pushed word search forward (meow-search)`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("w")
        whenKeys("n")
        thenSelection("foo")
        assertEquals(8, ed.selectionModel.selectionStart)
    }

    fun `test given repeated n then the search wraps at the end of the buffer`() {
        given("repeats", "<caret>foo bar foo")
        whenKeys("wnn")
        assertEquals(0, ed.selectionModel.selectionStart)
        thenSelection("foo")
    }

    fun `test given a reversed selection when n then the search goes backward`() {
        given("repeats", "foo bar <caret>foo bar foo")
        whenKeys("w")
        whenKeys(";")
        whenKeys("n")
        assertEquals(0, ed.selectionModel.selectionStart)
        thenSelection("foo")
        thenCaretAtSelectionStart()
    }

    fun `test given a selection that does not match the pattern when n then the selection text becomes the pattern`() {
        given("repeats", "foo <caret>bar foo bar")
        st.searchHistory.addLast(Regex("zzz"))
        whenKeys(",e")
        whenKeys("n")
        thenSelection("bar")
        assertEquals(12, ed.selectionModel.selectionStart)
    }

    fun `test given no pattern and no selection when n then nothing is selected`() {
        given("plain", "<caret>hello")
        whenKeys("n")
        thenNoSelection()
    }

    fun `test given visit with minibuffer input then the first match after point is selected`() {
        given("repeats", "<caret>alpha beta gamma beta")
        givenMinibufferAnswers("beta")
        whenKeys("v")
        thenSelection("beta")
        assertEquals(6, ed.selectionModel.selectionStart)
        thenSelType(SelType.VISIT)
    }

    fun `test given visit then n continues to the next match`() {
        given("repeats", "<caret>alpha beta gamma beta")
        givenMinibufferAnswers("beta")
        whenKeys("vn")
        assertEquals(17, ed.selectionModel.selectionStart)
    }
}
