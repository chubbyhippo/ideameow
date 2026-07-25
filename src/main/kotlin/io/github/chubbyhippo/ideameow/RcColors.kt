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

import com.intellij.ui.JBColor
import java.awt.Color

internal object RcColors {
    private const val OVERLAY_RGB = 0x2ECC71
    private const val EXPAND_HINT_LIGHT_RGB = 0xD05C0A
    private const val EXPAND_HINT_DARK_RGB = 0xFFB050
    private const val GRAB_LIGHT_RGB = 0xC0F0CD
    private const val GRAB_DARK_RGB = 0x0C331C

    private val DEFAULT_OVERLAY_COLOR = JBColor(Color(OVERLAY_RGB), Color(OVERLAY_RGB))
    private val DEFAULT_OVERLAY_TEXT_COLOR = JBColor(Color.WHITE, Color.WHITE)
    private val DEFAULT_EXPAND_HINT_COLOR = JBColor(Color(EXPAND_HINT_LIGHT_RGB), Color(EXPAND_HINT_DARK_RGB))
    private val DEFAULT_GRAB_COLOR = JBColor(Color(GRAB_LIGHT_RGB), Color(GRAB_DARK_RGB))

    fun overlayColor(): JBColor = resolveColor(DEFAULT_OVERLAY_COLOR) { it.overlayColor }

    fun overlayTextColor(): JBColor = resolveColor(DEFAULT_OVERLAY_TEXT_COLOR) { it.overlayTextColor }

    fun expandHintColor(): JBColor = resolveColor(DEFAULT_EXPAND_HINT_COLOR) { it.expandHintColor }

    fun grabColor(): JBColor = resolveColor(DEFAULT_GRAB_COLOR) { it.grabColor }

    private fun resolveColor(
        fallback: JBColor,
        pick: (Rc.Config) -> Color?,
    ): JBColor = (pick(Rc.config()) ?: pick(Rc.defaults()))?.let { JBColor(it, it) } ?: fallback
}
