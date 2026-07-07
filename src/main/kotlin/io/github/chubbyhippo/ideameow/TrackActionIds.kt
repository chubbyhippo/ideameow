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

/**
 * IdeaVim's "Track Action IDs", ported (keypad: SPC i d). While tracking is
 * on, every action performed in the IDE pops a notification with its action
 * id — the id an rc line binds with `<action>(...)` — plus Stop Tracking and
 * Copy Action Id buttons. Read out of IdeaVim master, not guessed
 * (IdeaSpecifics.kt VimActionListener + NotificationService.ActionIdNotifier):
 * the id is ActionManager.getId(action) with the action's ProxyShortcutSet id
 * as fallback; the notification fires from beforeActionPerformed; the
 * previous balloon is expired first so only one is up at a time; the
 * notification's own buttons never report themselves. One deliberate
 * deviation: IdeaVim's toggle is a visible checkbox in Search Everywhere —
 * a keypad press has no checkbox, so toggling answers with an on/off balloon
 * (and the toggle action itself is excluded from tracking).
 */
object TrackActionIds {

    @Volatile
    var enabled = false

    /** The last id shown while tracking — observable by the specs. */
    @Volatile
    var lastTrackedId: String? = null

    private var notification: Notification? = null

    /** IdeaVim's id resolution: the registered id, else the ProxyShortcutSet's. */
    fun idOf(action: AnAction): String? =
        ActionManager.getInstance().getId(action)
            ?: (action.shortcutSet as? ProxyShortcutSet)?.actionId

    fun toggle() {
        enabled = !enabled
        expire()
        if (!enabled) lastTrackedId = null
        Rc.notify(
            if (enabled) "Tracking action ids — perform any action to see its id (SPC i d stops)"
            else "Stopped tracking action ids",
            NotificationType.INFORMATION,
        )
    }

    /** beforeActionPerformed while tracking: show the performed action's id. */
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
            val n = NotificationGroupManager.getInstance()
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

    class StopTracking(private val n: Notification) : AnAction("Stop Tracking") {
        override fun actionPerformed(e: AnActionEvent) {
            enabled = false
            lastTrackedId = null
            n.expire()
        }
    }

    class CopyActionId(private val id: String, private val n: Notification) : AnAction("Copy Action Id") {
        override fun actionPerformed(e: AnActionEvent) {
            CopyPasteManager.getInstance().setContents(StringSelection(id))
            n.expire()
        }
    }
}

/** The toggle behind SPC i d — also a checkbox in Search Everywhere,
 *  exactly like IdeaVim's FindActionIdAction (a DumbAwareToggleAction). */
class TrackActionIdsAction : DumbAwareToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean = TrackActionIds.enabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (TrackActionIds.enabled != state) TrackActionIds.toggle()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Application-level AnActionListener (plugin.xml applicationListeners):
 *  IdeaVim notifies from beforeActionPerformed, so this does too. */
class MeowActionIdListener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        TrackActionIds.track(action)
    }
}
