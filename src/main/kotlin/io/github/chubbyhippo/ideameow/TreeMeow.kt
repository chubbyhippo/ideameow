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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JTree
import javax.swing.KeyStroke

object TreeMeow {
    private val SWING_MOTIONS =
        mapOf(
            "meow-next" to "selectNext",
            "meow-prev" to "selectPrevious",
            "meow-left" to "selectParent",
            "meow-right" to "selectChild",
        )

    fun boundChars(): Set<Char> =
        (Rc.defaults().motion.keys + Rc.cfg().motion.keys).filterTo(mutableSetOf()) { c ->
            (Rc.cfg().motion[c] ?: Rc.defaults().motion[c])?.command != "ignore"
        }

    fun dispatch(
        tree: JTree,
        c: Char,
        noremap: Boolean = false,
        depth: Int = 0,
    ) {
        val b = (if (noremap) null else Rc.cfg().motion[c]) ?: Rc.defaults().motion[c] ?: return
        val command = b.command
        if (command != null) {
            SWING_MOTIONS[command]?.let { swing(tree, it) }
            return
        }
        val actionId = b.action
        if (actionId != null) {
            Ide.actOn(tree, actionId)
            return
        }
        val keys = b.keys ?: return
        if (depth >= Rc.MAX_MAPPING_DEPTH) return
        for (k in keys) dispatch(tree, k, noremap || !b.recursive, depth + 1)
    }

    private fun swing(
        tree: JTree,
        name: String,
    ) {
        tree.actionMap
            .get(name)
            ?.actionPerformed(ActionEvent(tree, ActionEvent.ACTION_PERFORMED, name))
    }

    private val dispatcher =
        object : DumbAwareAction() {
            init {
                isEnabledInModalContext = true
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled =
                    e.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) == null &&
                    e.getData(PlatformDataKeys.CONTEXT_COMPONENT) is JTree
            }

            override fun actionPerformed(e: AnActionEvent) {
                val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return
                val c = (e.inputEvent as? KeyEvent)?.keyChar ?: return
                if (c == KeyEvent.CHAR_UNDEFINED) return
                dispatch(tree, c)
            }
        }

    private val installed = AtomicBoolean()

    private val focusListener =
        PropertyChangeListener { evt ->
            (evt.oldValue as? JTree)?.let { dispatcher.unregisterCustomShortcutSet(it) }
            (evt.newValue as? JTree)?.let { register(it) }
        }

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("focusOwner", focusListener)
    }

    fun uninstall() {
        if (!installed.compareAndSet(true, false)) return
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        kfm.removePropertyChangeListener("focusOwner", focusListener)
        (kfm.focusOwner as? JTree)?.let { dispatcher.unregisterCustomShortcutSet(it) }
    }

    fun refresh() {
        (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JTree)
            ?.let { register(it) }
    }

    private fun register(tree: JTree) {
        dispatcher.unregisterCustomShortcutSet(tree)
        val shortcuts =
            boundChars()
                .map { KeyboardShortcut(KeyStroke.getKeyStroke(it), null) }
        dispatcher.registerCustomShortcutSet(CustomShortcutSet(*shortcuts.toTypedArray()), tree)
    }
}

@Service(Service.Level.APP)
internal class TreeMeowLifecycle : Disposable {
    init {
        TreeMeow.install()
    }

    override fun dispose() = TreeMeow.uninstall()
}

internal class TreeMeowStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().getService(TreeMeowLifecycle::class.java)
    }
}
