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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor

private fun casified(
    slice: String,
    op: CaseOp,
): String =
    when (op) {
        CaseOp.UPCASE -> slice.uppercase()
        CaseOp.DOWNCASE -> slice.lowercase()
        CaseOp.CAPITALIZE -> capitalizedWords(slice)
    }

private fun capitalizedWords(slice: String): String {
    val pred = charPred(symbol = false)
    val out = StringBuilder(slice.length)
    var inWord = false
    for (char in slice) {
        if (pred(char)) {
            out.append(if (inWord) char.lowercaseChar() else char.uppercaseChar())
            inWord = true
        } else {
            out.append(char)
            inWord = false
        }
    }
    return out.toString()
}

internal fun caseWord(
    editor: Editor,
    state: MeowState,
    op: CaseOp,
) {
    if (Edits.blockedReadOnly(editor)) return
    val count = state.takeCount(1)
    if (count == 0) return
    val hadSelection = editor.selectionModel.hasSelection()
    val pred = charPred(symbol = false)
    editCarets(editor, op.commandName) { caret ->
        val text = editor.document.charsSequence
        val from = caret.offset
        val target = Words.move(text, from, count, pred)
        val start = minOf(from, target)
        val end = maxOf(from, target)
        if (start == end) return@editCarets
        editor.document.replaceString(start, end, casified(text.subSequence(start, end).toString(), op))
        if (count > 0) caret.moveToOffset(end)
    }
    if (hadSelection) Selections.collapse(editor, state)
}

internal fun killWord(
    editor: Editor,
    state: MeowState,
) {
    if (Edits.blockedReadOnly(editor)) return
    val count = state.takeCount(1)
    if (count == 0) return
    val text = editor.document.charsSequence
    val pred = charPred(symbol = false)
    var any = false
    for (caret in editor.caretModel.allCarets) {
        val from = caret.offset
        val target = Words.move(text, from, count, pred)
        if (target == from) {
            caret.removeSelection()
            continue
        }
        caret.setSelection(minOf(from, target), maxOf(from, target))
        any = true
    }
    if (any) Ide.act(editor, IdeActions.ACTION_EDITOR_CUT)
    state.selType = SelType.NONE
    state.selExpand = false
}
