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
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel

class AceResizeSpec : MeowSpec() {
    private fun targets(count: Int): List<AceResize.Target> = (0 until count).map { AceResize.Target(Rectangle(it * 10, 0, 10, 10)) {} }

    private fun pressed(
        keyCode: Int,
        mods: Int = 0,
    ) = KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), mods, keyCode, KeyEvent.CHAR_UNDEFINED)

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

    fun `test given h l j k then dirOf maps them and other keys are null`() {
        assertEquals(AceResize.Dir.LEFT, AceResize.dirOf('h'))
        assertEquals(AceResize.Dir.RIGHT, AceResize.dirOf('l'))
        assertEquals(AceResize.Dir.DOWN, AceResize.dirOf('j'))
        assertEquals(AceResize.Dir.UP, AceResize.dirOf('k'))
        assertNull(AceResize.dirOf('x'))
        assertNull(AceResize.dirOf('a'))
    }

    fun `test given a horizontal splitter then left and right nudge the proportion`() {
        assertEquals(0.45, AceResize.nudge(false, AceResize.Dir.LEFT, 0.5f, 0.05f)!!.toDouble(), 1e-5)
        assertEquals(0.55, AceResize.nudge(false, AceResize.Dir.RIGHT, 0.5f, 0.05f)!!.toDouble(), 1e-5)
        assertNull(AceResize.nudge(false, AceResize.Dir.UP, 0.5f, 0.05f))
        assertNull(AceResize.nudge(false, AceResize.Dir.DOWN, 0.5f, 0.05f))
    }

    fun `test given a vertical splitter then up and down nudge the proportion`() {
        assertEquals(0.45, AceResize.nudge(true, AceResize.Dir.UP, 0.5f, 0.05f)!!.toDouble(), 1e-5)
        assertEquals(0.55, AceResize.nudge(true, AceResize.Dir.DOWN, 0.5f, 0.05f)!!.toDouble(), 1e-5)
        assertNull(AceResize.nudge(true, AceResize.Dir.LEFT, 0.5f, 0.05f))
        assertNull(AceResize.nudge(true, AceResize.Dir.RIGHT, 0.5f, 0.05f))
    }

    fun `test given a proportion near the edge then nudge clamps to the min band`() {
        assertEquals(0.95, AceResize.nudge(false, AceResize.Dir.RIGHT, 0.93f, 0.05f)!!.toDouble(), 1e-5)
        assertEquals(0.05, AceResize.nudge(false, AceResize.Dir.LEFT, 0.07f, 0.05f)!!.toDouble(), 1e-5)
    }

    fun `test given each axis then holdLabel shows the arrow glyphs`() {
        assertEquals("← →", AceResize.holdLabel(AceResize.Axis.HORIZONTAL))
        assertEquals("↓ ↑", AceResize.holdLabel(AceResize.Axis.VERTICAL))
        assertEquals("←→↓↑", AceResize.holdLabel(AceResize.Axis.BOTH))
    }

    fun `test given each direction then toolWindowAction maps to the platform id`() {
        assertEquals("ResizeToolWindowLeft", AceResize.toolWindowAction(AceResize.Dir.LEFT))
        assertEquals("ResizeToolWindowRight", AceResize.toolWindowAction(AceResize.Dir.RIGHT))
        assertEquals("ResizeToolWindowDown", AceResize.toolWindowAction(AceResize.Dir.DOWN))
        assertEquals("ResizeToolWindowUp", AceResize.toolWindowAction(AceResize.Dir.UP))
    }

    fun `test given twelve dividers then ace-resize labels follow the avy subdivision`() {
        given("ace-resize labels", "text")
        AceResize.begin(ed, st, targets(12))
        val labels = Avy.labels(st.aceResize!!.node!!).map { it.second }
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "la", "ls", "ld", "lf"), labels)
        assertEquals(AceResize.Phase.PICK, st.aceResize!!.phase)
    }

    fun `test given a hint key then ace-resize enters the hold on that divider`() {
        given("ace-resize pick", "text")
        AceResize.begin(ed, st, targets(3))
        whenKeys("s")
        assertNotNull(st.aceResize)
        assertEquals(AceResize.Phase.HOLD, st.aceResize!!.phase)
    }

    fun `test given a held divider then h l j k resize it and the hold stays`() {
        given("ace-resize hold keys", "text")
        val moves = mutableListOf<AceResize.Dir>()
        AceResize.begin(ed, st, listOf(AceResize.Target(Rectangle(0, 0, 10, 10)) { moves.add(it) }))
        whenKeys("a")
        assertEquals(AceResize.Phase.HOLD, st.aceResize!!.phase)
        whenKeys("l")
        whenKeys("k")
        whenKeys("h")
        whenKeys("j")
        assertEquals(
            listOf(AceResize.Dir.RIGHT, AceResize.Dir.UP, AceResize.Dir.LEFT, AceResize.Dir.DOWN),
            moves,
        )
        assertNotNull(st.aceResize)
    }

    fun `test given each axis then accepts admits only its directions`() {
        assertTrue(AceResize.accepts(AceResize.Axis.HORIZONTAL, AceResize.Dir.LEFT))
        assertTrue(AceResize.accepts(AceResize.Axis.HORIZONTAL, AceResize.Dir.RIGHT))
        assertFalse(AceResize.accepts(AceResize.Axis.HORIZONTAL, AceResize.Dir.UP))
        assertFalse(AceResize.accepts(AceResize.Axis.HORIZONTAL, AceResize.Dir.DOWN))
        assertTrue(AceResize.accepts(AceResize.Axis.VERTICAL, AceResize.Dir.UP))
        assertTrue(AceResize.accepts(AceResize.Axis.VERTICAL, AceResize.Dir.DOWN))
        assertFalse(AceResize.accepts(AceResize.Axis.VERTICAL, AceResize.Dir.LEFT))
        assertFalse(AceResize.accepts(AceResize.Axis.VERTICAL, AceResize.Dir.RIGHT))
        assertTrue(AceResize.accepts(AceResize.Axis.BOTH, AceResize.Dir.UP))
    }

    fun `test given a horizontal divider then only h and l resize it and j k stay held`() {
        given("ace-resize horizontal hold", "text")
        val moves = mutableListOf<AceResize.Dir>()
        AceResize.begin(
            ed,
            st,
            listOf(AceResize.Target(Rectangle(0, 0, 10, 10), AceResize.Axis.HORIZONTAL) { moves.add(it) }),
        )
        whenKeys("a")
        whenKeys("l")
        whenKeys("h")
        whenKeys("j")
        whenKeys("k")
        assertEquals(listOf(AceResize.Dir.RIGHT, AceResize.Dir.LEFT), moves)
        assertNotNull(st.aceResize)
    }

    fun `test given a vertical divider then only j and k resize it and h l stay held`() {
        given("ace-resize vertical hold", "text")
        val moves = mutableListOf<AceResize.Dir>()
        AceResize.begin(
            ed,
            st,
            listOf(AceResize.Target(Rectangle(0, 0, 10, 10), AceResize.Axis.VERTICAL) { moves.add(it) }),
        )
        whenKeys("a")
        whenKeys("j")
        whenKeys("k")
        whenKeys("h")
        whenKeys("l")
        assertEquals(listOf(AceResize.Dir.DOWN, AceResize.Dir.UP), moves)
        assertNotNull(st.aceResize)
    }

    fun `test given plain arrow presses then arrowDir maps them and modified or non-arrow keys are null`() {
        assertEquals(AceResize.Dir.LEFT, AceResizeArrows.arrowDir(pressed(KeyEvent.VK_LEFT)))
        assertEquals(AceResize.Dir.RIGHT, AceResizeArrows.arrowDir(pressed(KeyEvent.VK_RIGHT)))
        assertEquals(AceResize.Dir.DOWN, AceResizeArrows.arrowDir(pressed(KeyEvent.VK_DOWN)))
        assertEquals(AceResize.Dir.UP, AceResizeArrows.arrowDir(pressed(KeyEvent.VK_UP)))
        assertNull(AceResizeArrows.arrowDir(pressed(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK)))
        assertNull(AceResizeArrows.arrowDir(pressed(KeyEvent.VK_A)))
    }

    fun `test given a held horizontal divider then holdArrow resizes on axis and stays held`() {
        given("ace-resize arrows hold", "text")
        val moves = mutableListOf<AceResize.Dir>()
        AceResize.begin(
            ed,
            st,
            listOf(AceResize.Target(Rectangle(0, 0, 10, 10), AceResize.Axis.HORIZONTAL) { moves.add(it) }),
        )
        whenKeys("a")
        assertTrue(AceResize.holdArrow(st, AceResize.Dir.RIGHT))
        assertTrue(AceResize.holdArrow(st, AceResize.Dir.UP))
        assertTrue(AceResize.holdArrow(st, AceResize.Dir.LEFT))
        assertEquals(listOf(AceResize.Dir.RIGHT, AceResize.Dir.LEFT), moves)
        assertNotNull(st.aceResize)
    }

    fun `test given no hold or the pick phase then holdArrow does not consume`() {
        given("ace-resize arrows idle", "text")
        assertFalse(AceResize.holdArrow(st, AceResize.Dir.LEFT))
        AceResize.begin(ed, st, targets(3))
        assertEquals(AceResize.Phase.PICK, st.aceResize!!.phase)
        assertFalse(AceResize.holdArrow(st, AceResize.Dir.LEFT))
    }

    fun `test given any non-hjkl key during the hold then ace-resize exits`() {
        given("ace-resize exit", "text")
        val moves = mutableListOf<AceResize.Dir>()
        AceResize.begin(ed, st, listOf(AceResize.Target(Rectangle(0, 0, 10, 10)) { moves.add(it) }))
        whenKeys("a")
        whenKeys("x")
        assertNull(st.aceResize)
        assertTrue(moves.isEmpty())
    }

    fun `test given a subtree key then ace-resize keeps the remaining suffixes in the pick`() {
        given("ace-resize subtree", "text")
        AceResize.begin(ed, st, targets(12))
        whenKeys("l")
        assertEquals(AceResize.Phase.PICK, st.aceResize!!.phase)
        assertEquals(listOf("a", "s", "d", "f"), Avy.labels(st.aceResize!!.node!!).map { it.second })
    }

    fun `test given a bad hint key then ace-resize hints and stays in the pick`() {
        given("ace-resize bad key", "text")
        AceResize.begin(ed, st, targets(3))
        whenKeys("x")
        assertNotNull(st.aceResize)
        assertEquals(AceResize.Phase.PICK, st.aceResize!!.phase)
    }

    fun `test given ESC during the pick then ace-resize cancels`() {
        given("ace-resize esc pick", "text")
        AceResize.begin(ed, st, targets(3))
        assertTrue(MeowEscape.wants(ed, st))
        pressEsc()
        assertNull(st.aceResize)
    }

    fun `test given ESC during the hold then ace-resize cancels`() {
        given("ace-resize esc hold", "text")
        AceResize.begin(ed, st, listOf(AceResize.Target(Rectangle(0, 0, 10, 10)) {}))
        whenKeys("a")
        assertEquals(AceResize.Phase.HOLD, st.aceResize!!.phase)
        assertTrue(MeowEscape.wants(ed, st))
        pressEsc()
        assertNull(st.aceResize)
    }

    fun `test given no dividers then ace-resize arms no session`() {
        given("ace-resize empty", "text")
        AceResize.begin(ed, st, emptyList())
        assertNull(st.aceResize)
    }

    fun `test given the bundled rc then SPC w r runs ace-resize`() {
        assertEquals("ace-resize", Rc.defaults().keypad["wr"]?.command)
        assertTrue(Engine.COMMANDS.containsKey("ace-resize"))
    }

    fun `test given a wr keypad entry then SPC w r dispatches it`() {
        given("spc wr dispatch", "text")
        givenRc("map <leader>wr meow-insert")
        whenKeys(" ")
        thenMode(MeowMode.KEYPAD)
        whenKeys("wr")
        thenMode(MeowMode.INSERT)
    }
}
