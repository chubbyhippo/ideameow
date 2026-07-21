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
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JSpinner
import javax.swing.JTextField

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

    fun `test given the bundled rc then SPC j j runs ace-click`() {
        assertEquals("ace-click", Rc.defaults().keypad["jj"]?.command)
        assertTrue(Engine.COMMANDS.containsKey("ace-click"))
    }
}
