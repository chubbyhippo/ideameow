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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class TreeMeowSpec : MeowSpec() {
    private fun givenTree(): JTree {
        val root = DefaultMutableTreeNode("root")
        val a = DefaultMutableTreeNode("a")
        a.add(DefaultMutableTreeNode("a1"))
        a.add(DefaultMutableTreeNode("a2"))
        root.add(a)
        root.add(DefaultMutableTreeNode("b"))
        return JTree(DefaultTreeModel(root)).apply {
            expandRow(0)
            setSelectionRow(0)
        }
    }

    private fun JTree.selectedText() = (lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as String?

    fun `test given a JTree then its ActionMap provides the arrow key actions`() {
        val tree = givenTree()
        for (name in listOf("selectNext", "selectPrevious", "selectParent", "selectChild")) {
            assertNotNull("JTree ActionMap must provide '$name'", tree.actionMap.get(name))
        }
    }

    fun `test given the bundled rc then it binds the tree keys`() {
        val d = Rc.defaults().motion
        assertEquals("meow-next", d['j']?.command)
        assertEquals("meow-prev", d['k']?.command)
        assertEquals("meow-left", d['h']?.command)
        assertEquals("meow-right", d['l']?.command)
        assertEquals("HideActiveWindow", d['q']?.action)
    }

    fun `test given a tree when j and k then the selection moves like the arrow keys`() {
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'j')
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'j')
        assertEquals("b", tree.selectedText())
        TreeMeow.dispatch(tree, 'k')
        assertEquals("a", tree.selectedText())
    }

    fun `test given a collapsed node when l then it expands, and l again enters it`() {
        val tree = givenTree()
        tree.setSelectionRow(1)
        TreeMeow.dispatch(tree, 'l')
        assertTrue("l on a collapsed node expands it", tree.isExpanded(1))
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'l')
        assertEquals("a1", tree.selectedText())
    }

    fun `test given an expanded node when h then it collapses, then goes to the parent`() {
        val tree = givenTree()
        tree.expandRow(1)
        tree.setSelectionRow(2)
        TreeMeow.dispatch(tree, 'h')
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'h')
        assertFalse("h on an expanded node collapses it", tree.isExpanded(1))
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'h')
        assertEquals("root", tree.selectedText())
    }

    fun `test given an editor-only command in the mmap then it is inert on trees`() {
        givenRc("mmap w meow-next-word")
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'w')
        assertEquals("a word motion has no tree meaning", "root", tree.selectedText())
    }

    fun `test given a user mmap override then it shadows the bundled defaults`() {
        givenRc("mmap j ignore")
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'j')
        assertEquals("root", tree.selectedText())
    }

    fun `test given a keys mapping then the replay resolves every key through the motion map`() {
        givenRc("mmap g jj")
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'g')
        assertEquals("b", tree.selectedText())
    }

    fun `test given a noremap replay then it skips user maps like the engine`() {
        givenRc(
            """
            mnoremap g jj
            mmap j ignore
            """.trimIndent(),
        )
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'j')
        assertEquals("a user-shadowed j is inert", "root", tree.selectedText())
        TreeMeow.dispatch(tree, 'g')
        assertEquals("the replay resolves j via the defaults", "b", tree.selectedText())
    }

    fun `test given an action mmap then it dispatches with the tree as context`() {
        val id = "IdeameowTreeMeowSpecProbe"
        var performed = 0
        val probe =
            object : AnAction(), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    performed++
                }
            }
        ActionManager.getInstance().registerAction(id, probe)
        try {
            givenRc("mmap z <action>($id)")
            TreeMeow.dispatch(givenTree(), 'z')
            assertEquals(1, performed)
        } finally {
            ActionManager.getInstance().unregisterAction(id)
        }
    }

    fun `test given defaults and user maps then boundChars merges them`() {
        givenRc("mmap w meow-next-word")
        val bound = TreeMeow.boundChars()
        for (c in "jkhlqw") assertTrue("'$c' must be bound", c in bound)
        assertFalse("unmapped letters stay native (speed search)", 'z' in bound)
    }

    fun `test given mmap q ignore then the key returns to the tree`() {
        givenRc("mmap q ignore")
        assertFalse("an ignored key leaves the shortcut set", 'q' in TreeMeow.boundChars())
        assertTrue("the other defaults stay", 'j' in TreeMeow.boundChars())
    }

    private val ctrlN = ChordKey.of(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)
    private val ctrlP = ChordKey.of(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)
    private val ctrlF = ChordKey.of(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)
    private val ctrlB = ChordKey.of(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK)
    private val altD = ChordKey.of(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK)
    private val ctrlS = ChordKey.of(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)

    fun `test given the bundled chord defaults then boundChords keeps the ones with a tree meaning`() {
        val bound = TreeMeow.boundChords()
        assertTrue("C-n has a tree analog (selectNext)", ctrlN in bound)
        assertTrue("C-p has a tree analog (selectPrevious)", ctrlP in bound)
        assertTrue("C-f has a tree analog (selectChild)", ctrlF in bound)
        assertTrue("C-b has a tree analog (selectParent)", ctrlB in bound)
        assertTrue("action chords forward generically through Ide.actOn", ctrlS in bound)
        assertFalse("kill-word has no tree meaning", altD in bound)
    }

    fun `test given C-n and C-p chords on a tree then the selection moves like j and k`() {
        val tree = givenTree()
        TreeMeow.dispatchChord(tree, ctrlN)
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatchChord(tree, ctrlN)
        assertEquals("b", tree.selectedText())
        TreeMeow.dispatchChord(tree, ctrlP)
        assertEquals("a", tree.selectedText())
    }

    fun `test given C-f and C-b chords on a collapsed node then they expand and collapse like l and h`() {
        val tree = givenTree()
        tree.setSelectionRow(1)
        TreeMeow.dispatchChord(tree, ctrlF)
        assertTrue("C-f on a collapsed node expands it", tree.isExpanded(1))
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatchChord(tree, ctrlF)
        assertEquals("a1", tree.selectedText())
        TreeMeow.dispatchChord(tree, ctrlB)
        assertEquals("a", tree.selectedText())
    }

    fun `test given a chord with no tree meaning then dispatchChord is inert`() {
        val tree = givenTree()
        TreeMeow.dispatchChord(tree, altD)
        assertEquals("kill-word has no tree meaning", "root", tree.selectedText())
    }

    fun `test given an unmapped chord then dispatchChord is a no-op`() {
        val tree = givenTree()
        val unmapped = ChordKey.of(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        TreeMeow.dispatchChord(tree, unmapped)
        assertEquals("root", tree.selectedText())
    }

    fun `test given a chord ignored via cmap then it drops out of boundChords`() {
        givenRc("cmap control N ignore")
        assertFalse("an ignored chord leaves the shortcut set", ctrlN in TreeMeow.boundChords())
        assertTrue("the other defaults stay", ctrlP in TreeMeow.boundChords())
    }

    private fun givenList(): JList<String> {
        val model = DefaultListModel<String>()
        model.addElement("first")
        model.addElement("second")
        model.addElement("third")
        return JList(model).apply { selectedIndex = 0 }
    }

    fun `test given a JList then its ActionMap provides the row actions`() {
        val list = givenList()
        for (name in listOf("selectNextRow", "selectPreviousRow", "selectFirstRow", "selectLastRow")) {
            assertNotNull("JList ActionMap must provide '$name'", list.actionMap.get(name))
        }
    }

    fun `test given the bundled chord defaults then boundListChords keeps the ones with a list meaning`() {
        val bound = TreeMeow.boundListChords()
        assertTrue("C-n has a list analog (selectNextRow)", ctrlN in bound)
        assertTrue("C-p has a list analog (selectPreviousRow)", ctrlP in bound)
        assertTrue("action chords forward generically through Ide.actOn", ctrlS in bound)
        assertFalse("C-f has no flat-list analog (no parent/child)", ctrlF in bound)
        assertFalse("kill-word has no list meaning", altD in bound)
    }

    fun `test given C-n and C-p chords on a JList then the selection moves like the arrow keys`() {
        val list = givenList()
        TreeMeow.dispatchListChord(list, ctrlN)
        assertEquals(1, list.selectedIndex)
        TreeMeow.dispatchListChord(list, ctrlN)
        assertEquals(2, list.selectedIndex)
        TreeMeow.dispatchListChord(list, ctrlP)
        assertEquals(1, list.selectedIndex)
    }

    fun `test given a chord with no list meaning then dispatchListChord is inert`() {
        val list = givenList()
        TreeMeow.dispatchListChord(list, altD)
        assertEquals("kill-word has no list meaning", 0, list.selectedIndex)
    }

    fun `test given an unmapped chord then dispatchListChord is a no-op`() {
        val list = givenList()
        val unmapped = ChordKey.of(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        TreeMeow.dispatchListChord(list, unmapped)
        assertEquals(0, list.selectedIndex)
    }
}
