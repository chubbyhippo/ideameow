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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx

class ModesKeypadSpec : MeowSpec() {
    private fun pressEsc() {
        val noop =
            object : EditorActionHandler() {
                override fun doExecute(
                    editor: Editor,
                    caret: Caret?,
                    dataContext: DataContext?,
                ) {}
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
        whenKeys("l")
        thenCaretAt(1)
    }

    fun `test given a read-only document then all motions work and the modify commands are inert`() {
        given("two lines", "<caret>one\ntwo")
        doc.setReadOnly(true)
        try {
            whenKeys("j")
            assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
            whenKeys("kw")
            thenSelection("one")
            whenKeys("s")
            thenText("one\ntwo")
            thenSelection("one")
            whenKeys("y")
            thenClipboard("one")
            whenKeys("d")
            whenKeys("p")
            thenText("one\ntwo")
            thenMode(MeowMode.NORMAL)
        } finally {
            doc.setReadOnly(false)
        }
    }

    private fun fireKeypadAction() {
        val action = ActionManager.getInstance().getAction("Ideameow.Keypad")
        ActionManagerEx.getInstanceEx().tryToExecute(action, null, ed.contentComponent, null, true)
    }

    fun `test given INSERT when the keypad action fires then a keypad command returns to INSERT`() {
        given("word", "ab<caret>cd")
        givenRc("map <leader>zz <action>(EditorLeft)")
        whenKeys("i")
        thenMode(MeowMode.INSERT)
        assertFalse("INSERT uses a bar cursor", ed.settings.isBlockCursor)
        fireKeypadAction()
        thenMode(MeowMode.KEYPAD)
        assertTrue("KEYPAD uses a block cursor", ed.settings.isBlockCursor)
        whenKeys("zz")
        thenMode(MeowMode.INSERT)
        assertFalse("back to INSERT's bar cursor", ed.settings.isBlockCursor)
        thenCaretAt(1)
    }

    fun `test given INSERT when the keypad action then escape then back to INSERT`() {
        given("word", "<caret>hello")
        whenKeys("i")
        fireKeypadAction()
        thenMode(MeowMode.KEYPAD)
        pressEsc()
        thenMode(MeowMode.INSERT)
    }

    fun `test given NORMAL when the keypad action fires then KEYPAD round-trips to NORMAL`() {
        given("word", "<caret>hello")
        fireKeypadAction()
        thenMode(MeowMode.KEYPAD)
        pressEsc()
        thenMode(MeowMode.NORMAL)
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
        whenKeys("i")
        assertFalse("INSERT uses a bar cursor", ed.settings.isBlockCursor)
        pressEsc()
        assertTrue("NORMAL uses a block cursor", ed.settings.isBlockCursor)
    }

    fun `test given editors without meow state then keys pass through untouched`() {
        given("word", "<caret>hello")
        ed.putUserData(Meow.KEY, null)
        assertFalse(Engine.handleChar(ed, 'w'))
    }

    fun `test given an rc key bound to an IDE action then the action performs`() {
        given("word", "ab<caret>cd")
        givenRc("nmap Z <action>(EditorLeft)")
        whenKeys("Z")
        thenCaretAt(1)
    }

    fun `test given a keypad entry bound to an IDE action then it performs and KEYPAD exits`() {
        given("word", "ab<caret>cd")
        givenRc("map <leader>zz <action>(EditorLeft)")
        whenKeys(" zz")
        thenMode(MeowMode.NORMAL)
        thenCaretAt(1)
    }

    fun `test given SPC i d then action-id tracking toggles and reports performed action ids`() {
        given("word", "ab<caret>cd")
        try {
            assertFalse("tracking starts off", TrackActionIds.enabled)
            whenKeys(" id")
            thenMode(MeowMode.NORMAL)
            assertTrue("first press turns tracking on", TrackActionIds.enabled)
            givenRc("nmap Z <action>(EditorLeft)")
            whenKeys("Z")
            assertEquals("EditorLeft", TrackActionIds.lastTrackedId)
            whenKeys(" id")
            assertFalse("second press turns tracking off", TrackActionIds.enabled)
            assertNull(TrackActionIds.lastTrackedId)
            whenKeys(" id")
            assertTrue("SPC i d keeps toggling: a third press is on again", TrackActionIds.enabled)
        } finally {
            TrackActionIds.enabled = false
            TrackActionIds.lastTrackedId = null
            TrackActionIds.expire()
        }
    }
}
