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

import java.awt.Color
import javax.swing.KeyStroke

internal object RcParser {
    private val ACTION_REGEX = Regex("""(?i)<action>\(([\w.$(),=-]+)\)""")
    private val WHICHKEY_LET_REGEX = Regex("""^let\s+g:WhichKeyDesc\w*\s*=\s*"(.+)"$""")

    fun parse(lines: List<String>): Rc.Config {
        val config = Rc.Config()
        for ((index, raw) in lines.withIndex()) {
            parseLine(config, raw.trim()) { message -> config.errors.add("line ${index + 1}: $message") }
        }
        return config
    }

    private fun parseLine(
        config: Rc.Config,
        line: String,
        err: (String) -> Unit,
    ) {
        if (line.isEmpty() || line.startsWith("\"") || line.startsWith("#")) return

        val whichKeyLet = WHICHKEY_LET_REGEX.matchEntire(line)
        if (whichKeyLet != null) {
            parseDescBody(config, whichKeyLet.groupValues[1], err)
            return
        }

        var text = line
        val cut = Regex("\\s\"").find(text)?.range?.first
        if (cut != null) text = text.substring(0, cut).trimEnd()
        if (text.isEmpty()) return

        val split = text.split(Regex("\\s+"), limit = 2)
        val command = split[0]
        val rest = split.getOrNull(1)?.trim() ?: ""
        dispatchCommand(config, command, rest, err)
    }

    private fun dispatchCommand(
        config: Rc.Config,
        command: String,
        rest: String,
        err: (String) -> Unit,
    ) {
        when (command) {
            "let" -> {}
            "set" -> parseSet(config, rest, err)
            "desc" -> parseDescBody(config, rest, err)
            "map", "noremap", "nmap", "nnoremap", "mmap", "mnoremap" -> parseMap(config, command, rest, err)
            "cmap", "cnoremap" -> parseChord(config, command, rest, err)
            "repeat" -> parseRepeat(config, rest, err)
            else -> err("unknown command '$command'")
        }
    }

    private fun parseDescBody(
        config: Rc.Config,
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
        config.keypadDesc[seq] = desc
    }

    private fun parseMap(
        config: Rc.Config,
        command: String,
        rest: String,
        err: (String) -> Unit,
    ) {
        val parts = rest.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) {
            err("$command needs a key and a target")
            return
        }
        val lhs = parts[0]
        val rhs = parts[1].trim()
        val recursive = command == "map" || command == "nmap" || command == "mmap"
        val motion = command == "mmap" || command == "mnoremap"

        val binding = parseTarget(rhs, recursive, "$command $rest", err) ?: return

        if (lhs.startsWith("<leader>")) {
            if (motion) {
                err("$command cannot define keypad entries; use map <leader>...")
                return
            }
            mapLeader(config, lhs, binding, err)
            return
        }
        mapKey(config, lhs, motion, binding, err)
    }

    private fun mapLeader(
        config: Rc.Config,
        lhs: String,
        binding: Rc.Binding,
        err: (String) -> Unit,
    ) {
        val seq = parseKeys(lhs.removePrefix("<leader>"), err) ?: return
        when {
            seq.isEmpty() -> err("<leader> alone cannot be mapped")
            seq[0] in "0123456789?/" -> err("keypad ${seq[0]} is reserved (digit argument / cheatsheet / describe)")
            else -> config.keypad[seq] = binding
        }
    }

    private fun mapKey(
        config: Rc.Config,
        lhs: String,
        motion: Boolean,
        binding: Rc.Binding,
        err: (String) -> Unit,
    ) {
        val keys = parseKeys(lhs, err) ?: return
        when {
            keys.length != 1 ->
                err("${if (motion) "motion" else "normal"}-mode key must be a single printable key: $lhs")

            keys == " " -> err("SPC is the keypad key and cannot be remapped")
            else -> (if (motion) config.motion else config.normal)[keys[0]] = binding
        }
    }

    private fun parseChord(
        config: Rc.Config,
        command: String,
        rest: String,
        err: (String) -> Unit,
    ) {
        val tokens = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size < 2) {
            err("$command needs a chord keystroke and a target")
            return
        }
        val target = tokens.last()
        val keystrokeText = tokens.dropLast(1).joinToString(" ")
        val keyStroke = KeyStroke.getKeyStroke(keystrokeText)
        if (keyStroke == null || keyStroke.keyCode == 0) {
            err("$command: cannot parse chord '$keystrokeText' (use e.g. 'control F', 'alt shift COMMA')")
            return
        }
        val key = ChordKey.fromKeyStroke(keyStroke)
        if (!key.hasNonShiftModifier()) {
            err("$command: chord '$keystrokeText' needs a Ctrl, Alt or Meta modifier")
            return
        }
        val binding = parseTarget(target, recursive = command == "cmap", "$command $rest", err) ?: return
        config.chords[key] = binding
    }

    private fun parseTarget(
        rhs: String,
        recursive: Boolean,
        errContext: String,
        err: (String) -> Unit,
    ): Rc.Binding? {
        val action = ACTION_REGEX.matchEntire(rhs)?.groupValues?.get(1)
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
        config: Rc.Config,
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
                config.repeat.getOrPut(group) { LinkedHashMap() }[key[0]] = binding
            }
        }
    }
}

private fun parseSet(
    config: Rc.Config,
    rest: String,
    err: (String) -> Unit,
) {
    when {
        rest == "which-key" -> {
            config.whichKey = true
        }

        rest == "nowhich-key" -> {
            config.whichKey = false
        }

        rest.startsWith("timeoutlen") -> {
            val delay =
                rest.substringAfter("=", "").trim().toIntOrNull()
                    ?: rest.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
            if (delay != null && delay >= 0) config.whichKeyDelayMs = delay
        }

        else -> parseSetColor(config, rest, err)
    }
}

private val COLOR_SETTERS: Map<String, (Rc.Config, Color) -> Unit> =
    mapOf(
        "overlay-color" to { config, color -> config.overlayColor = color },
        "overlay-text-color" to { config, color -> config.overlayTextColor = color },
        "expand-hint-color" to { config, color -> config.expandHintColor = color },
        "grab-color" to { config, color -> config.grabColor = color },
    )

private val HEX_COLOR_REGEX = Regex("[0-9a-fA-F]{6}")

private fun parseSetColor(
    config: Rc.Config,
    rest: String,
    err: (String) -> Unit,
) {
    val key = rest.substringBefore("=").trim()
    val setColor = COLOR_SETTERS[key] ?: return
    val raw = rest.substringAfter("=", "").trim()
    val color = parseHexColor(raw)
    if (color == null) {
        err("set $key: invalid color '$raw' (expected #RRGGBB)")
        return
    }
    setColor(config, color)
}

private fun parseHexColor(text: String): Color? {
    val hex = text.removePrefix("#")
    if (!HEX_COLOR_REGEX.matches(hex)) return null
    val rgb = hex.toInt(16)
    return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}

private fun parseKeys(
    text: String,
    err: (String) -> Unit,
): String? {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        val char = text[i]
        if (char == '<') {
            val close = text.indexOf('>', i)
            if (close < 0) {
                out.append(char)
                i++
                continue
            }
            when (text.substring(i + 1, close).lowercase()) {
                "space" -> {
                    out.append(' ')
                }

                "lt" -> {
                    out.append('<')
                }

                else -> {
                    err("unsupported key token ${text.substring(i, close + 1)} (only printable keys reach the meow engine)")
                    return null
                }
            }
            i = close + 1
        } else {
            out.append(char)
            i++
        }
    }
    return out.toString()
}
