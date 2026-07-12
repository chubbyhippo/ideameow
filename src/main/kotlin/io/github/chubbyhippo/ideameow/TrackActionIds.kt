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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ProxyShortcutSet
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareToggleAction
import java.awt.datatransfer.StringSelection

object TrackActionIds {
    @Volatile
    var enabled = false

    @Volatile
    var lastTrackedId: String? = null

    private var notification: Notification? = null

    @Suppress("UnstableApiUsage")
    fun idOf(action: AnAction): String? =
        ActionManager.getInstance().getId(action)
            ?: (action.shortcutSet as? ProxyShortcutSet)?.actionId

    fun toggle() {
        enabled = !enabled
        expire()
        if (!enabled) lastTrackedId = null
        Rc.notify(
            if (enabled) {
                "Tracking action ids — perform any action to see its id (SPC i d stops)"
            } else {
                "Stopped tracking action ids"
            },
            NotificationType.INFORMATION,
        )
    }

    fun track(action: AnAction) {
        if (!enabled) return
        if (action is TrackActionIdsAction || action is StopTracking || action is CopyActionId) return
        val id = idOf(action)
        lastTrackedId = id
        showId(id)
    }

    private fun showId(id: String?) {
        expire()
        runCatching {
            val n =
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("ideameow")
                    .createNotification(
                        if (id != null) "Action id: <code>$id</code>" else "<i>Cannot detect action id</i>",
                        NotificationType.INFORMATION,
                    )
            n.addAction(StopTracking(n))
            if (id != null) n.addAction(CopyActionId(id, n))
            notification = n
            n.notify(null)
        }
    }

    fun expire() {
        notification?.expire()
        notification = null
    }

    class StopTracking(
        private val n: Notification,
    ) : AnAction("Stop Tracking") {
        override fun actionPerformed(e: AnActionEvent) {
            enabled = false
            lastTrackedId = null
            n.expire()
        }
    }

    class CopyActionId(
        private val id: String,
        private val n: Notification,
    ) : AnAction("Copy Action Id") {
        override fun actionPerformed(e: AnActionEvent) {
            CopyPasteManager.getInstance().setContents(StringSelection(id))
            n.expire()
        }
    }
}

class TrackActionIdsAction : DumbAwareToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean = TrackActionIds.enabled

    override fun setSelected(
        e: AnActionEvent,
        state: Boolean,
    ) {
        if (TrackActionIds.enabled != state) TrackActionIds.toggle()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class MeowActionIdListener : AnActionListener {
    override fun beforeActionPerformed(
        action: AnAction,
        event: AnActionEvent,
    ) {
        TrackActionIds.track(action)
    }
}
