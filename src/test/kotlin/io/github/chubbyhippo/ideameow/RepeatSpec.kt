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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files

class RepeatSpec : MeowSpec() {
    private val navRc =
        """
        map <leader>tn meow-next
        repeat nav . meow-next
        repeat nav , meow-prev
        """.trimIndent()

    private fun caretLine() = doc.getLineNumber(ed.caretModel.offset)

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

    fun `test given repeat lines then named groups parse with their member targets`() {
        val c =
            Rc.parse(
                listOf(
                    "repeat nav . meow-next",
                    "repeat nav , meow-prev",
                    "repeat zoom i <action>(EditorIncreaseFontSize)",
                ),
            )
        assertEquals("meow-next", c.repeat["nav"]!!['.']!!.command)
        assertEquals("meow-prev", c.repeat["nav"]!![',']!!.command)
        assertEquals("EditorIncreaseFontSize", c.repeat["zoom"]!!['i']!!.action)
        assertTrue(c.errors.isEmpty())
    }

    fun `test given a repeat line with a bad target then an error is collected`() {
        val c =
            Rc.parse(
                listOf(
                    "repeat nav . meow-frobnicate",
                    "repeat nav",
                ),
            )
        assertEquals(2, c.errors.size)
        assertTrue(c.errors[0].contains("meow-frobnicate"))
    }

    fun `test given a repeat key that is not a single printable key then an error is collected`() {
        val c =
            Rc.parse(
                listOf(
                    "repeat nav ab meow-next",
                    "repeat nav <Space> meow-next",
                ),
            )
        assertEquals(2, c.errors.size)
    }

    fun `test given home rc repeat lines then they layer per key over the bundled group`() {
        givenRc("repeat error , meow-prev\nrepeat error e <action>(ShowErrorDescription)")
        val g = Rc.repeatGroups()["error"]!!
        assertEquals("GotoNextError", g['.']!!.action)
        assertEquals("meow-prev", g[',']!!.command)
        assertEquals("ShowErrorDescription", g['e']!!.action)
    }

    fun `test given a repeat member bound to ignore then the key is given back`() {
        givenRc("repeat zoom 0 ignore")
        val g = Rc.repeatGroups()["zoom"]!!
        assertFalse(g.containsKey('0'))
        assertEquals("EditorIncreaseFontSize", g['i']!!.action)
    }

    fun `test the bundled default ideameowrc declares the init el repeat groups`() {
        val d = Rc.defaults().repeat
        assertEquals("GotoNextError", d["error"]!!['.']!!.action)
        assertEquals("GotoPreviousError", d["error"]!![',']!!.action)
        assertEquals("VcsShowNextChangeMarker", d["change"]!!['.']!!.action)
        assertEquals("VcsShowPrevChangeMarker", d["change"]!![',']!!.action)
        assertEquals(setOf('i', '=', 'o', '-', 'u', '0'), d["zoom"]!!.keys)
        assertEquals("EditorSelectWord", d["expand"]!!['.']!!.action)
        assertEquals("EditorUnSelectWord", d["expand"]!![',']!!.action)
    }

    fun `test given the bundled rc then the tab repeat group cycles editor tabs`() {
        val d = Rc.defaults().repeat
        assertEquals("NextTab", d["tab"]!!['n']!!.action)
        assertEquals("PreviousTab", d["tab"]!!['p']!!.action)
        assertEquals("NextTab", d["tab"]!!['.']!!.action)
        assertEquals("PreviousTab", d["tab"]!![',']!!.action)
        assertEquals(setOf('n', 'p', '.', ','), d["tab"]!!.keys)
    }

    fun `test given a repeat line edit then the reload button sees a change`() {
        val home = Files.createTempDirectory("meow-home").toFile()
        val oldHome = System.getProperty("user.home")
        System.setProperty("user.home", home.path)
        try {
            Rc.rcFile().writeText("nmap Z ,b\n")
            Rc.load()
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Rc.rcFile())!!
            val docRc = FileDocumentManager.getInstance().getDocument(vf)!!
            WriteCommandAction.runWriteCommandAction(project) {
                docRc.setText("nmap Z ,b\nrepeat nav . meow-next\n")
            }
            assertFalse("a repeat-group change demands a reload", RcFileState.equalTo(docRc))
        } finally {
            System.setProperty("user.home", oldHome)
            Rc.setForTest(Rc.Config())
            home.deleteRecursively()
        }
    }

    fun `test given a keypad nav entry in a repeat group then tapping the members keeps walking`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertEquals(1, caretLine())
        whenKeys(".")
        assertEquals(2, caretLine())
        whenKeys(".")
        assertEquals(3, caretLine())
        whenKeys(",")
        assertEquals(2, caretLine())
        thenMode(MeowMode.NORMAL)
    }

    fun `test given a normal key bound to a member target then it arms the same run`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys("j")
        assertEquals(1, caretLine())
        whenKeys(".")
        assertEquals(2, caretLine())
    }

    fun `test given a run then a member tap continues after an editor switch`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertEquals(1, caretLine())
        st = MeowState()
        ed.putUserData(Meow.KEY, st)
        whenKeys(".")
        assertEquals(2, caretLine())
    }

    fun `test given a non-member key then the run ends and the key keeps its normal meaning`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertNotNull(Engine.repeatMap)
        whenKeys("w")
        thenSelection("two")
        assertNull(Engine.repeatMap)
    }

    fun `test given the run over then the member keys mean their normal commands again`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        whenKeys("x")
        thenSelection("two")
        whenKeys(".")
        assertEquals(Pending.BOUNDS, st.pending)
        assertEquals(1, caretLine())
    }

    fun `test given escape then the run ends`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertNotNull(Engine.repeatMap)
        pressEsc()
        assertNull(Engine.repeatMap)
        whenKeys(".")
        assertEquals(Pending.BOUNDS, st.pending)
        assertEquals(1, caretLine())
    }

    fun `test given SPC during a run then the keypad still opens`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        whenKeys(" tn")
        assertEquals(2, caretLine())
        thenMode(MeowMode.NORMAL)
    }

    fun `test given a digit during a run then it falls through as a count`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertEquals(1, caretLine())
        whenKeys("2j")
        assertEquals(3, caretLine())
    }

    fun `test given a run then the armed keys are the group members`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertEquals(setOf('.', ','), Engine.repeatMap?.keys)
        whenKeys("w")
        assertNull(Engine.repeatMap)
    }
}
