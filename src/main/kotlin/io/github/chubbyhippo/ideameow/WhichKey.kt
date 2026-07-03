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
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.Timer

/**
 * which-key: after a short delay on a pending prefix (keypad SPC sequences,
 * or the , . [ ] thing table), a non-focusable popup lists the available
 * continuations. Descriptions come from `desc` / `let g:WhichKeyDesc_*`
 * entries in ~/.ideameowrc; delay and on/off from `set timeoutlen` /
 * `set nowhich-key`.
 */
object WhichKey {
    private var popup: JBPopup? = null
    private var timer: Timer? = null

    private val THINGS = listOf(
        "r" to "round ( )", "s" to "square [ ]", "c" to "curly { }", "g" to "string",
        "e" to "symbol", "w" to "window", "b" to "buffer", "p" to "paragraph",
        "l" to "line", "v" to "visual line", "d" to "defun", "." to "sentence",
    )

    fun scheduleKeypad(editor: Editor, buffer: String) = schedule(editor) { keypadRows(buffer) }

    fun scheduleThings(editor: Editor) = schedule(editor) { THINGS }

    /** One row per next key continuing [buffer]: terminal label or group desc. */
    fun keypadRows(buffer: String): List<Pair<String, String>> {
        val cfg = Rc.cfg()
        val rows = sortedMapOf<String, String>()
        for ((seq, b) in cfg.keypad) {
            if (!seq.startsWith(buffer) || seq == buffer) continue
            val child = buffer + seq[buffer.length]
            val label =
                if (seq == child) cfg.keypadDesc[seq] ?: b.action ?: b.keys.orEmpty()
                else cfg.keypadDesc[child] ?: "+more"
            if (child !in rows || cfg.keypadDesc.containsKey(child)) rows[child] = label
        }
        return rows.map { (child, label) ->
            val key = child.last()
            (if (key == ' ') "SPC" else key.toString()) to label
        }
    }

    private fun schedule(editor: Editor, rowsProvider: () -> List<Pair<String, String>>) {
        hide()
        val cfg = Rc.cfg()
        if (!cfg.whichKey) return
        timer = Timer(cfg.whichKeyDelayMs.coerceAtLeast(0)) {
            timer = null
            show(editor, rowsProvider())
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun show(editor: Editor, rows: List<Pair<String, String>>) {
        runCatching {
            if (rows.isEmpty() || editor.isDisposed) return
            val html = buildString {
                append("<html><table cellpadding='1'>")
                for ((k, d) in rows) {
                    append("<tr><td align='right'><b>").append(esc(k))
                    append("</b></td><td>&nbsp;").append(esc(d)).append("</td></tr>")
                }
                append("</table></html>")
            }
            val label = JBLabel(html).apply { border = JBUI.Borders.empty(6, 10) }
            popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(label, null)
                .setRequestFocus(false)
                .setFocusable(false)
                .setCancelOnClickOutside(true)
                .createPopup()
                .also { it.showInBestPositionFor(editor) }
        }
    }

    fun hide() {
        timer?.stop()
        timer = null
        popup?.let { runCatching { it.cancel() } }
        popup = null
    }

    private fun esc(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
