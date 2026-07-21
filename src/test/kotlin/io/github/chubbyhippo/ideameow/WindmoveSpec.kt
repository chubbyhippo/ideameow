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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.KeyStroke

class WindmoveSpec : MeowSpec() {
    private val frame = Dimension(80, 25)

    private val l1 = "L1" to Rectangle(0, 0, 40, 12)
    private val l2 = "L2" to Rectangle(0, 12, 40, 6)
    private val l3 = "L3" to Rectangle(0, 18, 40, 6)
    private val r = "R" to Rectangle(40, 0, 40, 24)

    private fun stacked(vararg but: String) = listOf(l1, l2, l3, r).filter { it.first !in but }

    fun `test given window rectangles then ace-window orders them left to right then top down`() {
        assertEquals(listOf("L1", "L2", "R"), AceWindow.ordered(listOf(r, l2, l1)))
    }

    fun `test given one two or many windows then ace-window plans self other or labels`() {
        assertEquals(AceWindow.Plan.NONE, AceWindow.plan(1))
        assertEquals(AceWindow.Plan.OTHER, AceWindow.plan(2))
        assertEquals(AceWindow.Plan.LABELS, AceWindow.plan(3))
        assertEquals(AceWindow.Plan.LABELS, AceWindow.plan(9))
    }

    private fun mixedWindows(panel: JPanel): List<AceWindow.Window> =
        listOf(
            AceWindow.Window(Rectangle(0, 0, 40, 24), ed, ed.contentComponent),
            AceWindow.Window(Rectangle(40, 0, 40, 24), null, panel),
            AceWindow.Window(Rectangle(80, 0, 40, 24), null, JPanel()),
        )

    fun `test given editor and preview windows then ace-window labels them all`() {
        given("ace preview labels", "text")
        AceWindow.begin(ed, st, swap = false, windows = mixedWindows(JPanel()))
        assertEquals(3, st.aceWindow!!.windows.size)
        assertEquals(3, Avy.labels(st.aceWindow!!.node!!).size)
        AceWindow.cancel(st)
    }

    fun `test given a preview window pick then ace-window focuses its component`() {
        given("ace preview focus", "text")
        AceWindow.begin(ed, st, swap = false, windows = mixedWindows(JPanel()))
        whenKeys("s")
        assertNull(st.aceWindow)
    }

    fun `test given a swap pick on a preview window then ace-window hints instead of swapping`() {
        given("ace preview swap", "text")
        AceWindow.begin(ed, st, swap = true, windows = mixedWindows(JPanel()))
        whenKeys("s")
        assertNull(st.aceWindow)
    }

    fun `test given the current window then ace-window other-window picks the first non-current`() {
        given("ace other current", "text")
        val windows = mixedWindows(JPanel())
        assertSame(windows[1], AceWindow.otherWindow(windows, windows[0]))
        assertSame(windows[0], AceWindow.otherWindow(windows, windows[1]))
    }

    fun `test given a pick on the current window then ace-window stays and the session clears`() {
        given("ace current stay", "text")
        AceWindow.begin(ed, st, swap = false, windows = mixedWindows(JPanel()))
        whenKeys("a")
        assertNull(st.aceWindow)
    }

    fun `test given trees tables and lists in a tool window then each pane is an ace window`() {
        val root = JPanel()
        val tree = JTree()
        val table = JTable()
        val list = JList<String>()
        root.add(JScrollPane(tree))
        root.add(JScrollPane(table))
        root.add(list)
        root.add(JButton())
        root.add(JLabel("x"))
        assertEquals(listOf<JComponent>(list, tree, table), panes(root))
    }

    fun `test given an invisible pane then ace-window skips it`() {
        val root = JPanel()
        root.add(JTree().apply { isVisible = false })
        assertTrue(panes(root).isEmpty())
    }

    fun `test given a scrolled pane then the ace rect comes from its scroll host`() {
        val tree = JTree()
        val scroll = JScrollPane(tree)
        assertSame(scroll, paneHost(tree))
        val bare = JTree()
        assertSame(bare, paneHost(bare))
    }

    fun `test given a stacked column when left then the window at the caret row is entered`() {
        assertEquals("L1", Windmove.pick(Windmove.Dir.LEFT, r.second, 1, frame, stacked("R")))
        assertEquals("L2", Windmove.pick(Windmove.Dir.LEFT, r.second, 14, frame, stacked("R")))
        assertEquals("L3", Windmove.pick(Windmove.Dir.LEFT, r.second, 23, frame, stacked("R")))
    }

    fun `test given a visible caret then it is the reference, else the near edge plus one`() {
        assertEquals(14, Windmove.reference(Windmove.Dir.LEFT, r.second, Point(60, 14)))
        assertEquals(1, Windmove.reference(Windmove.Dir.LEFT, r.second, null))
        assertEquals(1, Windmove.reference(Windmove.Dir.LEFT, r.second, Point(60, 99)))
        assertEquals(60, Windmove.reference(Windmove.Dir.UP, r.second, Point(60, 14)))
        assertEquals(41, Windmove.reference(Windmove.Dir.UP, r.second, null))
    }

    fun `test given the middle of the stack then it moves in all four directions`() {
        val caretY = 14
        val caretX = 5
        assertEquals("R", Windmove.pick(Windmove.Dir.RIGHT, l2.second, caretY, frame, stacked("L2")))
        assertEquals("L1", Windmove.pick(Windmove.Dir.UP, l2.second, caretX, frame, stacked("L2")))
        assertEquals("L3", Windmove.pick(Windmove.Dir.DOWN, l2.second, caretX, frame, stacked("L2")))
    }

    fun `test given no window in the direction then the pick is null`() {
        assertNull(Windmove.pick(Windmove.Dir.RIGHT, r.second, 14, frame, stacked("R")))
        assertNull(Windmove.pick(Windmove.Dir.UP, l1.second, 5, frame, stacked("L1")))
    }

    fun `test given a two by two grid then the adjacent window is picked`() {
        val a = "A" to Rectangle(0, 0, 40, 12)
        val b = "B" to Rectangle(40, 0, 40, 12)
        val c = "C" to Rectangle(0, 12, 40, 12)
        val d = "D" to Rectangle(40, 12, 40, 12)
        val fromD = listOf(a, b, c)
        assertEquals("C", Windmove.pick(Windmove.Dir.LEFT, d.second, 18, frame, fromD))
        assertEquals("B", Windmove.pick(Windmove.Dir.UP, d.second, 60, frame, fromD))
        val fromA = listOf(b, c, d)
        assertEquals("B", Windmove.pick(Windmove.Dir.RIGHT, a.second, 5, frame, fromA))
        assertEquals("C", Windmove.pick(Windmove.Dir.DOWN, a.second, 5, frame, fromA))
        assertNull(Windmove.pick(Windmove.Dir.LEFT, a.second, 5, frame, fromA))
    }

    fun `test given a window covering the caret then it beats a nearer one outside the band`() {
        val current = Rectangle(0, 100, 50, 50)
        val covering = "covering" to Rectangle(0, 20, 50, 40)
        val nearer = "nearer" to Rectangle(60, 95, 40, 5)
        val picked =
            Windmove.pick(
                Windmove.Dir.UP,
                current,
                10,
                Dimension(200, 200),
                listOf(covering, nearer),
            )
        assertEquals("covering", picked)
    }

    fun `test given only windows outside the band then the smallest band distance wins`() {
        val current = Rectangle(0, 100, 50, 50)
        val bandNear = "bandNear" to Rectangle(60, 20, 40, 40)
        val bandFar = "bandFar" to Rectangle(120, 50, 40, 40)
        val picked =
            Windmove.pick(
                Windmove.Dir.UP,
                current,
                10,
                Dimension(200, 200),
                listOf(bandFar, bandNear),
            )
        assertEquals("bandNear", picked)
    }

    fun `test given no window in the direction then the message is Emacs verbatim`() {
        assertEquals("No window left from selected window", Windmove.noWindowMessage(Windmove.Dir.LEFT))
        assertEquals("No window down from selected window", Windmove.noWindowMessage(Windmove.Dir.DOWN))
    }

    fun `test given the default keymap then shift+arrows are the windmove shortcuts`() {
        val expected =
            mapOf(
                "Ideameow.WindmoveLeft" to "shift LEFT",
                "Ideameow.WindmoveRight" to "shift RIGHT",
                "Ideameow.WindmoveUp" to "shift UP",
                "Ideameow.WindmoveDown" to "shift DOWN",
            )
        for ((id, key) in expected) {
            val action = ActionManager.getInstance().getAction(id)
            assertTrue("$id must be a windmove action", action is WindmoveAction)
            val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(id)
            assertTrue(
                "$id must default to $key",
                shortcuts.any {
                    it is KeyboardShortcut &&
                        it.firstKeyStroke == KeyStroke.getKeyStroke(key) &&
                        it.secondKeyStroke == null
                },
            )
        }
    }

    fun `test given the shift-selection conflict then the promoter puts windmove first`() {
        val windmove = WindmoveLeftAction()
        val other = ActionManager.getInstance().getAction("EditorLeftWithSelection")
        val promoted = WindmovePromoter().promote(mutableListOf(other, windmove), DataContext.EMPTY_CONTEXT)
        assertSame(windmove, promoted.first())
    }

    fun `test given the bundled rc then SPC w hjkl dispatch windmove`() {
        val d = Rc.defaults().keypad
        assertEquals("Ideameow.WindmoveLeft", d["wh"]?.action)
        assertEquals("Ideameow.WindmoveDown", d["wj"]?.action)
        assertEquals("Ideameow.WindmoveUp", d["wk"]?.action)
        assertEquals("Ideameow.WindmoveRight", d["wl"]?.action)
    }

    fun `test given the swap actions then no default chords are claimed`() {
        for (id in listOf(
            "Ideameow.WindmoveSwapLeft",
            "Ideameow.WindmoveSwapRight",
            "Ideameow.WindmoveSwapUp",
            "Ideameow.WindmoveSwapDown",
        )) {
            val action = ActionManager.getInstance().getAction(id)
            assertTrue("$id must be a windmove swap action", action is WindmoveSwapAction)
            assertEquals(
                "$id must not claim a keymap chord",
                0,
                KeymapManager
                    .getInstance()
                    .activeKeymap
                    .getShortcuts(id)
                    .size,
            )
        }
    }

    fun `test given the bundled rc then the swaps are on SPC w capitals`() {
        val d = Rc.defaults().keypad
        assertEquals("Ideameow.WindmoveSwapLeft", d["wH"]?.action)
        assertEquals("Ideameow.WindmoveSwapDown", d["wJ"]?.action)
        assertEquals("Ideameow.WindmoveSwapUp", d["wK"]?.action)
        assertEquals("Ideameow.WindmoveSwapRight", d["wL"]?.action)
    }
}
