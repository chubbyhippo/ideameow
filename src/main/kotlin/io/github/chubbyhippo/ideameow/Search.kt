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
            "meow-search" to MeowCommand { editor, state -> search(editor, state) },
            "meow-visit" to MeowCommand { editor, state -> visit(editor, state) },
        )

    fun push(
        state: MeowState,
        re: Regex,
    ) {
        state.searchHistory.removeAll { it.pattern == re.pattern }
        state.searchHistory.addLast(re)
        while (state.searchHistory.size > SEARCH_RING_MAX) state.searchHistory.removeFirst()
    }

    private fun search(
        editor: Editor,
        state: MeowState,
    ) {
        val sm = editor.selectionModel
        var re = state.searchHistory.lastOrNull()
        if (sm.hasSelection()) {
            val selText =
                editor.document.charsSequence
                    .subSequence(sm.selectionStart, sm.selectionEnd)
                    .toString()
            if (selText.isNotEmpty() && (re == null || !re.matches(selText))) {
                re = Regex(Regex.escape(selText))
                push(state, re)
            }
        }
        if (re == null) {
            Ide.hint(editor, "No search pattern")
            return
        }
        searchWith(editor, state, re, backward = state.takeCount(1) < 0 || Selections.backwardP(editor))
    }

    private fun visit(
        editor: Editor,
        state: MeowState,
    ) {
        val backward = state.takeCount(1) < 0
        val input = Messages.showInputDialog(editor.project, "Visit (regexp):", "Meow Visit", null)
        if (input.isNullOrEmpty()) return
        val re =
            try {
                Regex(input)
            } catch (_: Exception) {
                Regex(Regex.escape(input))
            }
        push(state, re)
        searchWith(editor, state, re, backward)
    }

    private fun searchWith(
        editor: Editor,
        state: MeowState,
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
        val (mark, point) =
            if (backward) {
                m.range.last + 1 to m.range.first
            } else {
                m.range.first to m.range.last + 1
            }
        Selections.select(editor, state, SelType.VISIT, mark, point, expand = false)
    }
}
