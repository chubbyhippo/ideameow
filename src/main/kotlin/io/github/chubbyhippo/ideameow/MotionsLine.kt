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
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.ui.Messages
import kotlin.math.abs

internal fun line(
    editor: Editor,
    state: MeowState,
) {
    val doc = editor.document
    if (doc.textLength == 0) return
    val count = state.takeCount(1)
    val caretLine = doc.getLineNumber(editor.caretModel.offset)
    if (state.selType == SelType.LINE && state.selExpand && editor.selectionModel.hasSelection()) {
        val point = lineExpandPoint(doc, caretLine, abs(count), Selections.backwardP(editor))
        Selections.select(
            editor,
            state,
            Selections.SelectionSpec(SelType.LINE, Selections.mark(editor), point, expand = true),
        )
        return
    }
    val (mark, point) =
        if (count < 0) {
            doc.getLineEndOffset(caretLine) to doc.getLineStartOffset((caretLine + count + 1).coerceAtLeast(0))
        } else {
            doc.getLineStartOffset(caretLine) to
                doc.getLineEndOffset((caretLine + count - 1).coerceAtMost(doc.lineCount - 1))
        }
    Selections.select(editor, state, Selections.SelectionSpec(SelType.LINE, mark, point, expand = true))
}

internal fun gotoLine(
    editor: Editor,
    state: MeowState,
) {
    val input = Messages.showInputDialog(editor.project, "Goto line:", "Meow", null) ?: return
    val doc = editor.document
    val targetLine = if (doc.textLength == 0) null else parsedLineNumber(input, doc.lineCount)
    if (targetLine != null) {
        val spec =
            Selections.SelectionSpec(
                SelType.LINE,
                doc.getLineStartOffset(targetLine),
                doc.getLineEndOffset(targetLine),
                expand = true,
            )
        Selections.select(editor, state, spec)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }
}
