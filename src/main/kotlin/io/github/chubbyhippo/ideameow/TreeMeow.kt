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
        (Rc.defaults().motion.keys + Rc.config().motion.keys).filterTo(mutableSetOf()) { char ->
            (Rc.config().motion[char] ?: Rc.defaults().motion[char])?.command != "ignore"
        }

    fun dispatch(
        tree: JTree,
        char: Char,
        noremap: Boolean = false,
        depth: Int = 0,
    ) {
        val binding = (if (noremap) null else Rc.config().motion[char]) ?: Rc.defaults().motion[char] ?: return
        val command = binding.command
        if (command != null) {
            SWING_MOTIONS[command]?.let { swing(tree, it) }
            return
        }
        val actionId = binding.action
        if (actionId != null) {
            Ide.actOn(tree, actionId)
            return
        }
        val keys = binding.keys ?: return
        if (depth >= Rc.MAX_MAPPING_DEPTH) return
        for (key in keys) dispatch(tree, key, noremap || !binding.recursive, depth + 1)
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

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled =
                    event.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) == null &&
                    event.getData(PlatformDataKeys.CONTEXT_COMPONENT) is JTree
            }

            override fun actionPerformed(event: AnActionEvent) {
                val tree = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return
                val char = (event.inputEvent as? KeyEvent)?.keyChar ?: return
                if (char == KeyEvent.CHAR_UNDEFINED) return
                dispatch(tree, char)
            }
        }

    private val installed = AtomicBoolean()

    private val focusListener =
        PropertyChangeListener { event ->
            (event.oldValue as? JTree)?.let { dispatcher.unregisterCustomShortcutSet(it) }
            (event.newValue as? JTree)?.let { register(it) }
        }

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("focusOwner", focusListener)
    }

    fun uninstall() {
        if (!installed.compareAndSet(true, false)) return
        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        keyboardFocusManager.removePropertyChangeListener("focusOwner", focusListener)
        (keyboardFocusManager.focusOwner as? JTree)?.let { dispatcher.unregisterCustomShortcutSet(it) }
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
