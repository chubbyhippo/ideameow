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

/** ~/.ideameowrc parsing, nmap/mmap/map dispatch (including relayouting the
 *  meow keys themselves), and which-key rows. */
class RcSpec : MeowSpec() {

    // ------------------------------------------------------------- parsing

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
        val c = Rc.parse(
            listOf(
                "map <leader>gd <action>(GotoDeclaration)",
                "desc <leader>g goto things",
            )
        )
        assertEquals("GotoDeclaration", c.keypad["gd"]!!.action)
        assertEquals("goto things", c.keypadDesc["g"])
        Rc.setForTest(c)
        assertEquals("GotoDeclaration", Rc.keypad()["gd"]!!.action)
        assertEquals("RecentFiles", Rc.keypad()["b"]!!.action) // bundled defaults beneath
    }

    fun `test given the ideavimrc WhichKeyDesc let syntax then descriptions parse`() {
        val c = Rc.parse(listOf("""let g:WhichKeyDesc_leader_x = "<leader>x C-x files/buffers""""))
        assertEquals("C-x files/buffers", c.keypadDesc["x"])
        assertTrue(c.errors.isEmpty())
    }

    fun `test given set lines then which-key options apply and vim options are ignored`() {
        val c = Rc.parse(
            listOf(
                "set nowhich-key",
                "set timeoutlen=400",
                "set clipboard+=unnamedplus", // pasted from .ideavimrc: ignored
                "let mapleader=\" \"",
            )
        )
        assertEquals(false, c.whichKey)
        assertEquals(400, c.whichKeyDelayMs)
        assertTrue(c.errors.isEmpty())
    }

    fun `test which-key settings layer user over bundled defaults`() {
        // empty user config: the bundled file's `set which-key` / timeoutlen=300
        assertTrue(Rc.whichKeyEnabled())
        assertEquals(300, Rc.whichKeyDelayMs())
        givenRc("set nowhich-key\nset timeoutlen=150")
        assertFalse(Rc.whichKeyEnabled())
        assertEquals(150, Rc.whichKeyDelayMs())
    }

    fun `test given a trailing comment then it is stripped from the line`() {
        val c = Rc.parse(
            listOf(
                "nmap S <action>(AceAction)   \" jump anywhere",
                "map <leader>zz ,b            \" select the buffer",
            )
        )
        assertEquals("AceAction", c.normal['S']!!.action)
        assertEquals(",b", c.keypad["zz"]!!.keys)
        assertTrue(c.errors.isEmpty())
    }

    fun `test the bundled default ideameowrc defines the whole keymap`() {
        val d = Rc.defaults()
        assertTrue("bundled default must parse clean, got: ${d.errors}", d.errors.isEmpty())
        // the layout block must define meow's full QWERTY layout (Q is the
        // deliberate avy override further down the file)
        for ((key, cmd) in QWERTY) {
            if (key == 'Q') continue
            assertEquals("bundled layout line for '$key'", cmd, d.normal[key]?.command)
        }
        assertEquals("avy-goto-line", d.normal['Q']?.command)
        assertEquals("avy-goto-char-timer", d.normal['S']?.command)
        assertEquals("meow-next", d.motion['j']?.command)
        assertEquals("meow-prev", d.motion['k']?.command)
        // the keypad table lives in the file too — nothing is bound in code
        assertEquals("RecentFiles", d.keypad["b"]?.action)
        assertEquals("Switcher", d.keypad[" "]?.action)
        assertEquals("Ideameow.EditRc", d.keypad["cv"]?.action)
        assertEquals("Ideameow.ReloadRc", d.keypad["cV"]?.action)
        assertTrue("keypad table + ported leader groups", d.keypad.size > 150)
    }

    fun `test given bad lines then errors are collected with line numbers`() {
        val c = Rc.parse(
            listOf(
                "frobnicate everything",   // unknown command
                "nmap <Space> ,b",          // SPC is reserved
                "map <leader>1 <action>(X)", // keypad digits are reserved
                "nmap Q <CR>",               // unsupported key token
                "mmap <leader>x ,b",         // keypad entries are mode-independent
            )
        )
        assertEquals(5, c.errors.size)
        assertTrue(c.errors[0].startsWith("line 1"))
    }

    // ------------------------------------------------------------ dispatch

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
        thenSelection("one two") // Y -> user B -> whole buffer
    }

    fun `test given nnoremap then the RHS runs the bundled default instead`() {
        given("two words", "one two<caret>")
        givenRc("nmap B ,b\nnnoremap Z B")
        whenKeys("Z")
        thenSelection("two") // bundled-default B = back-symbol, not the user map
    }

    fun `test given a self-referencing map then recursion is depth-limited`() {
        given("plain", "<caret>hello")
        givenRc("nmap Z Z")
        whenKeys("Z") // must terminate via the depth guard
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
        givenRc("map <leader>b ,b") // bundled-default SPC b = RecentFiles
        whenKeys(" b")
        thenSelection("one two")
    }

    fun `test given a layout rebinding then the key runs the meow command`() {
        given("two words", "on<caret>e two")
        givenRc("nmap n meow-mark-word") // bundled-default n = meow-search
        whenKeys("n")
        thenSelection("one")
    }

    fun `test given ignore then the key is disabled`() {
        given("chars", "<caret>abc")
        givenRc("nmap d ignore")
        whenKeys("d")
        thenText("abc")
    }

    fun `test given a motion rebinding then read-only editors use it`() {
        given("three lines", "<caret>one\ntwo\nthree")
        givenRc("mmap n meow-next")
        doc.setReadOnly(true)
        try {
            whenKeys("n")
            assertEquals(1, doc.getLineNumber(ed.caretModel.offset))
            whenKeys("j") // the default motion keys stay underneath
            assertEquals(2, doc.getLineNumber(ed.caretModel.offset))
        } finally {
            doc.setReadOnly(false)
        }
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

    // ------------------------------------------------------------ which-key

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
        /**
         * meow's suggested QWERTY layout (KEYBINDING_QWERTY in meow's README;
         * `<` and `>` are plugin aliases for `[` and `]`) — the contract the
         * bundled .ideameowrc layout block must satisfy.
         */
        private val QWERTY: Map<Char, String> = buildMap {
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
