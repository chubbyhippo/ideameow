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

class ToolWindowEscapeSpec : MeowSpec() {
    override fun setUp() {
        super.setUp()
        ToolWindowEscape.reset()
    }

    override fun tearDown() {
        ToolWindowEscape.reset()
        super.tearDown()
    }

    fun `test given a single escape in a tool window then it does not jump`() {
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_000))
    }

    fun `test given a second escape in the same tool window within the timeout then it jumps`() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertTrue(ToolWindowEscape.onEscape("Terminal", 1_000 + ToolWindowEscape.TIMEOUT_MS))
    }

    fun `test given a completed jump then the next escape starts a new pair`() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertTrue(ToolWindowEscape.onEscape("Terminal", 1_100))
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_200))
    }

    fun `test given escapes slower than the timeout then they do not pair but re-arm`() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_001 + ToolWindowEscape.TIMEOUT_MS))
        assertTrue(ToolWindowEscape.onEscape("Terminal", 1_200 + ToolWindowEscape.TIMEOUT_MS))
    }

    fun `test given escapes in different tool windows then they do not pair`() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertFalse(ToolWindowEscape.onEscape("AIAssistant", 1_100))
        assertTrue(ToolWindowEscape.onEscape("AIAssistant", 1_200))
    }

    fun `test given focus outside any tool window then the pair breaks`() {
        ToolWindowEscape.onEscape("Terminal", 1_000)
        assertFalse(ToolWindowEscape.onEscape(null, 1_100))
        assertFalse(ToolWindowEscape.onEscape("Terminal", 1_200))
    }
}
