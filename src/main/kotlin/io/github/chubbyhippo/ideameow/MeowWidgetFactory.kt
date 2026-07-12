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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Component

class MeowWidgetFactory : StatusBarWidgetFactory {
    companion object {
        const val ID = "MeowMode"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = "Meow Mode"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = MeowWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private class MeowWidget(
    private val project: Project,
) : StatusBarWidget,
    StatusBarWidget.TextPresentation {
    override fun ID(): String = MeowWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {}

    override fun dispose() {}

    override fun getText(): String = Meow.statusText(project)

    override fun getTooltipText(): String = "Meow modal editing state"

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
}
