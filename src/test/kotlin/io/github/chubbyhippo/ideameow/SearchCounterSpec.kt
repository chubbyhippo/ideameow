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

class SearchCounterSpec : MeowSpec() {
    fun `test given repeated words when w then the other occurrences are highlighted with a counter overlay`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("w")
        thenSelection("foo")
        assertEquals(2, state.searchMatchHighlighters.size)
        assertNotNull("meow's own search indicator is painted above the editor", state.searchCounterOverlay)
        assertSame(ed.contentComponent, state.searchCounterOverlay!!.parent)
    }

    fun `test given a unique word when w then no highlight or counter appears`() {
        given("three words", "<caret>hello world again")
        whenKeys("w")
        thenSelection("hello")
        assertTrue(state.searchMatchHighlighters.isEmpty())
        assertNull(state.searchCounterOverlay)
    }

    fun `test given a search counter shown when g then it is cleared`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("w")
        assertNotNull(state.searchCounterOverlay)
        whenKeys("g")
        assertTrue(state.searchMatchHighlighters.isEmpty())
        assertNull(state.searchCounterOverlay)
    }

    fun `test given a search counter shown when insert is entered then it is cleared`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("w")
        assertNotNull(state.searchCounterOverlay)
        whenKeys("c")
        assertTrue(state.searchMatchHighlighters.isEmpty())
        assertNull(state.searchCounterOverlay)
    }

    fun `test given a plain motion after a word selection then the counter is cleared`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("w")
        assertNotNull(state.searchCounterOverlay)
        whenKeys("l")
        assertTrue(state.searchMatchHighlighters.isEmpty())
        assertNull(state.searchCounterOverlay)
    }

    fun `test given a symbol word selected twice when W then the match count updates`() {
        given("dollar symbols", $$"$<caret>foo bar $foo baz $foo")
        whenKeys("W")
        thenSelection($$"$foo")
        assertEquals(2, state.searchMatchHighlighters.size)
        assertNotNull(state.searchCounterOverlay)
    }

    fun `test given a meow-search match when n then the counter reflects the buffer occurrences`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("wn")
        thenSelection("foo")
        assertEquals(2, state.searchMatchHighlighters.size)
        assertNotNull(state.searchCounterOverlay)
    }

    fun `test given visit with minibuffer input then the counter reflects the buffer occurrences`() {
        given("repeats", "<caret>alpha beta gamma beta")
        givenMinibufferAnswers("beta")
        whenKeys("v")
        thenSelection("beta")
        assertEquals(1, state.searchMatchHighlighters.size)
        assertNotNull(state.searchCounterOverlay)
    }

    fun `test given a word that is also a substring of longer words then only whole-word matches are counted`() {
        given("substring", "<caret>go together, go home, goto, mango, ago, go")
        whenKeys("w")
        thenSelection("go")
        assertEquals(2, state.searchMatchHighlighters.size)
        assertNotNull(state.searchCounterOverlay)
    }

    fun `test given n after a word selection then it keeps stopping on whole-word matches only, matching the counter`() {
        given("substring", "<caret>go together, go home, goto, mango, ago, go")
        whenKeys("w")
        assertEquals(2, state.searchMatchHighlighters.size)
        whenKeys("n")
        thenSelection("go")
        assertEquals(2, state.searchMatchHighlighters.size)
    }
}
