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

/** meow-left/right/next/prev and the -expand variants, counts, negative arg. */
class MovementSpec : MeowSpec() {

    fun `test given a caret when l then it moves right without selecting`() {
        given("plain text", "<caret>hello")
        whenKeys("l")
        thenCaretAt(1)
        thenNoSelection()
    }

    fun `test given a caret when h then it moves left`() {
        given("plain text", "he<caret>llo")
        whenKeys("h")
        thenCaretAt(1)
        thenNoSelection()
    }

    fun `test given two lines when j then caret moves to next line`() {
        given("two lines", "<caret>one\ntwo")
        whenKeys("j")
        assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
    }

    fun `test given a count when 2 l then caret moves two chars (digit argument)`() {
        given("plain text", "<caret>hello")
        whenKeys("2l")
        thenCaretAt(2)
        thenNoSelection()
    }

    fun `test given four lines when 3 j then caret moves three lines down`() {
        given("four lines", "<caret>a\nb\nc\nd")
        whenKeys("3j")
        assertEquals(3, doc.getLineNumber(ed.caretModel.offset))
    }

    fun `test given negative argument when - 2 j then caret moves two lines up`() {
        given("four lines", "a\nb\nc\n<caret>d")
        whenKeys("-2j")
        assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
    }

    fun `test given no selection when H then a char selection is created leftwards`() {
        given("plain text", "hel<caret>lo")
        whenKeys("H")
        thenSelection("l")
        thenSelType(SelType.CHAR)
        thenCaretAtSelectionStart()
    }

    fun `test given a char selection when h then the selection survives and extends (meow keeps char selections)`() {
        given("plain text", "hel<caret>lo")
        whenKeys("Hh")
        thenSelection("el")
        thenSelType(SelType.CHAR)
    }

    fun `test given a word selection when h then the selection is cancelled (only char selections survive)`() {
        given("plain text", "<caret>hello world")
        whenKeys("w")
        thenSelection("hello")
        whenKeys("h")
        thenNoSelection()
    }

    fun `test given L J then char selection extends right and down`() {
        given("two lines", "<caret>ab\ncd")
        whenKeys("LJ")
        thenSelType(SelType.CHAR)
        assertTrue(ed.selectionModel.hasSelection())
        thenCaretAtSelectionEnd()
    }

    fun `test given an undefined key in NORMAL then it is swallowed and types nothing`() {
        given("plain text", "<caret>hello")
        whenKeys("#%")
        thenText("hello")
    }
}
