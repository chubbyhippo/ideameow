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
import javax.swing.KeyStroke

/**
 * The window surface: windmove, ported from windmove.el + window.el's
 * window-in-direction (Emacs 30.2). The picking geometry is pinned against
 * a batch-Emacs probe of real window layouts (the same layouts below;
 * sign-based references stand in for caret rows there, because
 * posn-at-point is nil in batch — the caret path itself is window.el's
 * three-line posn arithmetic, ported verbatim). The user-error message is
 * batch-verified character for character. Layouts use the probe frame's
 * pixel geometry: 80x25, left column L1(h12)/L2(h6)/L3(h6), right R(h24).
 */
class WindmoveSpec : MeowSpec() {

    private val frame = Dimension(80, 25)

    /**
     * +----+------+
     * | L1 |      |
     * +----+  R   |
     * | L2 |      |
     * +----+      |
     * | L3 |      |
     * +----+------+
     */
    private val l1 = "L1" to Rectangle(0, 0, 40, 12)
    private val l2 = "L2" to Rectangle(0, 12, 40, 6)
    private val l3 = "L3" to Rectangle(0, 18, 40, 6)
    private val r = "R" to Rectangle(40, 0, 40, 24)

    private fun stacked(vararg but: String) =
        listOf(l1, l2, l3, r).filter { it.first !in but }

    // ------------------------------------------------------- the caret band

    fun testLeftEntersTheStackedWindowAtTheCaretRow() {
        // probed: reference at the top edge -> L1, at the bottom edge -> L3
        // (batch sign probes); the caret row in L2's band -> L2 is the
        // posn-at-point path, window.el arithmetic
        assertEquals("L1", Windmove.pick(Windmove.Dir.LEFT, r.second, 1, frame, stacked("R")))
        assertEquals("L2", Windmove.pick(Windmove.Dir.LEFT, r.second, 14, frame, stacked("R")))
        assertEquals("L3", Windmove.pick(Windmove.Dir.LEFT, r.second, 23, frame, stacked("R")))
    }

    fun testReferenceIsTheCaretInsideTheWindowElseEdgePlusOne() {
        // window.el: posn-at-point when visible, else the (or ... 1) fallback
        assertEquals(14, Windmove.reference(Windmove.Dir.LEFT, r.second, Point(60, 14)))
        assertEquals(1, Windmove.reference(Windmove.Dir.LEFT, r.second, null))
        assertEquals(1, Windmove.reference(Windmove.Dir.LEFT, r.second, Point(60, 99)))
        assertEquals(60, Windmove.reference(Windmove.Dir.UP, r.second, Point(60, 14)))
        assertEquals(41, Windmove.reference(Windmove.Dir.UP, r.second, null))
    }

    fun testTheMiddleOfTheStackMovesInAllFourDirections() {
        // probed: from L2 — right -> R, up -> L1, down -> L3
        val caretY = 14
        val caretX = 5
        assertEquals("R", Windmove.pick(Windmove.Dir.RIGHT, l2.second, caretY, frame, stacked("L2")))
        assertEquals("L1", Windmove.pick(Windmove.Dir.UP, l2.second, caretX, frame, stacked("L2")))
        assertEquals("L3", Windmove.pick(Windmove.Dir.DOWN, l2.second, caretX, frame, stacked("L2")))
    }

    fun testNoWindowInTheDirectionIsNull() {
        // probed: R right -> nil, L1 up -> nil (windmove-wrap-around nil)
        assertNull(Windmove.pick(Windmove.Dir.RIGHT, r.second, 14, frame, stacked("R")))
        assertNull(Windmove.pick(Windmove.Dir.UP, l1.second, 5, frame, stacked("L1")))
    }

    fun testTheGridPicksTheAdjacentWindow() {
        // probed 2x2 grid: D left -> C, D up -> B; A right -> B, down -> C,
        // left -> nil
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

    // ---------------------------------------------- the two tiers (window.el)

    fun testAWindowCoveringTheCaretBeatsANearerOneOutsideTheBand() {
        // window-in-direction tier 1 before tier 2: editors don't tile the
        // frame like Emacs windows, so both tiers matter here (a diff pane
        // above-left, a lone editor nearer but off the caret's column)
        val current = Rectangle(0, 100, 50, 50)
        val covering = "covering" to Rectangle(0, 20, 50, 40) // bottom edge 60
        val nearer = "nearer" to Rectangle(60, 95, 40, 5) // bottom edge 100
        val picked = Windmove.pick(
            Windmove.Dir.UP, current, 10, Dimension(200, 200), listOf(covering, nearer),
        )
        assertEquals("covering", picked)
    }

    fun testOutsideTheBandTheSmallestBandDistanceWins() {
        // tier 2 orders by distance to the caret band (window--in-direction-2),
        // not by nearness in the movement direction
        val current = Rectangle(0, 100, 50, 50)
        val bandNear = "bandNear" to Rectangle(60, 20, 40, 40) // band starts at x=60
        val bandFar = "bandFar" to Rectangle(120, 50, 40, 40) // nearer above, band x=120
        val picked = Windmove.pick(
            Windmove.Dir.UP, current, 10, Dimension(200, 200), listOf(bandFar, bandNear),
        )
        assertEquals("bandNear", picked)
    }

    // ------------------------------------------------------ the Emacs surface

    fun testTheNoWindowMessageIsEmacsVerbatim() {
        // batch-verified: (windmove-do-window-select 'left) with one window
        assertEquals("No window left from selected window", Windmove.noWindowMessage(Windmove.Dir.LEFT))
        assertEquals("No window down from selected window", Windmove.noWindowMessage(Windmove.Dir.DOWN))
    }

    fun testWindmoveDefaultKeybindingsAreShiftArrowsOnTheDefaultKeymap() {
        // (windmove-default-keybindings) == shift + left/right/up/down
        val expected = mapOf(
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

    fun testThePromoterPutsWindmoveBeforeTheShiftSelectionActions() {
        // the shift+arrow conflict with EditorLeftWithSelection & friends is
        // resolved by promotion — windmove wins, as in Emacs
        val windmove = WindmoveLeftAction()
        val other = ActionManager.getInstance().getAction("EditorLeftWithSelection")
        val promoted = WindmovePromoter().promote(mutableListOf(other, windmove), DataContext.EMPTY_CONTEXT)
        assertSame(windmove, promoted.first())
    }

    fun testBundledRcPutsWindmoveOnSpcWHjkl() {
        val d = Rc.defaults().keypad
        assertEquals("Ideameow.WindmoveLeft", d["wh"]?.action)
        assertEquals("Ideameow.WindmoveDown", d["wj"]?.action)
        assertEquals("Ideameow.WindmoveUp", d["wk"]?.action)
        assertEquals("Ideameow.WindmoveRight", d["wl"]?.action)
    }

    fun testSwapActionsAreRegisteredWithoutDefaultChords() {
        // windmove-swap-states-default-keybindings is never called in
        // init.el — the swaps live only on the C-c w map, so only on SPC w
        for (id in listOf(
            "Ideameow.WindmoveSwapLeft", "Ideameow.WindmoveSwapRight",
            "Ideameow.WindmoveSwapUp", "Ideameow.WindmoveSwapDown",
        )) {
            val action = ActionManager.getInstance().getAction(id)
            assertTrue("$id must be a windmove swap action", action is WindmoveSwapAction)
            assertEquals(
                "$id must not claim a keymap chord",
                0, KeymapManager.getInstance().activeKeymap.getShortcuts(id).size,
            )
        }
    }

    fun testBundledRcPutsTheSwapsOnSpcWCapitals() {
        // init.el: the capitals mirror the h/j/k/l moves
        val d = Rc.defaults().keypad
        assertEquals("Ideameow.WindmoveSwapLeft", d["wH"]?.action)
        assertEquals("Ideameow.WindmoveSwapDown", d["wJ"]?.action)
        assertEquals("Ideameow.WindmoveSwapUp", d["wK"]?.action)
        assertEquals("Ideameow.WindmoveSwapRight", d["wL"]?.action)
    }
}
