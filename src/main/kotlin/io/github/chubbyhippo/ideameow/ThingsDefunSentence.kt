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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal fun defun(
    editor: Editor,
    offset: Int,
): Things.Bounds? {
    defunFromPsi(editor, offset)?.let { return it }
    val text = editor.document.charsSequence
    var bounds = pair(text, offset, '{', '}', false)
    while (bounds != null) {
        val outer = pair(text, bounds.start, '{', '}', false) ?: break
        bounds = outer
    }
    return bounds
}

private fun defunFromPsi(
    editor: Editor,
    offset: Int,
): Things.Bounds? {
    val file =
        editor.project
            ?.let { PsiDocumentManager.getInstance(it) }
            ?.also { it.commitDocument(editor.document) }
            ?.getPsiFile(editor.document) ?: return null
    val safeOffset = offset.coerceIn(0, (editor.document.textLength - 1).coerceAtLeast(0))
    var element = file.findElementAt(safeOffset)
    var found: Things.Bounds? = null
    while (element != null && element !is PsiFile && found == null) {
        if (isFunctionElement(element)) {
            found = Things.Bounds(element.textRange.startOffset, element.textRange.endOffset)
        } else {
            element = element.parent
        }
    }
    return found
}

private val FUNCTION_TYPE_MARKERS = listOf("METHOD", "FUNCTION", "LAMBDA")

private fun isFunctionElement(element: PsiElement): Boolean {
    val typeName =
        element.node
            ?.elementType
            ?.toString()
            ?.uppercase() ?: ""
    return typeName.startsWith("FUN") || FUNCTION_TYPE_MARKERS.any { typeName.contains(it) }
}

internal fun sentence(
    text: CharSequence,
    offset: Int,
    inner: Boolean,
): Things.Bounds? {
    if (text.isEmpty()) return null
    val start = sentenceStart(text, offset)
    val end = sentenceEnd(text, offset)
    return when {
        end <= start -> {
            null
        }

        inner -> {
            Things.Bounds(start, end)
        }

        else -> {
            var extendedEnd = end
            while (extendedEnd < text.length && text[extendedEnd] == ' ') extendedEnd++
            Things.Bounds(start, extendedEnd)
        }
    }
}

private fun sentenceStart(
    text: CharSequence,
    offset: Int,
): Int {
    var start = offset.coerceIn(0, text.length - 1)
    while (start > 0) {
        if (text[start - 1] in SENTENCE_ENDERS || blankLineBefore(text, start)) break
        start--
    }
    while (start < text.length && text[start].isWhitespace()) start++
    return start
}

private fun sentenceEnd(
    text: CharSequence,
    offset: Int,
): Int {
    var end = offset.coerceIn(0, text.length)
    while (end < text.length && text[end] !in SENTENCE_ENDERS && !blankLineAt(text, end)) end++
    if (end < text.length && text[end] in SENTENCE_ENDERS) end++
    return end
}

private fun blankLineBefore(
    text: CharSequence,
    pos: Int,
) = pos > 1 && text[pos - 1] == '\n' && text[pos - 2] == '\n'

private fun blankLineAt(
    text: CharSequence,
    pos: Int,
) = text[pos] == '\n' && pos + 1 < text.length && text[pos + 1] == '\n'
