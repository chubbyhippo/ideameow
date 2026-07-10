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
import com.intellij.openapi.editor.VisualPosition
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * meow-char-thing-table:
 *   r round  s square  c curly  g string  e symbol  w window  b buffer
 *   p paragraph  l line  v visual-line  d defun  . sentence
 * inner() excludes delimiters, bounds() includes them; both return
 * (startOffset, endOffset) or null when the thing doesn't exist at point.
 */
object Things {
    data class Bounds(
        val start: Int,
        val end: Int,
    )

    fun inner(
        editor: Editor,
        ch: Char,
        offset: Int,
    ): Bounds? = compute(editor, ch, offset, inner = true)

    fun bounds(
        editor: Editor,
        ch: Char,
        offset: Int,
    ): Bounds? = compute(editor, ch, offset, inner = false)

    private fun compute(
        editor: Editor,
        ch: Char,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val text = editor.document.charsSequence
        return when (ch) {
            'r' -> pair(text, offset, '(', ')', inner)
            's' -> pair(text, offset, '[', ']', inner)
            'c' -> pair(text, offset, '{', '}', inner)
            'g' -> string(text, offset, inner)
            'e' -> symbol(text, offset)
            'w' -> window(editor)
            'b' -> Bounds(0, text.length)
            'p' -> paragraph(editor, offset, inner)
            'l' -> line(editor, offset, inner)
            'v' -> visualLine(editor, offset)
            'd' -> defun(editor, offset)
            '.' -> sentence(text, offset, inner)
            else -> null
        }
    }

    /** Innermost pair of [open]/[close] containing [offset], nesting-aware. */
    fun pair(
        text: CharSequence,
        offset: Int,
        open: Char,
        close: Char,
        inner: Boolean,
    ): Bounds? {
        var depth = 0
        var start = -1
        var i = offset - 1
        while (i >= 0) {
            val c = text[i]
            if (c == close) {
                depth++
            } else if (c == open) {
                if (depth == 0) {
                    start = i
                    break
                }
                depth--
            }
            i--
        }
        if (start < 0) return null
        depth = 0
        var end = -1
        var j = offset
        while (j < text.length) {
            val c = text[j]
            if (c == open && j != start) {
                depth++
            } else if (c == close) {
                if (depth == 0) {
                    end = j
                    break
                }
                depth--
            }
            j++
        }
        if (end < 0) return null
        return if (inner) Bounds(start + 1, end) else Bounds(start, end + 1)
    }

    /**
     * String thing (meow `g`): the quoted run at point. meow delegates to the
     * major-mode syntax table (`bounds-of-thing-at-point 'string` plus
     * skip-syntax over the `"|` classes, which strips the WHOLE delimiter
     * run); this port is a text scan instead, so `,g`/`.g` still work in
     * plain-text and language-agnostic buffers — a deliberate divergence
     * (see meow-semantics.md). It recognizes single AND triple runs of the
     * three quote chars `'` `"` `` ` ``, so Python/Kotlin `"""`/`'''`,
     * Markdown/JS fenced ``` and template-literal `` ` `` all select.
     * inner() drops the full delimiter run on each side, bounds() keeps it
     * (mirroring the skip-syntax intent). A single-char run stays on one line;
     * a triple run spans lines (docstrings/fences). `\` escapes the next char.
     * Unterminated openers are skipped so a stray apostrophe can't swallow the
     * rest of the buffer — but, like meow, a text scan can still be fooled by
     * an odd quote elsewhere on the same line.
     */
    private fun string(
        text: CharSequence,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '"' || c == '\'' || c == '`') {
                val triple = i + 2 < n && text[i + 1] == c && text[i + 2] == c
                val len = if (triple) 3 else 1
                val open = i
                var j = i + len
                var closeEnd = -1
                while (j < n) {
                    val d = text[j]
                    if (!triple && d == '\n') break // single-char runs stay on one line
                    if (d == '\\') {
                        j += 2
                        continue
                    }
                    val closes = if (triple) j + 2 < n && text[j + 1] == c && text[j + 2] == c else true
                    if (d == c && closes) {
                        closeEnd = j + len
                        break
                    }
                    j++
                }
                if (closeEnd < 0) {
                    i = open + len // unterminated opener: skip it, keep scanning
                    continue
                }
                if (offset in open until closeEnd) {
                    return if (inner) Bounds(open + len, closeEnd - len) else Bounds(open, closeEnd)
                }
                i = closeEnd
                continue
            }
            i++
        }
        return null
    }

    fun isWordChar(c: Char) = Character.isLetterOrDigit(c)

    fun isSymbolChar(c: Char) = isWordChar(c) || c == '_' || c == '$'

    private fun symbol(
        text: CharSequence,
        offset: Int,
    ): Bounds? {
        var o = offset
        if (o >= text.length || !isSymbolChar(text[o])) {
            if (o > 0 && isSymbolChar(text[o - 1])) o-- else return null
        }
        var s = o
        var e = o
        while (s > 0 && isSymbolChar(text[s - 1])) s--
        while (e < text.length && isSymbolChar(text[e])) e++
        return Bounds(s, e)
    }

    private fun window(editor: Editor): Bounds {
        // one visible-lines rule with avy's candidate scan (Ide.visibleLines):
        // this copy had drifted — it lacked the headless and empty-document
        // guards, collapsing the window thing to line 0 in a zero-height view
        val (startLine, endLine) = Ide.visibleLines(editor)
        val doc = editor.document
        return Bounds(doc.getLineStartOffset(startLine), doc.getLineEndOffset(endLine))
    }

    private fun paragraph(
        editor: Editor,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        val doc = editor.document
        if (doc.lineCount == 0) return null

        fun blank(l: Int) = doc.charsSequence.subSequence(doc.getLineStartOffset(l), doc.getLineEndOffset(l)).isBlank()
        var ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
        if (blank(ln)) return null
        var first = ln
        var last = ln
        while (first > 0 && !blank(first - 1)) first--
        while (last < doc.lineCount - 1 && !blank(last + 1)) last++
        val start = doc.getLineStartOffset(first)
        if (inner) return Bounds(start, doc.getLineEndOffset(last))
        // bounds include the trailing blank lines (emacs forward-paragraph)
        var stop = last
        while (stop < doc.lineCount - 1 && blank(stop + 1)) stop++
        val end = if (stop < doc.lineCount - 1) doc.getLineStartOffset(stop + 1) else doc.getLineEndOffset(stop)
        return Bounds(start, end)
    }

    private fun line(
        editor: Editor,
        offset: Int,
        inner: Boolean,
    ): Bounds {
        val doc = editor.document
        val ln = doc.getLineNumber(offset.coerceIn(0, doc.textLength))
        val end = doc.getLineEndOffset(ln)
        return if (inner) {
            Bounds(doc.getLineStartOffset(ln), end)
        } else {
            Bounds(doc.getLineStartOffset(ln), (end + 1).coerceAtMost(doc.textLength))
        }
    }

    private fun visualLine(
        editor: Editor,
        offset: Int,
    ): Bounds {
        val vLine = editor.offsetToVisualPosition(offset).line
        val start = editor.visualPositionToOffset(VisualPosition(vLine, 0))
        var end = editor.visualPositionToOffset(VisualPosition(vLine + 1, 0))
        if (end <= start) {
            end = editor.document.textLength
        } else if (end > 0 && editor.document.charsSequence[end - 1] == '\n') {
            end--
        }
        return Bounds(start, end)
    }

    /**
     * defun: nearest PSI ancestor that looks like a function/method; falls
     * back to the outermost curly block around point for plain text.
     */
    private fun defun(
        editor: Editor,
        offset: Int,
    ): Bounds? {
        val project = editor.project
        if (project != null) {
            val pdm = PsiDocumentManager.getInstance(project)
            pdm.commitDocument(editor.document)
            val file = pdm.getPsiFile(editor.document)
            if (file != null) {
                var el = file.findElementAt(offset.coerceIn(0, (editor.document.textLength - 1).coerceAtLeast(0)))
                while (el != null && el !is PsiFile) {
                    val t =
                        el.node
                            ?.elementType
                            ?.toString()
                            ?.uppercase() ?: ""
                    if (t.contains("METHOD") || t.contains("FUNCTION") || t.startsWith("FUN") || t.contains("LAMBDA")) {
                        return Bounds(el.textRange.startOffset, el.textRange.endOffset)
                    }
                    el = el.parent
                }
            }
        }
        val text = editor.document.charsSequence
        var b = pair(text, offset, '{', '}', false) ?: return null
        while (true) {
            val outer = pair(text, b.start, '{', '}', false) ?: break
            b = outer
        }
        return b
    }

    private fun sentence(
        text: CharSequence,
        offset: Int,
        inner: Boolean,
    ): Bounds? {
        if (text.isEmpty()) return null
        val enders = ".!?"
        var s = offset.coerceIn(0, text.length - 1)
        while (s > 0) {
            val c = text[s - 1]
            if (c in enders || (c == '\n' && s > 1 && text[s - 2] == '\n')) break
            s--
        }
        while (s < text.length && text[s].isWhitespace()) s++
        var e = offset.coerceIn(0, text.length)
        while (e < text.length && text[e] !in enders && !(text[e] == '\n' && e + 1 < text.length && text[e + 1] == '\n')) e++
        if (e < text.length && text[e] in enders) e++
        if (e <= s) return null
        if (inner) return Bounds(s, e)
        var be = e
        while (be < text.length && text[be] == ' ') be++
        return Bounds(s, be)
    }
}
