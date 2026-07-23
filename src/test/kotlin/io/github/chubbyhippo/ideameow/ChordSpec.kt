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

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

class ChordSpec : MeowSpec() {
    override fun tearDown() {
        ChordDispatcher.reset()
        super.tearDown()
    }

    private fun pressed(
        keyCode: Int,
        mods: Int,
    ) = KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), mods, keyCode, KeyEvent.CHAR_UNDEFINED)

    private val ctrlF = ChordKey.of(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)
    private val altD = ChordKey.of(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK)
    private val altShiftComma = ChordKey.of(KeyEvent.VK_COMMA, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
    private val altShiftOpenBracket =
        ChordKey.of(KeyEvent.VK_OPEN_BRACKET, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)

    fun `test given the IntelliJ spelling then it normalizes to the same key as the pressed event`() {
        assertEquals(ctrlF, ChordKey.fromKeyStroke(KeyStroke.getKeyStroke("control F")))
        assertEquals(altShiftComma, ChordKey.fromKeyStroke(KeyStroke.getKeyStroke("alt shift COMMA")))
        assertEquals(ctrlF, ChordKey.of(pressed(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK).keyCode, InputEvent.CTRL_DOWN_MASK))
        assertFalse(
            "different chords are not equal",
            ChordKey.fromKeyStroke(KeyStroke.getKeyStroke("control F")) ==
                ChordKey.fromKeyStroke(KeyStroke.getKeyStroke("alt F")),
        )
    }

    fun `test given a cmap line then it parses into a chord binding`() {
        val c = Rc.parse(listOf("cmap control F forward-char", "cnoremap alt D kill-word"))
        assertEquals("forward-char", c.chords[ctrlF]?.command)
        val kill = c.chords[altD]!!
        assertEquals("kill-word", kill.command)
        assertFalse("cnoremap is non-recursive", kill.recursive)
        assertTrue(c.errors.isEmpty())
    }

    fun `test given a cmap with no modifier or a bad keystroke then errors are collected`() {
        val c =
            Rc.parse(
                listOf(
                    "cmap F forward-char",
                    "cmap frobnicate forward-char",
                    "cmap control forward-char",
                ),
            )
        assertEquals(3, c.errors.size)
    }

    fun `test given the bundled defaults then the whole Emacs chord layer resolves`() {
        val chords = Rc.chords()
        assertEquals("forward-char", chords[ctrlF]?.command)
        assertEquals("kill-word", chords[altD]?.command)
        assertEquals("beginning-of-buffer", chords[altShiftComma]?.command)
        assertEquals("backward-paragraph", chords[altShiftOpenBracket]?.command)
        assertEquals("the whole chord layer is present", 18, chords.size)
    }

    fun `test given a home cmap override then it wins over the bundled default`() {
        givenRc("cmap control F backward-char")
        assertEquals("backward-char", Rc.chords()[ctrlF]?.command)
    }

    fun `test given a home cmap ignore then the chord is handed back to the IDE`() {
        givenRc("cmap control F ignore")
        assertNull("Ctrl-F is released to the IDE", Rc.chords()[ctrlF])
        assertEquals("the rest of the layer stays intact", "kill-word", Rc.chords()[altD]?.command)
    }

    fun `test given a pressed chord event then bindingFor resolves it and plain keys do not`() {
        assertEquals("forward-char", ChordDispatcher.bindingFor(pressed(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK))?.command)
        assertNull(ChordDispatcher.bindingFor(pressed(KeyEvent.VK_A, 0)))
        assertNull(ChordDispatcher.bindingFor(pressed(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)))
    }

    fun `test given shift alone then it is not a chord but Ctrl and Alt-Shift are`() {
        assertFalse(ChordDispatcher.isChord(pressed(KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK)))
        assertTrue(ChordDispatcher.isChord(pressed(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)))
        assertTrue(
            ChordDispatcher.isChord(pressed(KeyEvent.VK_COMMA, InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)),
        )
    }

    fun `test given NORMAL then a mapped chord is claimed but INSERT and plain keys are not`() {
        given("chord modes", "<caret>hello")
        assertTrue(ChordDispatcher.claims(st, pressed(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)))
        st.mode = MeowMode.INSERT
        assertFalse(ChordDispatcher.claims(st, pressed(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)))
        st.mode = MeowMode.NORMAL
        assertFalse(ChordDispatcher.claims(st, pressed(KeyEvent.VK_A, 0)))
    }

    fun `test given no armed swallow then a typed event passes through`() {
        val typed = KeyEvent(JPanel(), KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'a')
        assertFalse(ChordDispatcher.dispatch(typed))
    }

    fun `test given a NORMAL editor then dispatching a chord binding runs its command`() {
        given("chord effect", "<caret>hello")
        Engine.dispatch(ed, st, Rc.chords()[ctrlF]!!)
        thenCaretAt(1)
        thenNoSelection()
    }
}
