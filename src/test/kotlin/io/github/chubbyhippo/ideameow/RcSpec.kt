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

/** ~/.ideameowrc parsing, nmap/map dispatch, and which-key rows. */
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

    fun `test given leader mappings and descriptions then the keypad table extends`() {
        val c = Rc.parse(
            listOf(
                "map <leader>gd <action>(GotoDeclaration)",
                "desc <leader>g goto things",
            )
        )
        assertEquals("GotoDeclaration", c.keypadUser["gd"]!!.action)
        assertEquals("goto things", c.keypadDesc["g"])
        assertEquals("GotoDeclaration", c.keypad["gd"]!!.action)
        assertEquals("RecentFiles", c.keypad["b"]!!.action) // builtin table survives
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
        assertFalse(c.whichKey)
        assertEquals(400, c.whichKeyDelayMs)
        assertTrue(c.errors.isEmpty())
    }

    fun `test given a trailing comment then it is stripped from the line`() {
        val c = Rc.parse(
            listOf(
                "nmap S <action>(AceAction)   \" jump anywhere",
                "map <leader>zz ,b            \" select the buffer",
            )
        )
        assertEquals("AceAction", c.normal['S']!!.action)
        assertEquals(",b", c.keypadUser["zz"]!!.keys)
        assertTrue(c.errors.isEmpty())
    }

    fun `test the shipped default ideameowrc parses without errors`() {
        val f = java.io.File(System.getProperty("user.dir"), Rc.FILE_NAME)
        if (!f.isFile) return // some runners relocate the working dir; nothing to check
        val c = Rc.parse(f.readLines())
        assertTrue("shipped default must parse clean, got: ${c.errors}", c.errors.isEmpty())
        assertTrue("shipped default should carry the ported leader groups", c.keypadUser.size > 100)
    }

    fun `test given bad lines then errors are collected with line numbers`() {
        val c = Rc.parse(
            listOf(
                "frobnicate everything",   // unknown command
                "nmap <Space> ,b",          // SPC is reserved
                "map <leader>1 <action>(X)", // keypad digits are reserved
                "nmap Q <CR>",               // unsupported key token
            )
        )
        assertEquals(4, c.errors.size)
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

    fun `test given nnoremap then the RHS runs the builtin key instead`() {
        given("two words", "one two<caret>")
        givenRc("nmap B ,b\nnnoremap Z B")
        whenKeys("Z")
        thenSelection("two") // builtin B = back-symbol, not the user map
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

    fun `test given an rc keypad mapping then it overrides the builtin entry`() {
        given("two words", "on<caret>e two")
        givenRc("map <leader>b ,b") // builtin SPC b = RecentFiles
        whenKeys(" b")
        thenSelection("one two")
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

    fun `test given the builtin table then the SPC SPC entry renders as SPC`() {
        assertTrue(WhichKey.keypadRows("").any { it.first == "SPC" })
    }
}
