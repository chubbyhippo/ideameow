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

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class RecenterSpec : MeowSpec() {
    private val controlL = ChordKey.of(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK)

    fun `test given the recenter cycle then the positions follow Emacs recenter-positions`() {
        assertEquals(
            listOf(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM, RevealAt.CENTER),
            (0..3).map { View.recenterPosition(it) },
        )
    }

    fun `test given a different previous command then the recenter cycle starts over`() {
        assertEquals(1, View.nextRecenterPhase(View.RECENTER_COMMAND, 0))
        assertEquals(3, View.nextRecenterPhase(View.RECENTER_COMMAND, 2))
        assertEquals(0, View.nextRecenterPhase("meow-left", 2))
        assertEquals(0, View.nextRecenterPhase(null, 2))
    }

    fun `test given repeated C-l then the phase cycles center top bottom like Emacs`() {
        given("a caret mid-buffer", "one\ntwo\nthr<caret>ee\nfour\nfive\n")
        val seen =
            (1..4).map {
                whenCommand(View.RECENTER_COMMAND)
                View.recenterPosition(st.recenterPhase)
            }
        assertEquals(listOf(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM, RevealAt.CENTER), seen)
    }

    fun `test given a motion between two C-l then the second one centers again`() {
        given("a caret mid-buffer", "one\ntwo\nthr<caret>ee\nfour\nfive\n")
        whenCommand(View.RECENTER_COMMAND)
        whenKeys("h")
        whenCommand(View.RECENTER_COMMAND)
        assertEquals(RevealAt.CENTER, View.recenterPosition(st.recenterPhase))
    }

    fun `test given the bundled rc then C-l runs recenter-top-bottom`() {
        assertEquals(View.RECENTER_COMMAND, RcLookups.chords()[controlL]?.command)
    }
}
