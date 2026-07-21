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
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UIUtil
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager

class AceClickSpec : MeowSpec() {
    private fun targets(
        count: Int,
        clicks: MutableList<Int>,
    ): List<AceClick.Target> {
        val panel = JPanel()
        return (0 until count).map { i ->
            val button = JButton()
            panel.add(button)
            AceClick.Target(Rectangle(i * 10, 0, 10, 10), button) { clicks.add(i) }
        }
    }

    private fun pressEsc() {
        val noop =
            object : EditorActionHandler() {
                override fun doExecute(
                    editor: Editor,
                    caret: Caret?,
                    dataContext: DataContext?,
                ) = Unit
            }
        MeowEscapeHandler(noop).execute(ed, null, (ed as EditorEx).dataContext)
    }

    fun `test given buttons links menus and combos then ace-click classifies them clickable`() {
        var hit = 0
        val button = JButton()
        button.addActionListener { hit++ }
        AceClick.clicker(button)!!.invoke()
        assertEquals(1, hit)
        assertNotNull(AceClick.clicker(JMenu()))
        assertNotNull(AceClick.clicker(LinkLabel<Any>("link", null)))
        assertNotNull(AceClick.clicker(JComboBox<String>()))
    }

    fun `test given panels labels and disabled buttons then ace-click skips them`() {
        assertNull(AceClick.clicker(JPanel()))
        assertNull(AceClick.clicker(JLabel("x")))
        assertNull(AceClick.clicker(JButton().apply { isEnabled = false }))
    }

    fun `test given combo spinner and scrollbar internals then ace-click skips the child buttons`() {
        assertNull(AceClick.clicker(JButton().also { JComboBox<String>().add(it) }))
        assertNull(AceClick.clicker(JButton().also { JSpinner().add(it) }))
        assertNull(AceClick.clicker(JButton().also { JScrollBar().add(it) }))
    }

    fun `test given an editable text field then ace-click classifies it clickable`() {
        assertNotNull(AceClick.clicker(JTextField("find")))
        assertNull(AceClick.clicker(JTextField().apply { isEditable = false }))
    }

    fun `test given combo and spinner editor fields then ace-click skips them`() {
        assertNull(AceClick.clicker(JTextField().also { JComboBox<String>().add(it) }))
        assertNull(AceClick.clicker((JSpinner().editor as JSpinner.DefaultEditor).textField))
    }

    fun `test given twelve targets then ace-click labels follow the avy subdivision`() {
        given("ace-click subdivision", "text")
        AceClick.begin(ed, st, targets(12, mutableListOf()))
        val labels = Avy.labels(st.aceClick!!.node!!).map { it.second }
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "la", "ls", "ld", "lf"), labels)
    }

    fun `test given a unique hint key then the target is clicked once and the session ends`() {
        given("ace-click unique", "text")
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, targets(3, clicks))
        whenKeys("s")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(1), clicks)
        assertNull(st.aceClick)
        thenMode(MeowMode.NORMAL)
    }

    fun `test given a subtree key then the remaining suffixes stay pending`() {
        given("ace-click subtree", "text")
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, targets(12, clicks))
        whenKeys("l")
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(clicks.isEmpty())
        assertEquals(listOf("a", "s", "d", "f"), Avy.labels(st.aceClick!!.node!!).map { it.second })
        whenKeys("d")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(10), clicks)
        assertNull(st.aceClick)
    }

    fun `test given a bad hint key then ace-click hints and stays`() {
        given("ace-click bad key", "text")
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, targets(3, clicks))
        whenKeys("x")
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(clicks.isEmpty())
        assertNotNull(st.aceClick)
    }

    fun `test given ESC during ace-click then the session cancels without a click`() {
        given("ace-click escape", "text")
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, targets(3, clicks))
        assertTrue(MeowEscape.wants(ed, st))
        pressEsc()
        UIUtil.dispatchAllInvocationEvents()
        assertNull(st.aceClick)
        assertTrue(clicks.isEmpty())
    }

    fun `test given a single target then ace-click labels instead of auto-clicking`() {
        given("ace-click single", "text")
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, targets(1, clicks))
        assertNotNull(st.aceClick)
        assertTrue(clicks.isEmpty())
        whenKeys("a")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(0), clicks)
    }

    fun `test given a detached target then the click is skipped`() {
        given("ace-click detached target", "text")
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, listOf(AceClick.Target(Rectangle(0, 0, 10, 10), JButton()) { clicks.add(0) }))
        whenKeys("a")
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(clicks.isEmpty())
        assertNull(st.aceClick)
    }

    fun `test given a target hidden after badging then the pick still clicks it`() {
        given("ace-click hidden target", "text")
        val clicks = mutableListOf<Int>()
        val hidden = targets(1, clicks)
        hidden[0].component.isVisible = false
        AceClick.begin(ed, st, hidden)
        whenKeys("a")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(0), clicks)
    }

    fun `test given no targets then ace-click arms no session`() {
        given("ace-click empty", "text")
        AceClick.begin(ed, st, emptyList())
        assertNull(st.aceClick)
    }

    fun `test given a menu then ace-click opens it by selecting its path`() {
        val menu = JMenu("m")
        menu.add(JMenuItem("i"))
        try {
            AceClick.clicker(menu)!!.invoke()
            assertEquals(listOf<MenuElement>(menu, menu.popupMenu), MenuSelectionManager.defaultManager().selectedPath.toList())
        } finally {
            MenuSelectionManager.defaultManager().clearSelectedPath()
        }
    }

    fun `test given a menu item in an open path then the pick clears the path before clicking`() {
        val popup = JPopupMenu()
        val item = JMenuItem("i")
        popup.add(item)
        var pathSizeAtClick = -1
        item.addActionListener { pathSizeAtClick = MenuSelectionManager.defaultManager().selectedPath.size }
        MenuSelectionManager.defaultManager().setSelectedPath(arrayOf<MenuElement>(popup))
        try {
            AceClick.clicker(item)!!.invoke()
            assertEquals(0, pathSizeAtClick)
        } finally {
            MenuSelectionManager.defaultManager().clearSelectedPath()
        }
    }

    fun `test given a pick outside an open menu then the menu closes before the click`() {
        given("ace-click menu outside", "text")
        val clicks = mutableListOf<Int>()
        MenuSelectionManager.defaultManager().setSelectedPath(arrayOf<MenuElement>(JPopupMenu()))
        try {
            AceClick.begin(ed, st, targets(1, clicks))
            whenKeys("a")
            UIUtil.dispatchAllInvocationEvents()
            assertEquals(listOf(0), clicks)
            assertEquals(0, MenuSelectionManager.defaultManager().selectedPath.size)
        } finally {
            MenuSelectionManager.defaultManager().clearSelectedPath()
        }
    }

    fun `test given targets on two layers then each layer carries its own badge canvas`() {
        given("ace-click layers", "text")
        val panel = JPanel()
        val first = JLayeredPane()
        val second = JLayeredPane()
        val button = JButton().also { panel.add(it) }
        val other = JButton().also { panel.add(it) }
        AceClick.begin(
            ed,
            st,
            listOf(
                AceClick.Target(Rectangle(0, 0, 10, 10), button, first) { },
                AceClick.Target(Rectangle(10, 0, 10, 10), other, second) { },
            ),
        )
        assertEquals(2, st.aceClick!!.canvases.size)
        assertEquals(1, first.componentCount)
        assertEquals(1, second.componentCount)
        pressEsc()
        assertEquals(0, first.componentCount)
        assertEquals(0, second.componentCount)
    }

    fun `test given screen geometry then hint labels follow the screen order`() {
        given("ace-click screen order", "text")
        val panel = JPanel()
        val clicks = mutableListOf<String>()
        val rightmost = JButton().also { panel.add(it) }
        val leftmost = JButton().also { panel.add(it) }
        AceClick.begin(
            ed,
            st,
            listOf(
                AceClick.Target(Rectangle(0, 0, 10, 10), rightmost, null, Rectangle(100, 0, 10, 10)) { clicks.add("right") },
                AceClick.Target(Rectangle(0, 0, 10, 10), leftmost, null, Rectangle(0, 0, 10, 10)) { clicks.add("left") },
            ),
        )
        whenKeys("a")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf("left"), clicks)
    }

    fun `test given an INSERT editor with a live session then keys drive the pick`() {
        given("ace-click insert session", "text")
        whenKeys("i")
        thenMode(MeowMode.INSERT)
        val clicks = mutableListOf<Int>()
        AceClick.begin(ed, st, targets(1, clicks))
        whenKeys("a")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(0), clicks)
        thenMode(MeowMode.INSERT)
    }

    fun `test given the bundled rc then SPC SPC runs ace-click`() {
        assertEquals("ace-click", Rc.defaults().keypad[" "]?.command)
        assertNull("SPC j j is retired", Rc.defaults().keypad["jj"])
        assertTrue(Engine.COMMANDS.containsKey("ace-click"))
    }

    fun `test given a space-keyed keypad entry then SPC SPC dispatches it`() {
        given("spc spc dispatch", "text")
        givenRc("map <leader><Space> meow-insert")
        whenKeys(" ")
        thenMode(MeowMode.KEYPAD)
        whenKeys(" ")
        thenMode(MeowMode.INSERT)
    }
}
