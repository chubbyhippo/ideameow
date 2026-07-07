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
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * The tree surface: MOTION-map dispatch on tool-window trees (TreeMeow).
 * Platform-specific — there is no meow/Emacs source of truth for JTrees;
 * what IS pinned against meow is the resolution order (user maps over
 * bundled defaults, noremap replays skipping user maps), which must behave
 * exactly like Engine's, and the translation of the four motion commands
 * to the tree's native arrow-key Swing actions (IdeaVim's NERDTree
 * navigates through the same ActionMap names).
 */
class TreeMeowSpec : MeowSpec() {

    /**
     * root
     * ├── a
     * │   ├── a1
     * │   └── a2
     * └── b
     */
    private fun givenTree(): JTree {
        val root = DefaultMutableTreeNode("root")
        val a = DefaultMutableTreeNode("a")
        a.add(DefaultMutableTreeNode("a1"))
        a.add(DefaultMutableTreeNode("a2"))
        root.add(a)
        root.add(DefaultMutableTreeNode("b"))
        return JTree(DefaultTreeModel(root)).apply {
            expandRow(0) // root visible+expanded: rows are root(0) a(1) b(2)
            setSelectionRow(0)
        }
    }

    private fun JTree.selectedText() =
        (lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as String?

    // ------------------------------------------------- the Swing contract

    fun testTreeActionMapProvidesTheArrowKeyActions() {
        val tree = givenTree()
        for (name in listOf("selectNext", "selectPrevious", "selectParent", "selectChild")) {
            assertNotNull("JTree ActionMap must provide '$name'", tree.actionMap.get(name))
        }
    }

    fun testBundledRcBindsTheTreeKeys() {
        val d = Rc.defaults().motion
        assertEquals("meow-next", d['j']?.command)
        assertEquals("meow-prev", d['k']?.command)
        assertEquals("meow-left", d['h']?.command)
        assertEquals("meow-right", d['l']?.command)
        assertEquals("HideActiveWindow", d['q']?.action)
    }

    // ------------------------------------------------------- j k h l keys

    fun testJAndKMoveTheSelectionLikeTheArrowKeys() {
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'j')
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'j')
        assertEquals("b", tree.selectedText())
        TreeMeow.dispatch(tree, 'k')
        assertEquals("a", tree.selectedText())
    }

    fun testLExpandsACollapsedNodeThenEntersIt() {
        val tree = givenTree()
        tree.setSelectionRow(1) // "a", collapsed
        TreeMeow.dispatch(tree, 'l')
        assertTrue("l on a collapsed node expands it", tree.isExpanded(1))
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'l')
        assertEquals("a1", tree.selectedText())
    }

    fun testHCollapsesAnExpandedNodeThenGoesToTheParent() {
        val tree = givenTree()
        tree.expandRow(1) // open "a"
        tree.setSelectionRow(2) // "a1"
        TreeMeow.dispatch(tree, 'h')
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'h')
        assertFalse("h on an expanded node collapses it", tree.isExpanded(1))
        assertEquals("a", tree.selectedText())
        TreeMeow.dispatch(tree, 'h')
        assertEquals("root", tree.selectedText())
    }

    // --------------------------------------------------- resolution rules

    fun testEditorOnlyCommandsAreInertOnTrees() {
        givenRc("mmap w meow-next-word")
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'w')
        assertEquals("a word motion has no tree meaning", "root", tree.selectedText())
    }

    fun testUserMotionMapsOverrideTheBundledDefaults() {
        givenRc("mmap j ignore")
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'j')
        assertEquals("root", tree.selectedText())
    }

    fun testKeysReplayResolvesEveryKeyThroughTheMotionMap() {
        givenRc("mmap g jj")
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'g')
        assertEquals("b", tree.selectedText())
    }

    fun testNoremapReplaysSkipUserMapsLikeTheEngine() {
        givenRc(
            """
            mnoremap g jj
            mmap j ignore
            """.trimIndent()
        )
        val tree = givenTree()
        TreeMeow.dispatch(tree, 'j')
        assertEquals("a user-shadowed j is inert", "root", tree.selectedText())
        TreeMeow.dispatch(tree, 'g')
        assertEquals("the replay resolves j via the defaults", "b", tree.selectedText())
    }

    fun testActionBindingsDispatchWithTheTreeAsContext() {
        val id = "IdeameowTreeMeowSpecProbe"
        var performed = 0
        val probe = object : AnAction(), DumbAware {
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

    fun testBoundCharsMergeTheDefaultsAndTheUserMaps() {
        givenRc("mmap w meow-next-word")
        val bound = TreeMeow.boundChars()
        for (c in "jkhlqw") assertTrue("'$c' must be bound", c in bound)
        assertFalse("unmapped letters stay native (speed search)", 'z' in bound)
    }

    fun testIgnoreGivesAKeyBackToTheTree() {
        givenRc("mmap q ignore")
        assertFalse("an ignored key leaves the shortcut set", 'q' in TreeMeow.boundChars())
        assertTrue("the other defaults stay", 'j' in TreeMeow.boundChars())
    }
}
