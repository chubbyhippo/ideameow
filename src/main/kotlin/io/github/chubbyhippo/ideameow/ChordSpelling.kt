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

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal object ChordSpelling {
    private const val MODIFIER_PREFIX_LENGTH = 2

    private val namedKeys =
        mapOf(
            "SPC" to ' ',
            "SPACE" to ' ',
            "TAB" to '\t',
        )

    private val unshiftedKeyCodes =
        mapOf(
            ' ' to KeyEvent.VK_SPACE,
            '\t' to KeyEvent.VK_TAB,
            ',' to KeyEvent.VK_COMMA,
            '.' to KeyEvent.VK_PERIOD,
            '/' to KeyEvent.VK_SLASH,
            ';' to KeyEvent.VK_SEMICOLON,
            '\'' to KeyEvent.VK_QUOTE,
            '[' to KeyEvent.VK_OPEN_BRACKET,
            ']' to KeyEvent.VK_CLOSE_BRACKET,
            '\\' to KeyEvent.VK_BACK_SLASH,
            '-' to KeyEvent.VK_MINUS,
            '=' to KeyEvent.VK_EQUALS,
            '`' to KeyEvent.VK_BACK_QUOTE,
        )

    private val shiftedKeyCodes =
        mapOf(
            '<' to KeyEvent.VK_COMMA,
            '>' to KeyEvent.VK_PERIOD,
            '?' to KeyEvent.VK_SLASH,
            ':' to KeyEvent.VK_SEMICOLON,
            '"' to KeyEvent.VK_QUOTE,
            '{' to KeyEvent.VK_OPEN_BRACKET,
            '}' to KeyEvent.VK_CLOSE_BRACKET,
            '|' to KeyEvent.VK_BACK_SLASH,
            '_' to KeyEvent.VK_MINUS,
            '+' to KeyEvent.VK_EQUALS,
            '~' to KeyEvent.VK_BACK_QUOTE,
            '!' to KeyEvent.VK_1,
            '@' to KeyEvent.VK_2,
            '#' to KeyEvent.VK_3,
            '$' to KeyEvent.VK_4,
            '%' to KeyEvent.VK_5,
            '^' to KeyEvent.VK_6,
            '&' to KeyEvent.VK_7,
            '*' to KeyEvent.VK_8,
            '(' to KeyEvent.VK_9,
            ')' to KeyEvent.VK_0,
        )

    fun keyStrokeOf(text: String): KeyStroke? {
        if (text.any { it.isWhitespace() }) return KeyStroke.getKeyStroke(text)
        return emacsKeyStroke(text) ?: KeyStroke.getKeyStroke(text)
    }

    private data class Prefix(
        val modifiers: Int,
        val shift: Boolean,
        val key: String,
    )

    private fun emacsKeyStroke(text: String): KeyStroke? {
        val prefix = prefixOf(text) ?: return null
        val key = namedKeys[prefix.key.uppercase()] ?: prefix.key.singleOrNull()
        return key?.let { chordKeyStroke(prefix, it) }
    }

    private fun prefixOf(text: String): Prefix? {
        var rest = text
        var modifiers = 0
        var shift = false
        var spelled = true
        while (spelled && rest.length > MODIFIER_PREFIX_LENGTH && rest[1] == '-') {
            when (rest[0].uppercaseChar()) {
                'C' -> modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
                'M', 'A' -> modifiers = modifiers or InputEvent.ALT_DOWN_MASK
                'S' -> shift = true
                else -> spelled = false
            }
            if (spelled) rest = rest.substring(MODIFIER_PREFIX_LENGTH)
        }
        return if (spelled && modifiers != 0) Prefix(modifiers, shift, rest) else null
    }

    private fun chordKeyStroke(
        prefix: Prefix,
        key: Char,
    ): KeyStroke? {
        val keyCode = keyCodeOf(key) ?: return null
        val shifted = prefix.shift || key.isUpperCase() || shiftedKeyCodes.containsKey(key)
        val modifiers = if (shifted) prefix.modifiers or InputEvent.SHIFT_DOWN_MASK else prefix.modifiers
        return KeyStroke.getKeyStroke(keyCode, modifiers)
    }

    private fun keyCodeOf(key: Char): Int? {
        val lowered = key.lowercaseChar()
        return when {
            lowered in 'a'..'z' -> KeyEvent.VK_A + (lowered - 'a')
            key in '0'..'9' -> KeyEvent.VK_0 + (key - '0')
            else -> unshiftedKeyCodes[key] ?: shiftedKeyCodes[key]
        }
    }
}
