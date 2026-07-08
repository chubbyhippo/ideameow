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

/**
 * Double-ESC in a tool window (ToolWindowEscape). Platform-specific — no
 * meow/Emacs source of truth: single ESC already leaves most tool windows
 * natively, and this covers the ones that swallow it (terminal, AI chat).
 * What IS pinned here is the pairing state machine the AWT dispatcher
 * feeds: same tool window twice within the timeout jumps, everything else
 * re-arms or breaks the pair.
 */
class ToolWindowEscapeSpec : MeowSpec() {

    override fun setUp() {
        super.setUp()
        ToolWindowEscape.reset()
    }

    override fun tearDown() {
        ToolWindowEscape.reset()
        super.tearDown()
    }

    fun testTheFirstEscapeInAToolWindowDoesNotJump() {
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_000))
    }

    fun testASecondEscapeInTheSameToolWindowWithinTheTimeoutJumps() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertTrue(ToolWindowEscape.onEscape("Terminal", 1_000 + ToolWindowEscape.TIMEOUT_MS))
    }

    fun testAJumpConsumesThePairSoTheNextEscapeStartsANewOne() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertTrue(ToolWindowEscape.onEscape("Terminal", 1_100))
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_200))
    }

    fun testEscapesSlowerThanTheTimeoutDoNotPairButReArm() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_001 + ToolWindowEscape.TIMEOUT_MS))
        assertTrue(ToolWindowEscape.onEscape("Terminal", 1_200 + ToolWindowEscape.TIMEOUT_MS))
    }

    fun testEscapesInDifferentToolWindowsDoNotPair() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertFalse(ToolWindowEscape.onEscape("AIAssistant", 1_100))
        assertTrue(ToolWindowEscape.onEscape("AIAssistant", 1_200))
    }

    fun testFocusOutsideAnyToolWindowBreaksThePair() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertFalse(ToolWindowEscape.onEscape(null, 1_100))
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_200))
    }
}
