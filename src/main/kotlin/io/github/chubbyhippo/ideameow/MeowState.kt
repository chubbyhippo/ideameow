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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.WindowManager
import javax.swing.Timer

enum class MeowMode { NORMAL, INSERT, MOTION, KEYPAD }

/**
 * Selection types mirror meow's (expand/select . type) pairs: [expand] is the
 * cdr flag that makes follow-up commands of the same family extend the
 * selection instead of re-creating it (meow-mark-word -> meow-next-word).
 */
enum class SelType { NONE, CHAR, WORD, SYMBOL, LINE, BLOCK, FIND, TILL, VISIT, JOIN, TRANSIENT }

/** Commands that read one more key before acting. */
enum class Pending { FIND, TILL, FIND_EXPAND, INNER, BOUNDS, BEGIN, END, DESCRIBE }

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
    var lastIsTill = false

    /** last() is the active pattern, meow's regexp-search-ring. */
    val searchHistory = ArrayDeque<Regex>()
    val selectionHistory = ArrayDeque<Pair<Int, Int>>()

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
    val hints = mutableListOf<Inlay<*>>()
    var hintTimer: Timer? = null

    fun takeCount(default: Int = 1): Int {
        val n = if (pendingCount == 0) default else pendingCount
        val r = if (negative) -n else n
        pendingCount = 0
        negative = false
        return r
    }
}

object Meow {
    val KEY: Key<MeowState> = Key.create("meow.state")

    fun state(editor: Editor): MeowState? = editor.getUserData(KEY)

    fun setMode(editor: Editor, st: MeowState, mode: MeowMode) {
        st.mode = mode
        if (mode != MeowMode.KEYPAD) st.keypad.setLength(0)
        editor.settings.isBlockCursor = mode != MeowMode.INSERT
        updateWidgets()
    }

    fun updateWidgets() {
        for (p in ProjectManager.getInstance().openProjects) {
            WindowManager.getInstance().getStatusBar(p)?.updateWidget(MeowWidgetFactory.ID)
        }
    }

    /** Status text for the widget, derived from the focused editor. */
    fun statusText(project: Project): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return ""
        val st = state(editor) ?: return ""
        val beacon = editor.caretModel.caretCount > 1
        return when {
            st.mode == MeowMode.KEYPAD -> "MEOW KEYPAD  SPC ${st.keypad.toString().toCharArray().joinToString(" ")}"
            beacon && st.mode == MeowMode.INSERT -> "MEOW BEACON-INSERT"
            beacon -> "MEOW BEACON"
            else -> "MEOW ${st.mode}"
        }
    }
}
