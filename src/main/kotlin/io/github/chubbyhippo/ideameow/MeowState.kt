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

/**
 * Selection types mirror meow's (expand/select . type) pairs: [MeowState.selExpand]
 * is the cdr flag that makes follow-up commands of the same family extend the
 * selection instead of re-creating it (meow-mark-word -> meow-next-word).
 */
enum class SelType { NONE, CHAR, WORD, SYMBOL, LINE, BLOCK, FIND, TILL, VISIT, JOIN, TRANSIENT }

/** Commands that read one more key before acting. */
enum class Pending { FIND, TILL, INNER, BOUNDS, BEGIN, END }

/**
 * A recorded selection, meow--selection style: a null [type] is the
 * placeholder meow pushes when a selection is created from nothing —
 * popping it returns the caret to where the selection chain started.
 */
data class SavedSelection(
    val type: SelType?,
    val expand: Boolean,
    val anchor: Int,
    val active: Int,
)

/** Everything meow remembers about one editor, stored in its user data. */
class MeowState {
    var mode = MeowMode.NORMAL
    var selType = SelType.NONE
    var selExpand = false
    var pending: Pending? = null

    // digit-argument (keypad SPC 1-9, or plain digits with no selection) and
    // negative-argument, consumed by the next command
    var pendingCount = 0
    var negative = false

    var lastFind: Char? = null

    /** last() is the active pattern, meow's regexp-search-ring. */
    val searchHistory = ArrayDeque<Regex>()

    /** meow--selection-history; cleared by meow--cancel-selection. */
    val selectionHistory = ArrayDeque<SavedSelection>()

    /** meow--selection: survives region-killing edits (stale on purpose). */
    var lastSelection: SavedSelection? = null

    /** temporary-goal-column for consecutive vertical moves (j/k chains). */
    var goalColumn: Int? = null

    /** last dispatched command name — the this-command/last-command handoff. */
    var lastCommand: String? = null

    var grab: RangeMarker? = null
    var grabHighlighter: RangeHighlighter? = null

    val keypad = StringBuilder()
    val unit = mutableListOf<Char>()
    var lastKeys: List<Char> = emptyList()
    var replaying = false

    // ~/.ideameowrc binding replay: recursion guard, and noremap bypass depth
    var replayDepth = 0
    var noremapDepth = 0

    var savedBlockCursor: Boolean? = null

    /** The expand-hint paint-over canvas (see ExpandHints) and its 1 s timer. */
    var hintOverlay: JComponent? = null
    var hintTimer: Timer? = null

    /** An active avy jump (S / Q), consuming keys until it lands or cancels. */
    var avy: Avy.Session? = null

    /** The armed repeat transient (Emacs repeat-mode, see Rc repeat groups):
     *  member keys re-dispatch their binding, any other key or ESC ends the
     *  run and falls through to the normal map. */
    var repeatMap: Map<Char, Rc.Binding>? = null

    fun takeCount(default: Int = 1): Int {
        val n = if (pendingCount == 0) default else pendingCount
        val r = if (negative) -n else n
        pendingCount = 0
        negative = false
        return r
    }
}
