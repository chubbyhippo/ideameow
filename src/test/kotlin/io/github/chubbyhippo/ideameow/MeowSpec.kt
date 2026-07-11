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

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * BDD base for the meow QWERTY specs. Platform fixtures are JUnit3-based, so
 * the Given/When/Then structure lives in a tiny DSL:
 *
 *   given ("a buffer", "hello <caret>world")
 *   whenKeys ("we")
 *   thenSelection ("world")
 *
 * Every behavior asserted here was cross-checked against meow-edit/meow's
 * source (docstrings and command bodies) — not against vim intuition.
 */
abstract class MeowSpec : BasePlatformTestCase() {
    protected lateinit var st: MeowState

    protected val ed get() = myFixture.editor
    protected val doc get() = ed.document

    override fun setUp() {
        super.setUp()
        // never read a developer's real ~/.ideameowrc during tests
        Rc.setForTest(Rc.Config())
    }

    override fun tearDown() {
        try {
            TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
            WhichKey.hide()
            Rc.setForTest(Rc.Config())
        } finally {
            super.tearDown()
        }
    }

    // ------------------------------------------------------------------ DSL

    protected fun given(
        @Suppress("UNUSED_PARAMETER") description: String,
        text: String,
    ) {
        myFixture.configureByText("spec.txt", text)
        st = MeowState()
        ed.putUserData(Meow.KEY, st)
    }

    protected fun givenRc(text: String) {
        Rc.setForTest(Rc.parse(text.lines()))
    }

    protected fun givenClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    protected fun givenMinibufferAnswers(answer: String) {
        TestDialogManager.setTestInputDialog { answer }
    }

    protected fun givenCaretAt(offset: Int) {
        ed.caretModel.moveToOffset(offset)
    }

    protected fun whenKeys(keys: String) {
        for (c in keys) Engine.handleChar(ed, c, null)
    }

    protected fun thenSelection(expected: String) = assertEquals("selected text", expected, ed.selectionModel.selectedText)

    protected fun thenNoSelection() = assertFalse("expected no selection", ed.selectionModel.hasSelection())

    protected fun thenCaretAt(offset: Int) = assertEquals("caret offset", offset, ed.caretModel.offset)

    protected fun thenCaretAtSelectionStart() =
        assertEquals("caret at selection start (reversed)", ed.selectionModel.selectionStart, ed.caretModel.offset)

    protected fun thenCaretAtSelectionEnd() =
        assertEquals("caret at selection end (forward)", ed.selectionModel.selectionEnd, ed.caretModel.offset)

    protected fun thenText(expected: String) = assertEquals("buffer text", expected, doc.text)

    protected fun thenMode(expected: MeowMode) = assertEquals("meow mode", expected, st.mode)

    protected fun thenSelType(expected: SelType) = assertEquals("selection type", expected, st.selType)

    protected fun thenClipboard(expected: String) =
        assertEquals(
            "clipboard",
            expected,
            CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor),
        )

    protected fun thenCaretCount(expected: Int) = assertEquals("caret count", expected, ed.caretModel.caretCount)
}
