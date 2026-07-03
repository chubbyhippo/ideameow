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

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.io.File

/**
 * ~/.ideameowrc — an .ideavimrc-flavored config file.
 *
 *   " comments start with a double quote (or #)
 *   nmap S <action>(AceAction)          NORMAL key -> IDE action
 *   nmap Z ,b                           NORMAL key -> meow keys (recursive)
 *   nnoremap Z ,b                       same, but the RHS ignores user maps
 *   map <leader>gd <action>(GotoDeclaration)
 *   map <leader>k ,b
 *   desc <leader>g goto things
 *   let g:WhichKeyDesc_g = "<leader>g goto things"   (ideavimrc-compatible)
 *   set nowhich-key                     disable the which-key popup
 *   set timeoutlen=300                  which-key popup delay in ms
 *
 * Keypad keys 0-9, ? and / are reserved; SPC itself cannot be remapped.
 * Unknown `set` options are ignored so .ideavimrc lines can be pasted as-is.
 */
object Rc {
    const val FILE_NAME = ".ideameowrc"

    data class Binding(
        val action: String? = null,
        val keys: String? = null,
        val recursive: Boolean = true,
    )

    class Config {
        val normal = mutableMapOf<Char, Binding>()
        val keypadUser = linkedMapOf<String, Binding>()
        val keypadDesc = mutableMapOf<String, String>()
        var whichKey = true
        var whichKeyDelayMs = 250
        val errors = mutableListOf<String>()

        /** Builtin keypad table with user entries layered on top. */
        val keypad: Map<String, Binding> by lazy {
            LinkedHashMap<String, Binding>().apply {
                Keypad.BUILTIN.forEach { (seq, actionId) -> put(seq, Binding(action = actionId)) }
                putAll(keypadUser)
            }
        }
    }

    @Volatile
    private var loaded = false

    @Volatile
    var config: Config = Config()
        private set

    fun cfg(): Config {
        if (!loaded) load()
        return config
    }

    fun setForTest(c: Config) {
        config = c
        loaded = true
    }

    fun rcFile(): File = File(System.getProperty("user.home"), FILE_NAME)

    fun load() {
        loaded = true
        val f = rcFile()
        config = if (f.isFile) parse(f.readLines()) else Config()
        if (config.errors.isNotEmpty()) {
            notify(
                "ideameow: problem(s) in ~/$FILE_NAME\n" + config.errors.joinToString("\n"),
                NotificationType.WARNING
            )
        }
    }

    fun notify(text: String, type: NotificationType) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ideameow")
                .createNotification(text, type)
                .notify(null)
        }
    }

    private val ACTION_RE = Regex("""(?i)<action>\(([\w.$]+)\)""")
    private val WHICHKEY_LET_RE = Regex("""^let\s+g:WhichKeyDesc\w*\s*=\s*"(.+)"$""")

    fun parse(lines: List<String>): Config {
        val c = Config()
        for ((i, raw) in lines.withIndex()) {
            var line = raw.trim()
            fun err(msg: String) = c.errors.add("line ${i + 1}: $msg")

            if (line.isEmpty() || line.startsWith("\"") || line.startsWith("#")) continue

            val wk = WHICHKEY_LET_RE.matchEntire(line)
            if (wk != null) {
                parseDescBody(c, wk.groupValues[1], ::err)
                continue
            }

            // trailing `" comment` (checked after the let-line above, whose
            // quoted value would otherwise be truncated)
            val cut = Regex("\\s\"").find(line)?.range?.first
            if (cut != null) line = line.substring(0, cut).trimEnd()
            if (line.isEmpty()) continue

            val split = line.split(Regex("\\s+"), limit = 2)
            val cmd = split[0]
            val rest = split.getOrNull(1)?.trim() ?: ""
            when (cmd) {
                "let" -> {} // mapleader and friends: accepted, nothing to do
                "set" -> parseSet(c, rest)
                "desc" -> parseDescBody(c, rest, ::err)
                "map", "noremap", "nmap", "nnoremap" -> parseMap(c, cmd, rest, ::err)
                else -> err("unknown command '$cmd'")
            }
        }
        return c
    }

    private fun parseSet(c: Config, rest: String) {
        when {
            rest == "which-key" -> c.whichKey = true
            rest == "nowhich-key" -> c.whichKey = false
            rest.startsWith("timeoutlen") -> {
                val n = rest.substringAfter("=", "").trim().toIntOrNull()
                    ?: rest.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
                if (n != null && n >= 0) c.whichKeyDelayMs = n
            }
            else -> {} // ignore unknown options so .ideavimrc content pastes cleanly
        }
    }

    private fun parseDescBody(c: Config, body: String, err: (String) -> Unit) {
        if (!body.startsWith("<leader>")) {
            err("descriptions must start with <leader>: $body")
            return
        }
        val after = body.removePrefix("<leader>")
        val seqToken = after.takeWhile { !it.isWhitespace() }
        val desc = after.drop(seqToken.length).trim()
        val seq = parseKeys(seqToken, err) ?: return
        if (seq.isEmpty()) {
            err("empty key sequence in description: $body")
            return
        }
        c.keypadDesc[seq] = desc
    }

    private fun parseMap(c: Config, cmd: String, rest: String, err: (String) -> Unit) {
        val parts = rest.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) {
            err("$cmd needs a key and a target")
            return
        }
        val lhs = parts[0]
        val rhs = parts[1].trim()
        val recursive = cmd == "map" || cmd == "nmap"

        val action = ACTION_RE.matchEntire(rhs)?.groupValues?.get(1)
        val binding = if (action != null) {
            Binding(action = action, recursive = recursive)
        } else {
            val keys = parseKeys(rhs.replace(Regex("\\s+"), ""), err) ?: return
            if (keys.isEmpty()) {
                err("empty target in '$cmd $rest'")
                return
            }
            Binding(keys = keys, recursive = recursive)
        }

        if (lhs.startsWith("<leader>")) {
            val seq = parseKeys(lhs.removePrefix("<leader>"), err) ?: return
            when {
                seq.isEmpty() -> err("<leader> alone cannot be mapped")
                seq[0] in "0123456789?/" -> err("keypad ${seq[0]} is reserved (digit argument / cheatsheet / describe)")
                else -> c.keypadUser[seq] = binding
            }
            return
        }

        val keys = parseKeys(lhs, err) ?: return
        when {
            keys.length != 1 -> err("normal-mode key must be a single printable key: $lhs")
            keys == " " -> err("SPC is the keypad key and cannot be remapped")
            else -> c.normal[keys[0]] = binding
        }
    }

    /** `<Space>` and `<lt>` tokens plus plain printable chars; null on error. */
    private fun parseKeys(s: String, err: (String) -> Unit): String? {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '<') {
                val close = s.indexOf('>', i)
                if (close < 0) {
                    out.append(ch); i++; continue
                }
                when (s.substring(i + 1, close).lowercase()) {
                    "space" -> out.append(' ')
                    "lt" -> out.append('<')
                    else -> {
                        err("unsupported key token ${s.substring(i, close + 1)} (only printable keys reach the meow engine)")
                        return null
                    }
                }
                i = close + 1
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }
}
