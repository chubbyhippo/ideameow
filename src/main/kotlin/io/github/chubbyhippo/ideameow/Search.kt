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
        regex: Regex,
    ) {
        state.searchHistory.removeAll { it.pattern == regex.pattern }
        state.searchHistory.addLast(regex)
        while (state.searchHistory.size > SEARCH_RING_MAX) state.searchHistory.removeFirst()
    }

    private fun search(
        editor: Editor,
        state: MeowState,
    ) {
        val selectionModel = editor.selectionModel
        var regex = state.searchHistory.lastOrNull()
        if (selectionModel.hasSelection()) {
            val selectionText =
                editor.document.charsSequence
                    .subSequence(selectionModel.selectionStart, selectionModel.selectionEnd)
                    .toString()
            if (selectionText.isNotEmpty() && (regex == null || !regex.matches(selectionText))) {
                regex = Regex(Regex.escape(selectionText))
                push(state, regex)
            }
        }
        if (regex == null) {
            Ide.hint(editor, "No search pattern")
            return
        }
        searchWith(editor, state, regex, backward = state.takeCount(1) < 0 || Selections.backwardP(editor))
    }

    private fun visit(
        editor: Editor,
        state: MeowState,
    ) {
        val backward = state.takeCount(1) < 0
        val input = Messages.showInputDialog(editor.project, "Visit (regexp):", "Meow Visit", null)
        if (input.isNullOrEmpty()) return
        val regex =
            try {
                Regex(input)
            } catch (_: Exception) {
                Regex(Regex.escape(input))
            }
        push(state, regex)
        searchWith(editor, state, regex, backward)
    }

    private fun searchWith(
        editor: Editor,
        state: MeowState,
        regex: Regex,
        backward: Boolean,
    ) {
        val text = editor.document.charsSequence
        val caret = editor.caretModel.offset
        val match: MatchResult? =
            if (!backward) {
                regex.find(text, caret) ?: regex.find(text, 0)
            } else {
                lastMatchBefore(regex, text, caret)
            }
        if (match == null || match.value.isEmpty()) {
            Ide.hint(editor, "No match: ${regex.pattern}")
            return
        }
        val (mark, point) =
            if (backward) {
                match.range.last + 1 to match.range.first
            } else {
                match.range.first to match.range.last + 1
            }
        Selections.select(editor, state, SelType.VISIT, mark, point, expand = false)
    }

    private fun lastMatchBefore(
        regex: Regex,
        text: CharSequence,
        caret: Int,
    ): MatchResult? {
        var last: MatchResult? = null
        var current = regex.find(text, 0)
        while (current != null && current.range.last + 1 <= caret) {
            last = current
            current = current.next()
        }
        return last ?: generateSequence(current) { it.next() }.lastOrNull()
    }
}
