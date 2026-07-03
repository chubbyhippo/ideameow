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

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx

/** State transitions: INSERT/NORMAL/MOTION/KEYPAD, escape, keypad dispatch. */
class ModesKeypadSpec : MeowSpec() {

    private fun pressEsc() {
        val noop = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {}
        }
        MeowEscapeHandler(noop).execute(ed, null, (ed as EditorEx).dataContext)
    }

    fun `test given INSERT when escape then back to NORMAL`() {
        given("word", "<caret>hello")
        whenKeys("i")
        thenMode(MeowMode.INSERT)
        pressEsc()
        thenMode(MeowMode.NORMAL)
    }

    fun `test given beacon carets in NORMAL when escape then they collapse`() {
        given("repeats", "<caret>foo bar foo")
        whenKeys(",bG")
        givenCaretAt(0)
        whenKeys("w")
        thenCaretCount(2)
        pressEsc()
        thenCaretCount(1)
        thenMode(MeowMode.NORMAL)
    }

    fun `test given a pending find when escape then the pending key is dropped`() {
        given("word", "<caret>hello")
        whenKeys("f")
        assertNotNull(st.pending)
        pressEsc()
        assertNull(st.pending)
        whenKeys("l") // 'l' must act as a motion again, not as the find target
        thenCaretAt(1)
    }

    fun `test given a read-only document then keys behave like MOTION`() {
        given("two lines", "<caret>one\ntwo")
        doc.setReadOnly(true)
        try {
            whenKeys("j")
            assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
            whenKeys("w") // selection commands are swallowed in MOTION
            thenNoSelection()
            thenText("one\ntwo")
        } finally {
            doc.setReadOnly(false)
        }
    }

    fun `test given SPC then KEYPAD opens and a digit becomes the count for the next command`() {
        given("four lines", "<caret>a\nb\nc\nd")
        whenKeys(" ")
        thenMode(MeowMode.KEYPAD)
        whenKeys("3")
        thenMode(MeowMode.NORMAL)
        whenKeys("j")
        assertEquals(3, doc.getLineNumber(ed.caretModel.offset))
    }

    fun `test given SPC x then the keypad keeps collecting the prefix`() {
        given("word", "<caret>hello")
        whenKeys(" x")
        thenMode(MeowMode.KEYPAD)
        assertEquals("x", st.keypad.toString())
    }

    fun `test given an undefined keypad sequence then KEYPAD exits back to NORMAL`() {
        given("word", "<caret>hello")
        whenKeys(" x~")
        thenMode(MeowMode.NORMAL)
        thenText("hello")
    }

    fun `test given KEYPAD when escape then back to NORMAL without dispatch`() {
        given("word", "<caret>hello")
        whenKeys(" x")
        pressEsc()
        thenMode(MeowMode.NORMAL)
        thenText("hello")
    }

    fun `test given NORMAL then a block cursor, given INSERT then a bar cursor`() {
        given("word", "<caret>hello")
        // given() attaches state without going through the factory listener,
        // so drive the mode switches and observe the cursor shape they set
        whenKeys("i")
        assertFalse("INSERT uses a bar cursor", ed.settings.isBlockCursor)
        pressEsc()
        assertTrue("NORMAL uses a block cursor", ed.settings.isBlockCursor)
    }

    fun `test given editors without meow state then keys pass through untouched`() {
        given("word", "<caret>hello")
        ed.putUserData(Meow.KEY, null)
        assertFalse(Engine.handleChar(ed, 'w', null))
    }
}
