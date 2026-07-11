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

class AvySpec : MeowSpec() {
    private fun timeout() = Avy.finishInput(ed, st)

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

    fun `test given S with input matching many places then labels select the jump target`() {
        given("repeats", "<caret>foo bar foo baz foo")
        whenKeys("S")
        whenKeys("fo")
        timeout()
        whenKeys("s")
        thenCaretAt(8)
        assertNull("session ends after the jump", st.avy)
    }

    fun `test given a single candidate then avy jumps immediately (avy-single-candidate-jump)`() {
        given("words", "<caret>alpha beta gamma")
        whenKeys("S")
        whenKeys("gam")
        timeout()
        thenCaretAt(11)
        assertNull(st.avy)
    }

    fun `test given no candidates then the session ends where it started`() {
        given("words", "<caret>alpha beta")
        whenKeys("S")
        whenKeys("zz")
        timeout()
        thenCaretAt(0)
        assertNull(st.avy)
        whenKeys("l")
        thenCaretAt(1)
    }

    fun `test given matching is case-insensitive (avy-case-fold-search)`() {
        given("mixed case", "<caret>Foo bar fOO")
        whenKeys("S")
        whenKeys("foo")
        timeout()
        whenKeys("s")
        thenCaretAt(8)
    }

    fun `test given an active selection then the avy jump extends it (avy-action-goto)`() {
        given("words", "<caret>hello world again")
        whenKeys("w")
        whenKeys("S")
        whenKeys("aga")
        timeout()
        thenSelection("hello world ")
        thenCaretAtSelectionEnd()
    }

    fun `test given a bad selection key then avy stays active (avy-handler-default)`() {
        given("repeats", "<caret>xx xx xx")
        whenKeys("S")
        whenKeys("xx")
        timeout()
        whenKeys("z")
        assertNotNull(st.avy)
        whenKeys("d")
        thenCaretAt(6)
    }

    fun `test given more candidates than keys then leading keys stay single and the last key hosts a subtree`() {
        given("ten es", "<caret>e e e e e e e e e e")
        whenKeys("S")
        whenKeys("e")
        timeout()
        whenKeys("l")
        assertNotNull(st.avy)
        whenKeys("s")
        thenCaretAt(18)
    }

    fun `test given escape during an avy session then it cancels in place`() {
        given("words", "<caret>foo foo foo")
        whenKeys("S")
        whenKeys("foo")
        timeout()
        assertNotNull(st.avy)
        pressEsc()
        assertNull(st.avy)
        thenCaretAt(0)
    }

    fun `test given Q then visible lines are labeled and a key jumps to that line`() {
        given("four lines", "one\ntwo\nthr<caret>ee\nfour")
        whenKeys("Q")
        assertNotNull(st.avy)
        whenKeys("f")
        thenCaretAt(14)
        assertNull(st.avy)
    }

    fun `test given Q then a digit switches to the goto-line number prompt`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenMinibufferAnswers("3")
        whenKeys("Q3")
        thenCaretAt(8)
        assertNull(st.avy)
    }

    fun `test the avy-subdiv distribution matches avy 0-5-0`() {
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1, 1, 1), Avy.subdiv(9, 9))
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1, 1, 2), Avy.subdiv(10, 9))
        assertEquals(listOf(1, 1, 1, 1, 9, 9, 9, 9, 9), Avy.subdiv(49, 9))
        assertEquals(listOf(9, 9, 9, 9, 9, 9, 9, 9, 9), Avy.subdiv(81, 9))
    }
}
