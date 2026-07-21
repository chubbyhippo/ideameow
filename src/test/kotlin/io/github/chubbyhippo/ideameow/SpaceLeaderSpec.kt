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

import com.intellij.ui.CheckBoxList
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree

class SpaceLeaderSpec : MeowSpec() {
    override fun tearDown() {
        SpaceLeader.reset()
        super.tearDown()
    }

    private fun typed(
        source: JPanel,
        c: Char,
    ) = KeyEvent(source, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c)

    fun `test given trees tables and panels then space stays a leader surface`() {
        assertFalse(SpaceLeader.nativeSpace(JTree()))
        assertFalse(SpaceLeader.nativeSpace(JTable()))
        assertFalse(SpaceLeader.nativeSpace(JPanel()))
    }

    fun `test given inputs buttons combos and checkbox lists then space stays native`() {
        assertTrue(SpaceLeader.nativeSpace(JTextField()))
        assertTrue(SpaceLeader.nativeSpace(JTextArea().apply { isEditable = false }))
        assertTrue(SpaceLeader.nativeSpace(JButton()))
        assertTrue(SpaceLeader.nativeSpace(JComboBox<String>()))
        assertTrue(SpaceLeader.nativeSpace(CheckBoxList<String>()))
    }

    fun `test given a component nested in a native-space ancestor then space stays native`() {
        assertTrue(SpaceLeader.nativeSpace(JPanel().also { JComboBox<String>().add(it) }))
    }

    fun `test given an open menu then arming skips the native-space and editor gates`() {
        given("space leader menu", "text")
        assertTrue(SpaceLeader.blocksArming(false, JTextField()))
        assertFalse(SpaceLeader.blocksArming(true, JTextField()))
        assertTrue(SpaceLeader.blocksArming(false, ed.contentComponent))
        assertFalse(SpaceLeader.blocksArming(true, ed.contentComponent))
        assertFalse(SpaceLeader.blocksArming(false, JPanel()))
    }

    fun `test given a routed leader surface then typed keys drive the keypad`() {
        given("space leader routing", "text")
        whenKeys(" ")
        thenMode(MeowMode.KEYPAD)
        val panel = JPanel()
        SpaceLeader.setForTest(ed, st, panel)
        assertSame(panel, SpaceLeader.surfaceFor(ed))
        assertTrue(SpaceLeader.dispatch(typed(panel, 'w')))
        thenMode(MeowMode.KEYPAD)
        assertSame(panel, SpaceLeader.surfaceFor(ed))
    }

    fun `test given an INSERT editor then the leader keypad round-trips back to INSERT`() {
        given("space leader insert", "text")
        whenKeys("i")
        thenMode(MeowMode.INSERT)
        SpaceLeader.openKeypad(ed, st)
        thenMode(MeowMode.KEYPAD)
        val panel = JPanel()
        SpaceLeader.setForTest(ed, st, panel)
        assertTrue(SpaceLeader.dispatch(typed(panel, '3')))
        thenMode(MeowMode.INSERT)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given a terminal keypad key from the leader surface then the route clears after dispatch`() {
        given("space leader terminal", "text")
        whenKeys(" ")
        val panel = JPanel()
        SpaceLeader.setForTest(ed, st, panel)
        assertTrue(SpaceLeader.dispatch(typed(panel, '3')))
        thenMode(MeowMode.NORMAL)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given ESC pressed on a routed leader surface then the keypad exits and the route clears`() {
        given("space leader escape", "text")
        whenKeys(" ")
        val panel = JPanel()
        SpaceLeader.setForTest(ed, st, panel)
        val esc =
            KeyEvent(
                panel,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED,
            )
        assertTrue(SpaceLeader.dispatch(esc))
        thenMode(MeowMode.NORMAL)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given an ace-window session then leader keys route the pick and the route clears`() {
        given("space leader ace", "text")
        val panel = JPanel()
        val windows =
            listOf(
                AceWindow.Window(Rectangle(0, 0, 40, 24), ed, ed.contentComponent),
                AceWindow.Window(Rectangle(40, 0, 40, 24), null, panel),
                AceWindow.Window(Rectangle(80, 0, 40, 24), null, JPanel()),
            )
        AceWindow.begin(ed, st, swap = false, windows = windows)
        SpaceLeader.setForTest(ed, st, panel)
        assertTrue(SpaceLeader.dispatch(typed(panel, 's')))
        assertNull(st.aceWindow)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given the keypad already left then a routed key passes through and clears`() {
        given("space leader stale", "text")
        val panel = JPanel()
        SpaceLeader.setForTest(ed, st, panel)
        assertFalse(SpaceLeader.dispatch(typed(panel, 'x')))
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given a reset then no surface is reported for any editor`() {
        given("space leader reset", "text")
        val panel = JPanel()
        SpaceLeader.setForTest(ed, st, panel)
        assertSame(panel, SpaceLeader.surfaceFor(ed))
        SpaceLeader.reset()
        assertNull(SpaceLeader.surfaceFor(ed))
    }
}
