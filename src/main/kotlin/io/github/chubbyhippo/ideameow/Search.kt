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
import com.intellij.openapi.ui.Messages

internal object Search {
    private const val SEARCH_RING_MAX = 50

    val commands: Map<String, MeowCommand> =
        mapOf(
            "meow-search" to MeowCommand { ed, st, _ -> search(ed, st) },
            "meow-visit" to MeowCommand { ed, st, _ -> visit(ed, st) },
        )

    fun push(
        st: MeowState,
        re: Regex,
    ) {
        st.searchHistory.removeAll { it.pattern == re.pattern }
        st.searchHistory.addLast(re)
        while (st.searchHistory.size > SEARCH_RING_MAX) st.searchHistory.removeFirst()
    }

    private fun search(
        editor: Editor,
        st: MeowState,
    ) {
        val sm = editor.selectionModel
        var re = st.searchHistory.lastOrNull()
        if (sm.hasSelection()) {
            val selText =
                editor.document.charsSequence
                    .subSequence(sm.selectionStart, sm.selectionEnd)
                    .toString()
            if (selText.isNotEmpty() && (re == null || !re.matches(selText))) {
                re = Regex(Regex.escape(selText))
                push(st, re)
            }
        }
        if (re == null) {
            Ide.hint(editor, "No search pattern")
            return
        }
        searchWith(editor, st, re, backward = st.takeCount(1) < 0 || Selections.backwardP(editor))
    }

    private fun visit(
        editor: Editor,
        st: MeowState,
    ) {
        val backward = st.takeCount(1) < 0
        val input = Messages.showInputDialog(editor.project, "Visit (regexp):", "Meow Visit", null)
        if (input.isNullOrEmpty()) return
        val re =
            try {
                Regex(input)
            } catch (_: Exception) {
                Regex(Regex.escape(input))
            }
        push(st, re)
        searchWith(editor, st, re, backward)
    }

    private fun searchWith(
        editor: Editor,
        st: MeowState,
        re: Regex,
        backward: Boolean,
    ) {
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val m: MatchResult? =
            if (!backward) {
                re.find(text, caret) ?: re.find(text, 0)
            } else {
                var last: MatchResult? = null
                var cur = re.find(text, 0)
                while (cur != null && cur.range.last + 1 <= caret) {
                    last = cur
                    cur = cur.next()
                }
                if (last == null) {
                    var tail: MatchResult? = cur
                    while (true) {
                        val nx = tail?.next() ?: break
                        tail = nx
                    }
                    last = tail
                }
                last
            }
        if (m == null || m.value.isEmpty()) {
            Ide.hint(editor, "No match: ${re.pattern}")
            return
        }
        if (!backward) {
            Selections.select(editor, st, SelType.VISIT, m.range.first, m.range.last + 1, expand = false)
        } else {
            Selections.select(editor, st, SelType.VISIT, m.range.last + 1, m.range.first, expand = false)
        }
    }
}
