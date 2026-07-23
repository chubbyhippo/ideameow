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

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.RangeHighlighter
import javax.swing.JComponent
import javax.swing.Timer

enum class MeowMode { NORMAL, INSERT, MOTION, KEYPAD }

enum class SelType { NONE, CHAR, WORD, SYMBOL, LINE, BLOCK, FIND, TILL, VISIT, JOIN, TRANSIENT }

enum class Pending { FIND, TILL, INNER, BOUNDS, BEGIN, END }

data class SavedSelection(
    val type: SelType?,
    val expand: Boolean,
    val mark: Int,
    val point: Int,
)

class MeowState {
    var mode = MeowMode.NORMAL
    var selType = SelType.NONE
    var selExpand = false
    var pending: Pending? = null

    var pendingCount = 0
    var negative = false

    var lastFind: Char? = null

    val searchHistory = ArrayDeque<Regex>()

    val selectionHistory = ArrayDeque<SavedSelection>()

    var lastSelection: SavedSelection? = null

    var goalColumn: Int? = null

    var lastCommand: String? = null

    var grab: RangeMarker? = null
    var grabHighlighter: RangeHighlighter? = null

    val keypad = StringBuilder()

    var keypadPreviousMode = MeowMode.NORMAL
    val unitKeys = mutableListOf<Char>()
    var lastKeys: List<Char> = emptyList()
    var replaying = false

    var replayDepth = 0
    var noremapDepth = 0

    var savedBlockCursor: Boolean? = null

    var hintOverlay: JComponent? = null
    var hintTimer: Timer? = null

    var avy: Avy.Session? = null

    var aceWindow: AceWindow.Session? = null

    var aceClick: AceClick.Session? = null

    var aceResize: AceResize.Session? = null

    fun takeCount(default: Int = 1): Int {
        val n = if (pendingCount == 0) default else pendingCount
        val r = if (negative) -n else n
        pendingCount = 0
        negative = false
        return r
    }
}
