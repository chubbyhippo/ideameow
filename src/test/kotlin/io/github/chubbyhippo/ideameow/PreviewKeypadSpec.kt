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

import java.awt.event.KeyEvent
import javax.swing.JPanel

class PreviewKeypadSpec : MeowSpec() {
    override fun tearDown() {
        PreviewKeypad.reset()
        super.tearDown()
    }

    private fun typed(
        source: JPanel,
        c: Char,
    ) = KeyEvent(source, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c)

    fun `test given a routed preview surface then typed keys drive the keypad`() {
        given("preview keypad routing", "text")
        whenKeys(" ")
        thenMode(MeowMode.KEYPAD)
        val panel = JPanel()
        PreviewKeypad.setForTest(ed, st, panel)
        assertSame(panel, PreviewKeypad.surfaceFor(ed))
        assertTrue(PreviewKeypad.dispatch(typed(panel, 'w')))
        thenMode(MeowMode.KEYPAD)
        assertSame(panel, PreviewKeypad.surfaceFor(ed))
    }

    fun `test given a terminal keypad key from the preview then the route clears after dispatch`() {
        given("preview keypad terminal", "text")
        whenKeys(" ")
        val panel = JPanel()
        PreviewKeypad.setForTest(ed, st, panel)
        assertTrue(PreviewKeypad.dispatch(typed(panel, '3')))
        thenMode(MeowMode.NORMAL)
        assertNull(PreviewKeypad.surfaceFor(ed))
    }

    fun `test given ESC pressed on a routed preview surface then the keypad exits and the route clears`() {
        given("preview keypad escape", "text")
        whenKeys(" ")
        val panel = JPanel()
        PreviewKeypad.setForTest(ed, st, panel)
        val esc =
            KeyEvent(
                panel,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED,
            )
        assertTrue(PreviewKeypad.dispatch(esc))
        thenMode(MeowMode.NORMAL)
        assertNull(PreviewKeypad.surfaceFor(ed))
    }

    fun `test given the keypad already left then a routed key passes through and clears`() {
        given("preview keypad stale", "text")
        val panel = JPanel()
        PreviewKeypad.setForTest(ed, st, panel)
        assertFalse(PreviewKeypad.dispatch(typed(panel, 'x')))
        assertNull(PreviewKeypad.surfaceFor(ed))
    }

    fun `test given a reset then no surface is reported for any editor`() {
        given("preview keypad reset", "text")
        val panel = JPanel()
        PreviewKeypad.setForTest(ed, st, panel)
        assertSame(panel, PreviewKeypad.surfaceFor(ed))
        PreviewKeypad.reset()
        assertNull(PreviewKeypad.surfaceFor(ed))
    }
}
