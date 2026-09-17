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
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAwareAction
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JList
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

    private val CHORD_SWING_MOTIONS =
        mapOf(
            "next-line" to "selectNext",
            "previous-line" to "selectPrevious",
            "forward-char" to "selectChild",
            "backward-char" to "selectParent",
            "beginning-of-buffer" to "selectFirst",
            "end-of-buffer" to "selectLast",
        )

    private val LIST_CHORD_SWING_MOTIONS =
        mapOf(
            "next-line" to "selectNextRow",
            "previous-line" to "selectPreviousRow",
            "beginning-of-buffer" to "selectFirstRow",
            "end-of-buffer" to "selectLastRow",
        )

    fun boundChars(): Set<Char> =
        (Rc.defaults().motion.keys + Rc.config().motion.keys).filterTo(mutableSetOf()) { char ->
            (Rc.config().motion[char] ?: Rc.defaults().motion[char])?.command != "ignore"
        }

    fun boundChords(): Set<ChordKey> =
        RcLookups.chords().filterValues { binding ->
            binding.action != null || CHORD_SWING_MOTIONS.containsKey(binding.command)
        }.keys

    fun boundListChords(): Set<ChordKey> =
        RcLookups.chords().filterValues { binding ->
            binding.action != null || LIST_CHORD_SWING_MOTIONS.containsKey(binding.command)
        }.keys

    fun dispatch(
        tree: JTree,
        char: Char,
        noremap: Boolean = false,
        depth: Int = 0,
    ) {
        val binding = (if (noremap) null else Rc.config().motion[char]) ?: Rc.defaults().motion[char] ?: return
        val command = binding.command
        val actionId = binding.action
        when {
            command != null -> {
                SWING_MOTIONS[command]?.let { swing(tree, it) }
            }

            actionId != null -> {
                Ide.actOn(tree, actionId)
            }

            else -> {
                val keys = binding.keys
                if (keys != null && depth < Rc.MAX_MAPPING_DEPTH) {
                    for (key in keys) dispatch(tree, key, noremap || !binding.recursive, depth + 1)
                }
            }
        }
    }

    fun dispatchChord(
        tree: JTree,
        chord: ChordKey,
    ) {
        val binding = RcLookups.chords()[chord] ?: return
        val actionId = binding.action
        val command = binding.command
        when {
            actionId != null -> Ide.actOn(tree, actionId)
            command != null -> CHORD_SWING_MOTIONS[command]?.let { swing(tree, it) }
        }
    }

    fun dispatchListChord(
        list: JList<*>,
        chord: ChordKey,
    ) {
        val binding = RcLookups.chords()[chord] ?: return
        val actionId = binding.action
        val command = binding.command
        when {
            actionId != null -> Ide.actOn(list, actionId)
            command != null -> LIST_CHORD_SWING_MOTIONS[command]?.let { swing(list, it) }
        }
    }

    private fun swing(
        component: JComponent,
        name: String,
    ) {
        component.actionMap
            .get(name)
            ?.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, name))
    }

    private val dispatcher =
        object : DumbAwareActionShim() {
            override fun isTarget(event: AnActionEvent): Boolean =
                event.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) == null &&
                    event.getData(PlatformDataKeys.CONTEXT_COMPONENT) is JTree

            override fun actionPerformed(event: AnActionEvent) {
                val tree = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return
                val char = (event.inputEvent as? KeyEvent)?.keyChar
                if (char == null || char == KeyEvent.CHAR_UNDEFINED) return
                dispatch(tree, char)
            }
        }

    private val chordDispatcher =
        object : DumbAwareActionShim() {
            override fun isTarget(event: AnActionEvent): Boolean =
                event.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) == null &&
                    event.getData(PlatformDataKeys.CONTEXT_COMPONENT) is JTree

            override fun actionPerformed(event: AnActionEvent) {
                val tree = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return
                val keyEvent = event.inputEvent as? KeyEvent ?: return
                dispatchChord(tree, ChordKey.of(keyEvent.keyCode, keyEvent.modifiersEx))
            }
        }

    private val listChordDispatcher =
        object : DumbAwareActionShim() {
            override fun isTarget(event: AnActionEvent): Boolean =
                event.getData(PlatformDataKeys.SPEED_SEARCH_TEXT) == null &&
                    event.getData(PlatformDataKeys.CONTEXT_COMPONENT) is JList<*>

            override fun actionPerformed(event: AnActionEvent) {
                val list = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JList<*> ?: return
                val keyEvent = event.inputEvent as? KeyEvent ?: return
                dispatchListChord(list, ChordKey.of(keyEvent.keyCode, keyEvent.modifiersEx))
            }
        }

    private val installed = AtomicBoolean()

    private val focusListener =
        PropertyChangeListener { event ->
            unregister(event.oldValue as? JComponent)
            register(event.newValue as? JComponent)
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
        unregister(keyboardFocusManager.focusOwner as? JComponent)
    }

    fun refresh() {
        register(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JComponent)
    }

    private fun unregister(component: JComponent?) {
        when (component) {
            is JTree -> {
                dispatcher.unregisterCustomShortcutSet(component)
                chordDispatcher.unregisterCustomShortcutSet(component)
            }

            is JList<*> -> listChordDispatcher.unregisterCustomShortcutSet(component)
            else -> {}
        }
    }

    @Suppress("SpreadOperator")
    private fun register(component: JComponent?) {
        when (component) {
            is JTree -> {
                dispatcher.unregisterCustomShortcutSet(component)
                val shortcuts = boundChars().map { KeyboardShortcut(KeyStroke.getKeyStroke(it), null) }
                dispatcher.registerCustomShortcutSet(CustomShortcutSet(*shortcuts.toTypedArray()), component)

                chordDispatcher.unregisterCustomShortcutSet(component)
                val chordShortcuts =
                    boundChords().map { KeyboardShortcut(KeyStroke.getKeyStroke(it.keyCode, it.modifiers), null) }
                chordDispatcher.registerCustomShortcutSet(CustomShortcutSet(*chordShortcuts.toTypedArray()), component)
            }

            is JList<*> -> {
                listChordDispatcher.unregisterCustomShortcutSet(component)
                val chordShortcuts =
                    boundListChords().map { KeyboardShortcut(KeyStroke.getKeyStroke(it.keyCode, it.modifiers), null) }
                listChordDispatcher.registerCustomShortcutSet(CustomShortcutSet(*chordShortcuts.toTypedArray()), component)
            }

            else -> {}
        }
    }
}

private abstract class DumbAwareActionShim : DumbAwareAction() {
    init {
        isEnabledInModalContext = true
    }

    abstract fun isTarget(event: AnActionEvent): Boolean

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = isTarget(event)
    }
}

@Service(Service.Level.APP)
internal class TreeMeowLifecycle : Disposable {
    init {
        TreeMeow.install()
    }

    override fun dispose() = TreeMeow.uninstall()
}
