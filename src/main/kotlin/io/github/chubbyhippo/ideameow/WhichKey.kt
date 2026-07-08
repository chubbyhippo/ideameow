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
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.Timer

/**
 * which-key, the Emacs way: after `timeoutlen` ms on a pending prefix — a
 * keypad SPC sequence, or the , . [ ] thing table — a NON-focusable panel
 * appears along the bottom of the editor listing the continuations in
 * columns (column-major, like which-key's grid). It never takes focus and
 * never interrupts: just keep typing the sequence in the editor; ESC cancels
 * through the editor as usual. Deeper prefixes in the same chain refresh the
 * panel without the delay. Descriptions come from `desc` /
 * `let g:WhichKeyDesc_*` entries in ~/.ideameowrc; delay and on/off from
 * `set timeoutlen` / `set nowhich-key`.
 */
object WhichKey {
    private var popup: JBPopup? = null
    private var timer: Timer? = null

    /** True when hide() closed a visible panel: the chain's next panel
     *  appears with no delay, like which-key refreshing between prefixes. */
    private var chainVisible = false

    /** which-key-separator. */
    private const val SEPARATOR = " → "

    private val THINGS =
        listOf(
            "r" to "round ( )",
            "s" to "square [ ]",
            "c" to "curly { }",
            "g" to "string",
            "e" to "symbol",
            "w" to "window",
            "b" to "buffer",
            "p" to "paragraph",
            "l" to "line",
            "v" to "visual line",
            "d" to "defun",
            "." to "sentence",
        )

    fun scheduleKeypad(
        editor: Editor,
        buffer: String,
    ) = schedule(editor) { keypadRows(buffer) }

    fun scheduleThings(editor: Editor) = schedule(editor) { THINGS }

    /** One row per next key continuing [buffer]: terminal label or group desc. */
    fun keypadRows(buffer: String): List<Pair<String, String>> {
        val descs = Rc.keypadDescs()
        val rows = sortedMapOf<String, String>()
        for ((seq, b) in Rc.keypad()) {
            if (!seq.startsWith(buffer) || seq == buffer) continue
            val child = buffer + seq[buffer.length]
            val label =
                if (seq == child) {
                    descs[seq] ?: b.action ?: b.command ?: b.keys.orEmpty()
                } else {
                    descs[child] ?: "+more"
                }
            if (child !in rows || descs.containsKey(child)) rows[child] = label
        }
        return rows.map { (child, label) ->
            val key = child.last()
            (if (key == ' ') "SPC" else key.toString()) to label
        }
    }

    private fun schedule(
        editor: Editor,
        rowsProvider: () -> List<Pair<String, String>>,
    ) {
        hide()
        if (!Rc.whichKeyEnabled()) {
            chainVisible = false
            return
        }
        val delay = if (chainVisible) 0 else Rc.whichKeyDelayMs().coerceAtLeast(0)
        chainVisible = false
        timer =
            Timer(delay) {
                timer = null
                show(editor, rowsProvider())
            }.apply {
                isRepeats = false
                start()
            }
    }

    private fun show(
        editor: Editor,
        rows: List<Pair<String, String>>,
    ) {
        runCatching {
            if (rows.isEmpty() || editor.isDisposed) return
            val label =
                JBLabel(gridHtml(editor, rows)).apply {
                    border = JBUI.Borders.empty(6, 10)
                }
            val p =
                JBPopupFactory
                    .getInstance()
                    .createComponentPopupBuilder(label, null)
                    .setRequestFocus(false)
                    .setFocusable(false)
                    .setCancelKeyEnabled(false) // ESC belongs to the editor
                    .setCancelOnClickOutside(true)
                    .createPopup()
            popup = p
            // Emacs shows which-key in a bottom side window spanning the
            // frame — the closest here: bottom-left of the editor component
            val host = editor.component
            val pref = label.preferredSize
            val y = (host.height - pref.height - JBUI.scale(8)).coerceAtLeast(0)
            p.show(RelativePoint(host, Point(JBUI.scale(4), y)))
        }
    }

    /** which-key's grid: entries run DOWN each column, then across, with as
     *  many columns as the editor width fits. */
    private fun gridHtml(
        editor: Editor,
        rows: List<Pair<String, String>>,
    ): String {
        val metrics = editor.component.getFontMetrics(JBFont.label())
        val entryWidth = rows.maxOf { (k, d) -> metrics.stringWidth(k + SEPARATOR + d) } + JBUI.scale(28)
        val available = (editor.component.width - JBUI.scale(28)).coerceAtLeast(entryWidth)
        val cols = (available / entryWidth).coerceIn(1, rows.size)
        val perColumn = (rows.size + cols - 1) / cols
        return buildString {
            append("<html><table cellpadding='1' cellspacing='0'>")
            for (r in 0 until perColumn) {
                append("<tr>")
                for (c in 0 until cols) {
                    val i = c * perColumn + r
                    if (i < rows.size) {
                        val (k, d) = rows[i]
                        append("<td align='right'><b>").append(esc(k)).append("</b></td>")
                        append("<td>").append(SEPARATOR).append(esc(d)).append("</td>")
                        append("<td width='").append(JBUI.scale(18)).append("'></td>")
                    }
                }
                append("</tr>")
            }
            append("</table></html>")
        }
    }

    fun hide() {
        timer?.stop()
        timer = null
        popup?.let {
            chainVisible = it.isVisible
            runCatching { it.cancel() }
        }
        popup = null
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
