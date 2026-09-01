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

    fun `test given trees tables panels and buttons then space stays a leader surface`() {
        assertFalse(nativeSpace(JTree()))
        assertFalse(nativeSpace(JTable()))
        assertFalse(nativeSpace(JPanel()))
        assertFalse(nativeSpace(JButton()))
    }

    fun `test given inputs combos and checkbox lists then space stays native`() {
        assertTrue(nativeSpace(JTextField()))
        assertTrue(nativeSpace(JTextArea().apply { isEditable = false }))
        assertTrue(nativeSpace(JComboBox<String>()))
        assertTrue(nativeSpace(CheckBoxList<String>()))
    }

    fun `test given a tree whose space toggles then space stays native`() {
        assertTrue("com.intellij.openapi.vcs.changes.ui.ChangesTree" in SpaceLeader.SPACE_TREES)
        assertTrue("com.intellij.ui.CheckboxTree" in SpaceLeader.SPACE_TREES)
        assertFalse(treeConsumesSpace(JTree::class.java))
    }

    fun `test given a component nested in a native-space ancestor then space stays native`() {
        assertTrue(nativeSpace(JPanel().also { JComboBox<String>().add(it) }))
    }

    fun `test given an open menu then arming skips the native-space and editor gates`() {
        given("space leader menu", "text")
        assertTrue(blocksArming(false, JTextField()))
        assertFalse(blocksArming(true, JTextField()))
        assertTrue(blocksArming(false, ed.contentComponent))
        assertFalse(blocksArming(true, ed.contentComponent))
        assertFalse(blocksArming(false, JPanel()))
    }

    fun `test given a routed leader surface then typed keys drive the keypad`() {
        given("space leader routing", "text")
        whenKeys(" ")
        thenMode(MeowMode.KEYPAD)
        val panel = JPanel()
        SpaceLeader.routeTo(ed, state, panel)
        assertSame(panel, SpaceLeader.surfaceFor(ed))
        assertTrue(SpaceLeader.dispatch(typed(panel, 'w')))
        thenMode(MeowMode.KEYPAD)
        assertSame(panel, SpaceLeader.surfaceFor(ed))
    }

    fun `test given an INSERT editor then the leader keypad round-trips back to INSERT`() {
        given("space leader insert", "text")
        whenKeys("i")
        thenMode(MeowMode.INSERT)
        SpaceLeader.openKeypad(ed, state)
        thenMode(MeowMode.KEYPAD)
        val panel = JPanel()
        SpaceLeader.routeTo(ed, state, panel)
        assertTrue(SpaceLeader.dispatch(typed(panel, '3')))
        thenMode(MeowMode.INSERT)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given a terminal keypad key from the leader surface then the route clears after dispatch`() {
        given("space leader terminal", "text")
        whenKeys(" ")
        val panel = JPanel()
        SpaceLeader.routeTo(ed, state, panel)
        assertTrue(SpaceLeader.dispatch(typed(panel, '3')))
        thenMode(MeowMode.NORMAL)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given ESC pressed on a routed leader surface then the keypad exits and the route clears`() {
        given("space leader escape", "text")
        whenKeys(" ")
        val panel = JPanel()
        SpaceLeader.routeTo(ed, state, panel)
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
        AceWindow.begin(ed, state, AceWindow.Request(swap = false, windows = windows))
        SpaceLeader.routeTo(ed, state, panel)
        assertTrue(SpaceLeader.dispatch(typed(panel, 's')))
        assertNull(state.aceWindow)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given the keypad already left then a routed key passes through and clears`() {
        given("space leader stale", "text")
        val panel = JPanel()
        SpaceLeader.routeTo(ed, state, panel)
        assertFalse(SpaceLeader.dispatch(typed(panel, 'x')))
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given a reset then no surface is reported for any editor`() {
        given("space leader reset", "text")
        val panel = JPanel()
        SpaceLeader.routeTo(ed, state, panel)
        assertSame(panel, SpaceLeader.surfaceFor(ed))
        SpaceLeader.reset()
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given a focus outside any editor then routeIfOutsideEditor arms the surface`() {
        given("space leader route outside", "text")
        val tree = JTree()
        routeIfOutsideEditor(ed, state, tree)
        assertSame(tree, SpaceLeader.surfaceFor(ed))
    }

    fun `test given a focus inside the editor then routeIfOutsideEditor arms nothing`() {
        given("space leader route inside", "text")
        routeIfOutsideEditor(ed, state, ed.contentComponent)
        assertNull(SpaceLeader.surfaceFor(ed))
    }

    fun `test given a null focus then routeIfOutsideEditor arms nothing`() {
        given("space leader route null", "text")
        routeIfOutsideEditor(ed, state, null)
        assertNull(SpaceLeader.surfaceFor(ed))
    }
}
