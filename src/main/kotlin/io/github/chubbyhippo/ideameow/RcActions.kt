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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class ReloadRcAction :
    AnAction(),
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) = RcReload.perform()
}

class EditRcAction :
    AnAction(),
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val f = Rc.rcFile()
        seedIfMissing(f)
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f) ?: return
        OpenFileDescriptor(project, vf).navigate(true)
    }

    companion object {
        fun seedIfMissing(f: File) {
            if (f.exists()) return
            f.writeText(
                Rc.defaultsText()
                    ?: (
                        "\" ~/${Rc.FILE_NAME} — ideameow configuration\n" +
                            "\" the bundled defaults (full meow layout + keypad table) stay\n" +
                            "\" underneath — lines here override them entry by entry, e.g.:\n" +
                            "\" nmap Q meow-goto-line\n"
                    ),
            )
        }
    }
}
