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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * The repeat transient — Emacs repeat-mode, ported (init.el's transient
 * repeat maps, repeat.el read from Emacs 30.2 source). Rc `repeat` groups
 * make multi-key entries tap-to-continue: dispatching any binding whose
 * TARGET is a group member arms the group (target identity, like the
 * repeat-map symbol property; the entering key needn't be a member —
 * repeat-check-key 'no), then member keys re-dispatch their targets and any
 * other key or ESC ends the run and keeps its normal meaning
 * (set-transient-map fall-through — never swallowed, no timeout).
 */
class RepeatSpec : MeowSpec() {
    /** A keypad nav entry plus a repeat group over the same targets; the
     *  members deliberately sit on `.`/`,` — meow's bounds/inner-of-thing —
     *  to pin that a live run shadows them and a finished run gives them back. */
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

    // ------------------------------------------------------------- parsing

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
                    "repeat nav . meow-frobnicate", // misspelled command
                    "repeat nav", // group and key but no target
                ),
            )
        assertEquals(2, c.errors.size)
        assertTrue(c.errors[0].contains("meow-frobnicate"))
    }

    fun `test given a repeat key that is not a single printable key then an error is collected`() {
        val c =
            Rc.parse(
                listOf(
                    "repeat nav ab meow-next", // two keys
                    "repeat nav <Space> meow-next", // SPC is the keypad key
                ),
            )
        assertEquals(2, c.errors.size)
    }

    fun `test given home rc repeat lines then they layer per key over the bundled group`() {
        givenRc("repeat error , meow-prev\nrepeat error e <action>(ShowErrorDescription)")
        val g = Rc.repeatGroups()["error"]!!
        assertEquals("GotoNextError", g['.']!!.action) // bundled default beneath
        assertEquals("meow-prev", g[',']!!.command) // the user override
        assertEquals("ShowErrorDescription", g['e']!!.action) // the user extension
    }

    fun `test given a repeat member bound to ignore then the key is given back`() {
        givenRc("repeat zoom 0 ignore")
        val g = Rc.repeatGroups()["zoom"]!!
        assertFalse(g.containsKey('0'))
        assertEquals("EditorIncreaseFontSize", g['i']!!.action) // the rest stays
    }

    fun `test the bundled default ideameowrc declares the init el repeat groups`() {
        // ported 1:1 from init.el's transient maps: flymake -> error,
        // diff-hl -> change, text-scale -> zoom, expreg -> expand
        val d = Rc.defaults().repeat
        assertEquals("GotoNextError", d["error"]!!['.']!!.action)
        assertEquals("GotoPreviousError", d["error"]!![',']!!.action)
        assertEquals("VcsShowNextChangeMarker", d["change"]!!['.']!!.action)
        assertEquals("VcsShowPrevChangeMarker", d["change"]!![',']!!.action)
        assertEquals(setOf('i', '=', 'o', '-', 'u', '0'), d["zoom"]!!.keys)
        assertEquals("EditorSelectWord", d["expand"]!!['.']!!.action)
        assertEquals("EditorUnSelectWord", d["expand"]!![',']!!.action)
    }

    fun `test given a repeat line edit then the reload button sees a change`() {
        // the floating button hashes the PARSED config — repeat groups are
        // part of it, so editing one must light the button up
        val home = FileUtil.createTempDirectory("meow-home", null)
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

    // ------------------------------------------------------------ dispatch

    fun `test given a keypad nav entry in a repeat group then tapping the members keeps walking`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn") // SPC t n -> meow-next, arms the nav group
        assertEquals(1, caretLine())
        whenKeys(".") // member: re-dispatches meow-next, re-arms
        assertEquals(2, caretLine())
        whenKeys(".")
        assertEquals(3, caretLine())
        whenKeys(",") // the other member walks back
        assertEquals(2, caretLine())
        thenMode(MeowMode.NORMAL)
    }

    fun `test given a normal key bound to a member target then it arms the same run`() {
        // membership is the TARGET, not the key that ran it — Emacs puts
        // repeat-map on the command symbol, so every binding of it arms
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys("j") // bundled-default j = meow-next, a nav member by identity
        assertEquals(1, caretLine())
        whenKeys(".")
        assertEquals(2, caretLine())
    }

    fun `test given a non-member key then the run ends and the key keeps its normal meaning`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertNotNull(st.repeatMap)
        whenKeys("w") // not a member: falls through to meow-mark-word
        thenSelection("two")
        assertNull(st.repeatMap)
    }

    fun `test given the run over then the member keys mean their normal commands again`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        whenKeys("x") // ends the run (meow-line)
        thenSelection("two")
        whenKeys(".") // meow-bounds-of-thing again, waiting for its thing key
        assertEquals(Pending.BOUNDS, st.pending)
        assertEquals(1, caretLine()) // and no nav happened
    }

    fun `test given escape then the run ends`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertNotNull(st.repeatMap)
        pressEsc()
        assertNull(st.repeatMap)
        whenKeys(".")
        assertEquals(Pending.BOUNDS, st.pending)
        assertEquals(1, caretLine())
    }

    fun `test given SPC during a run then the keypad still opens`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        whenKeys(" tn") // SPC is not a member: run ends, keypad works as ever
        assertEquals(2, caretLine())
        thenMode(MeowMode.NORMAL)
    }

    fun `test given a digit during a run then it falls through as a count`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertEquals(1, caretLine())
        whenKeys("2j") // 2 ends the run and counts the next command
        assertEquals(3, caretLine())
    }

    fun `test given a run then the armed keys are the group members`() {
        given("four lines", "<caret>one\ntwo\nthree\nfour")
        givenRc(navRc)
        whenKeys(" tn")
        assertEquals(setOf('.', ','), st.repeatMap?.keys)
        whenKeys("w")
        assertNull(st.repeatMap)
    }
}
