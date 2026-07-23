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

import javax.swing.KeyStroke

internal object RcParser {
    private val ACTION_RE = Regex("""(?i)<action>\(([\w.$(),=-]+)\)""")
    private val WHICHKEY_LET_RE = Regex("""^let\s+g:WhichKeyDesc\w*\s*=\s*"(.+)"$""")

    fun parse(lines: List<String>): Rc.Config {
        val c = Rc.Config()
        for ((i, raw) in lines.withIndex()) {
            var line = raw.trim()

            fun err(msg: String) = c.errors.add("line ${i + 1}: $msg")

            if (line.isEmpty() || line.startsWith("\"") || line.startsWith("#")) continue

            val wk = WHICHKEY_LET_RE.matchEntire(line)
            if (wk != null) {
                parseDescBody(c, wk.groupValues[1], ::err)
                continue
            }

            val cut = Regex("\\s\"").find(line)?.range?.first
            if (cut != null) line = line.substring(0, cut).trimEnd()
            if (line.isEmpty()) continue

            val split = line.split(Regex("\\s+"), limit = 2)
            val cmd = split[0]
            val rest = split.getOrNull(1)?.trim() ?: ""
            when (cmd) {
                "let" -> {}

                "set" -> {
                    parseSet(c, rest)
                }

                "desc" -> {
                    parseDescBody(c, rest, ::err)
                }

                "map", "noremap", "nmap", "nnoremap", "mmap", "mnoremap" -> {
                    parseMap(c, cmd, rest, ::err)
                }

                "cmap", "cnoremap" -> {
                    parseChord(c, cmd, rest, ::err)
                }

                "repeat" -> {
                    parseRepeat(c, rest, ::err)
                }

                else -> {
                    err("unknown command '$cmd'")
                }
            }
        }
        return c
    }

    private fun parseSet(
        c: Rc.Config,
        rest: String,
    ) {
        when {
            rest == "which-key" -> {
                c.whichKey = true
            }

            rest == "nowhich-key" -> {
                c.whichKey = false
            }

            rest.startsWith("timeoutlen") -> {
                val n =
                    rest.substringAfter("=", "").trim().toIntOrNull()
                        ?: rest.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
                if (n != null && n >= 0) c.whichKeyDelayMs = n
            }

            else -> {}
        }
    }

    private fun parseDescBody(
        c: Rc.Config,
        body: String,
        err: (String) -> Unit,
    ) {
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

    private fun parseMap(
        c: Rc.Config,
        cmd: String,
        rest: String,
        err: (String) -> Unit,
    ) {
        val parts = rest.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) {
            err("$cmd needs a key and a target")
            return
        }
        val lhs = parts[0]
        val rhs = parts[1].trim()
        val recursive = cmd == "map" || cmd == "nmap" || cmd == "mmap"
        val motion = cmd == "mmap" || cmd == "mnoremap"

        val binding = parseTarget(rhs, recursive, "$cmd $rest", err) ?: return

        if (lhs.startsWith("<leader>")) {
            if (motion) {
                err("$cmd cannot define keypad entries; use map <leader>...")
                return
            }
            val seq = parseKeys(lhs.removePrefix("<leader>"), err) ?: return
            when {
                seq.isEmpty() -> err("<leader> alone cannot be mapped")
                seq[0] in "0123456789?/" -> err("keypad ${seq[0]} is reserved (digit argument / cheatsheet / describe)")
                else -> c.keypad[seq] = binding
            }
            return
        }

        val keys = parseKeys(lhs, err) ?: return
        when {
            keys.length != 1 -> {
                err("${if (motion) "motion" else "normal"}-mode key must be a single printable key: $lhs")
            }

            keys == " " -> {
                err("SPC is the keypad key and cannot be remapped")
            }

            else -> {
                (if (motion) c.motion else c.normal)[keys[0]] = binding
            }
        }
    }

    private fun parseChord(
        c: Rc.Config,
        cmd: String,
        rest: String,
        err: (String) -> Unit,
    ) {
        val tokens = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size < 2) {
            err("$cmd needs a chord keystroke and a target")
            return
        }
        val target = tokens.last()
        val keystroke = tokens.dropLast(1).joinToString(" ")
        val ks = KeyStroke.getKeyStroke(keystroke)
        if (ks == null || ks.keyCode == 0) {
            err("$cmd: cannot parse chord '$keystroke' (use e.g. 'control F', 'alt shift COMMA')")
            return
        }
        val key = ChordKey.fromKeyStroke(ks)
        if (!key.hasNonShiftModifier()) {
            err("$cmd: chord '$keystroke' needs a Ctrl, Alt or Meta modifier")
            return
        }
        val binding = parseTarget(target, recursive = cmd == "cmap", "$cmd $rest", err) ?: return
        c.chords[key] = binding
    }

    private fun parseTarget(
        rhs: String,
        recursive: Boolean,
        errContext: String,
        err: (String) -> Unit,
    ): Rc.Binding? {
        val action = ACTION_RE.matchEntire(rhs)?.groupValues?.get(1)
        return when {
            action != null -> {
                Rc.Binding(action = action, recursive = recursive)
            }

            rhs in Engine.COMMANDS -> {
                Rc.Binding(command = rhs, recursive = recursive)
            }

            rhs.startsWith("meow-") -> {
                err("unknown meow command '$rhs'")
                null
            }

            else -> {
                val keys = parseKeys(rhs.replace(Regex("\\s+"), ""), err) ?: return null
                if (keys.isEmpty()) {
                    err("empty target in '$errContext'")
                    null
                } else {
                    Rc.Binding(keys = keys, recursive = recursive)
                }
            }
        }
    }

    private fun parseRepeat(
        c: Rc.Config,
        rest: String,
        err: (String) -> Unit,
    ) {
        val parts = rest.split(Regex("\\s+"), limit = 3)
        if (parts.size < 3) {
            err("repeat needs a group, a member key and a target")
            return
        }
        val (group, keyToken, rhs) = parts
        val key = parseKeys(keyToken, err) ?: return
        when {
            key.length != 1 -> {
                err("repeat member key must be a single printable key: $keyToken")
            }

            key == " " -> {
                err("SPC is the keypad key and cannot be a repeat member")
            }

            else -> {
                val binding = parseTarget(rhs.trim(), recursive = true, "repeat $rest", err) ?: return
                c.repeat.getOrPut(group) { LinkedHashMap() }[key[0]] = binding
            }
        }
    }

    private fun parseKeys(
        s: String,
        err: (String) -> Unit,
    ): String? {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '<') {
                val close = s.indexOf('>', i)
                if (close < 0) {
                    out.append(ch)
                    i++
                    continue
                }
                when (s.substring(i + 1, close).lowercase()) {
                    "space" -> {
                        out.append(' ')
                    }

                    "lt" -> {
                        out.append('<')
                    }

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
