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

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestActionEvent
import java.io.File

class RcSpec : MeowSpec() {
    fun `test given an action mapping then it parses into a normal override`() {
        val c = Rc.parse(listOf("nmap S <action>(AceAction)"))
        assertEquals("AceAction", c.normal['S']!!.action)
        assertTrue(c.errors.isEmpty())
    }

    fun `test given a key-sequence mapping then it parses as replay keys`() {
        val c = Rc.parse(listOf("nmap Z ,b"))
        assertEquals(",b", c.normal['Z']!!.keys)
        assertTrue(c.normal['Z']!!.recursive)
    }

    fun `test given nnoremap then the binding is non-recursive`() {
        val c = Rc.parse(listOf("nnoremap Z ,b"))
        assertFalse(c.normal['Z']!!.recursive)
    }

    fun `test given a meow command name then it parses into a command binding`() {
        val c = Rc.parse(listOf("nmap n meow-mark-word", "nmap d ignore", "nmap Z repeat"))
        assertEquals("meow-mark-word", c.normal['n']!!.command)
        assertEquals("ignore", c.normal['d']!!.command)
        assertEquals("repeat", c.normal['Z']!!.command)
        assertTrue(c.errors.isEmpty())
    }

    fun `test given mmap then the binding lands in the motion map`() {
        val c = Rc.parse(listOf("mmap n meow-next", "mnoremap e k"))
        assertEquals("meow-next", c.motion['n']!!.command)
        assertEquals("k", c.motion['e']!!.keys)
        assertFalse(c.motion['e']!!.recursive)
        assertTrue(c.normal.isEmpty())
        assertTrue(c.errors.isEmpty())
    }

    fun `test given an unknown meow command then an error is collected`() {
        val c = Rc.parse(listOf("nmap Z meow-frobnicate"))
        assertEquals(1, c.errors.size)
        assertTrue(c.errors[0].contains("meow-frobnicate"))
    }

    fun `test given leader mappings and descriptions then the keypad table extends`() {
        val c =
            Rc.parse(
                listOf(
                    "map <leader>gd <action>(GotoDeclaration)",
                    "desc <leader>g goto things",
                ),
            )
        assertEquals("GotoDeclaration", c.keypad["gd"]!!.action)
        assertEquals("goto things", c.keypadDesc["g"])
        Rc.setForTest(c)
        assertEquals("GotoDeclaration", Rc.keypad()["gd"]!!.action)
        assertEquals("RecentFiles", Rc.keypad()["bb"]!!.action)
    }

    fun `test given a parameterized action then the whole serialized command is kept`() {
        val id = "com.example.showView(com.example.viewId=com.example.SomeView,com.example.focus=true)"
        val c = Rc.parse(listOf("map <leader>bj <action>($id)"))
        assertEquals(id, c.keypad["bj"]!!.action)
        assertTrue(c.errors.isEmpty())
    }

    fun `test given the ideavimrc WhichKeyDesc let syntax then descriptions parse`() {
        val c = Rc.parse(listOf("""let g:WhichKeyDesc_leader_x = "<leader>x C-x files/buffers""""))
        assertEquals("C-x files/buffers", c.keypadDesc["x"])
        assertTrue(c.errors.isEmpty())
    }

    fun `test given set lines then which-key options apply and vim options are ignored`() {
        val c =
            Rc.parse(
                listOf(
                    "set nowhich-key",
                    "set timeoutlen=400",
                    "set clipboard+=unnamedplus",
                    "let mapleader=\" \"",
                ),
            )
        assertEquals(false, c.whichKey)
        assertEquals(400, c.whichKeyDelayMs)
        assertTrue(c.errors.isEmpty())
    }

    fun `test which-key settings layer user over bundled defaults`() {
        assertTrue(Rc.whichKeyEnabled())
        assertEquals(300, Rc.whichKeyDelayMs())
        givenRc("set nowhich-key\nset timeoutlen=150")
        assertFalse(Rc.whichKeyEnabled())
        assertEquals(150, Rc.whichKeyDelayMs())
    }

    fun `test given a trailing comment then it is stripped from the line`() {
        val c =
            Rc.parse(
                listOf(
                    "nmap S <action>(AceAction)   \" jump anywhere",
                    "map <leader>zz ,b            \" select the buffer",
                ),
            )
        assertEquals("AceAction", c.normal['S']!!.action)
        assertEquals(",b", c.keypad["zz"]!!.keys)
        assertTrue(c.errors.isEmpty())
    }

    fun `test the bundled default ideameowrc defines the whole keymap`() {
        val d = Rc.defaults()
        assertTrue("bundled default must parse clean, got: ${d.errors}", d.errors.isEmpty())
        for ((key, cmd) in QWERTY) {
            if (key == 'Q') continue
            assertEquals("bundled layout line for '$key'", cmd, d.normal[key]?.command)
        }
        assertEquals("avy-goto-line", d.normal['Q']?.command)
        assertEquals("avy-goto-char-timer", d.normal['S']?.command)
        assertEquals("meow-next", d.motion['j']?.command)
        assertEquals("meow-prev", d.motion['k']?.command)
        assertEquals("RecentFiles", d.keypad["bb"]?.action)
        assertEquals("Switcher", d.keypad[" "]?.action)
        assertEquals("Ideameow.EditRc", d.keypad["cm"]?.action)
        assertEquals("Ideameow.ReloadRc", d.keypad["cM"]?.action)
        assertEquals("Ideameow.TrackActionIds", d.keypad["id"]?.action)
        assertTrue("keypad table + ported leader groups", d.keypad.size > 150)
    }

    fun `test given no home rc then the first SPC c m seeds it with the full bundled keymap`() {
        val f = File.createTempFile("ideameowrc-spec", null)
        try {
            f.delete()
            EditRcAction.seedIfMissing(f)
            val seeded = Rc.parse(f.readLines())
            assertTrue("seeded rc must parse clean, got: ${seeded.errors}", seeded.errors.isEmpty())
            assertEquals("the whole layout is in the copy", "meow-append", seeded.normal['a']?.command)
            assertEquals("avy-goto-line", seeded.normal['Q']?.command)
            assertTrue("the whole keypad table is in the copy", seeded.keypad.size > 150)
            f.writeText("nmap Q meow-goto-line\n")
            EditRcAction.seedIfMissing(f)
            assertEquals("nmap Q meow-goto-line\n", f.readText())
        } finally {
            f.delete()
        }
    }

    fun `test given unsaved rc edits in the editor then SPC c M flushes and reloads them`() {
        val home = FileUtil.createTempDirectory("meow-home", null)
        val oldHome = System.getProperty("user.home")
        System.setProperty("user.home", home.path)
        try {
            Rc.rcFile().writeText("nmap Z ,b\n")
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Rc.rcFile())!!
            val doc = FileDocumentManager.getInstance().getDocument(vf)!!
            WriteCommandAction.runWriteCommandAction(project) { doc.setText("nmap Q meow-goto-line\n") }
            assertTrue("the edit must start out unsaved", FileDocumentManager.getInstance().isDocumentUnsaved(doc))
            given("word", "ab<caret>cd")
            whenKeys(" cM")
            assertEquals(
                "the document edit is what got loaded",
                "meow-goto-line",
                Rc.cfg().normal['Q']?.command,
            )
            assertFalse("the document was flushed to disk", FileDocumentManager.getInstance().isDocumentUnsaved(doc))
        } finally {
            System.setProperty("user.home", oldHome)
            home.deleteRecursively()
        }
    }

    fun `test given comment-only rc edits then the reload button reports no changes`() {
        val home = FileUtil.createTempDirectory("meow-home", null)
        val oldHome = System.getProperty("user.home")
        System.setProperty("user.home", home.path)
        try {
            Rc.rcFile().writeText("nmap Z ,b\n")
            Rc.load()
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Rc.rcFile())!!
            val doc = FileDocumentManager.getInstance().getDocument(vf)!!
            WriteCommandAction.runWriteCommandAction(project) { doc.setText("\" just a comment\nnmap Z ,b\n") }
            assertTrue("comments don't count as changes", RcFileState.equalTo(doc))
            WriteCommandAction.runWriteCommandAction(project) { doc.setText("nmap Q meow-goto-line\n") }
            assertFalse("a mapping change does", RcFileState.equalTo(doc))
        } finally {
            System.setProperty("user.home", oldHome)
            Rc.setForTest(Rc.Config())
            home.deleteRecursively()
        }
    }

    fun `test given a non-rc editor then the floating reload action is hidden`() {
        given("word", "ab<caret>cd")
        val action = ReloadRcFloatingAction()
        val e =
            TestActionEvent.createTestEvent(
                action,
                SimpleDataContext
                    .builder()
                    .add(CommonDataKeys.EDITOR, myFixture.editor)
                    .add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile)
                    .build(),
            )
        action.update(e)
        assertFalse(e.presentation.isEnabledAndVisible)
    }

    fun `test given the rc editor with changes then the floating reload applies them in place`() {
        val home = FileUtil.createTempDirectory("meow-home", null)
        val oldHome = System.getProperty("user.home")
        System.setProperty("user.home", home.path)
        try {
            Rc.rcFile().writeText("nmap Z ,b\n")
            Rc.load()
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Rc.rcFile())!!
            val doc = FileDocumentManager.getInstance().getDocument(vf)!!
            WriteCommandAction.runWriteCommandAction(project) { doc.setText("nmap Q meow-goto-line\n") }
            val editor = EditorFactory.getInstance().createEditor(doc, project)
            try {
                val action = ReloadRcFloatingAction()
                val e =
                    TestActionEvent.createTestEvent(
                        action,
                        SimpleDataContext
                            .builder()
                            .add(CommonDataKeys.EDITOR, editor)
                            .add(CommonDataKeys.VIRTUAL_FILE, vf)
                            .build(),
                    )
                action.update(e)
                assertTrue("visible on the rc editor", e.presentation.isEnabledAndVisible)
                assertEquals("Reload ~/${Rc.FILE_NAME}", e.presentation.text)
                action.actionPerformed(e)
                assertEquals(
                    "the unsaved edit is what got loaded",
                    "meow-goto-line",
                    Rc.cfg().normal['Q']?.command,
                )
                action.update(e)
                assertEquals("No Changes in ~/${Rc.FILE_NAME}", e.presentation.text)
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        } finally {
            System.setProperty("user.home", oldHome)
            Rc.setForTest(Rc.Config())
            home.deleteRecursively()
        }
    }

    fun `test given bad lines then errors are collected with line numbers`() {
        val c =
            Rc.parse(
                listOf(
                    "frobnicate everything",
                    "nmap <Space> ,b",
                    "map <leader>1 <action>(X)",
                    "nmap Q <CR>",
                    "mmap <leader>x ,b",
                ),
            )
        assertEquals(5, c.errors.size)
        assertTrue(c.errors[0].startsWith("line 1"))
    }

    fun `test given an rc key-sequence override then the key replays through the engine`() {
        given("two words", "on<caret>e two")
        givenRc("nmap Z ,b")
        whenKeys("Z")
        thenSelection("one two")
    }

    fun `test given a recursive map then the RHS expands user maps`() {
        given("two words", "one two<caret>")
        givenRc("nmap B ,b\nnmap Y B")
        whenKeys("Y")
        thenSelection("one two")
    }

    fun `test given nnoremap then the RHS runs the bundled default instead`() {
        given("two words", "one two<caret>")
        givenRc("nmap B ,b\nnnoremap Z B")
        whenKeys("Z")
        thenSelection("two")
    }

    fun `test given a self-referencing map then recursion is depth-limited`() {
        given("plain", "<caret>hello")
        givenRc("nmap Z Z")
        whenKeys("Z")
        thenText("hello")
    }

    fun `test given an rc keypad mapping with keys then SPC seq replays them`() {
        given("two words", "on<caret>e two")
        givenRc("map <leader>k ,b")
        whenKeys(" k")
        thenSelection("one two")
        thenMode(MeowMode.NORMAL)
    }

    fun `test given an rc keypad mapping then it overrides the bundled entry`() {
        given("two words", "on<caret>e two")
        givenRc("map <leader>bb ,b")
        whenKeys(" bb")
        thenSelection("one two")
    }

    fun `test given a layout rebinding then the key runs the meow command`() {
        given("two words", "on<caret>e two")
        givenRc("nmap n meow-mark-word")
        whenKeys("n")
        thenSelection("one")
    }

    fun `test given ignore then the key is disabled`() {
        given("chars", "<caret>abc")
        givenRc("nmap d ignore")
        whenKeys("d")
        thenText("abc")
    }

    fun `test given a motion rebinding then MOTION-state editors use it`() {
        given("three lines", "<caret>one\ntwo\nthree")
        givenRc("mmap n meow-next")
        st.mode = MeowMode.MOTION
        whenKeys("n")
        assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
        whenKeys("j")
        assertEquals(2, doc.getLineNumber(ed.caretModel.offset))
    }

    fun `test given repeat on another key then it repeats the last command`() {
        given("chars", "<caret>abcdef")
        givenRc("nmap Z repeat")
        whenKeys("d")
        thenText("bcdef")
        whenKeys("Z")
        thenText("cdef")
    }

    fun `test given a mapped key when quote then the mapping repeats`() {
        given("chars", "<caret>abcdef")
        givenRc("nmap Z d")
        whenKeys("Z")
        thenText("bcdef")
        whenKeys("'")
        thenText("cdef")
    }

    fun `test given keypad entries then which-key rows show terminals and groups`() {
        givenRc("map <leader>zz <action>(GotoFile)\ndesc <leader>z my group")
        val top = WhichKey.keypadRows("")
        assertTrue(top.contains("z" to "my group"))
        val inner = WhichKey.keypadRows("z")
        assertTrue(inner.contains("z" to "GotoFile"))
    }

    fun `test given a terminal with a description then which-key prefers it`() {
        givenRc("map <leader>zz <action>(GotoFile)\ndesc <leader>zz open a file")
        assertTrue(WhichKey.keypadRows("z").contains("z" to "open a file"))
    }

    fun `test given the default table then the SPC SPC entry renders as SPC`() {
        assertTrue(WhichKey.keypadRows("").any { it.first == "SPC" })
    }

    companion object {
        private val QWERTY: Map<Char, String> =
            buildMap {
                for (n in 0..9) put('0' + n, "meow-expand-$n")
                put('-', "meow-negative-argument")
                put(';', "meow-reverse")
                put(',', "meow-inner-of-thing")
                put('.', "meow-bounds-of-thing")
                put('[', "meow-beginning-of-thing")
                put(']', "meow-end-of-thing")
                put('<', "meow-beginning-of-thing")
                put('>', "meow-end-of-thing")
                put('a', "meow-append")
                put('A', "meow-open-below")
                put('b', "meow-back-word")
                put('B', "meow-back-symbol")
                put('c', "meow-change")
                put('d', "meow-delete")
                put('D', "meow-backward-delete")
                put('e', "meow-next-word")
                put('E', "meow-next-symbol")
                put('f', "meow-find")
                put('g', "meow-cancel-selection")
                put('G', "meow-grab")
                put('h', "meow-left")
                put('H', "meow-left-expand")
                put('i', "meow-insert")
                put('I', "meow-open-above")
                put('j', "meow-next")
                put('J', "meow-next-expand")
                put('k', "meow-prev")
                put('K', "meow-prev-expand")
                put('l', "meow-right")
                put('L', "meow-right-expand")
                put('m', "meow-join")
                put('n', "meow-search")
                put('o', "meow-block")
                put('O', "meow-to-block")
                put('p', "meow-yank")
                put('q', "meow-quit")
                put('Q', "meow-goto-line")
                put('r', "meow-replace")
                put('R', "meow-swap-grab")
                put('s', "meow-kill")
                put('t', "meow-till")
                put('u', "meow-undo")
                put('U', "meow-undo-in-selection")
                put('v', "meow-visit")
                put('w', "meow-mark-word")
                put('W', "meow-mark-symbol")
                put('x', "meow-line")
                put('X', "meow-goto-line")
                put('y', "meow-save")
                put('Y', "meow-sync-grab")
                put('z', "meow-pop-selection")
                put('\'', "repeat")
            }
    }
}
