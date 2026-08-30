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

class ThingsSpec : MeowSpec() {
    fun `test given caret inside parens when comma r then inner round is selected forward`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys(",r")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()
    }

    fun `test given caret inside parens when comma or dot with parens then performs same as r`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys(",(")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys(",)")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys(".(")
        thenSelection("(bar baz)")
        thenCaretAtSelectionStart()

        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys(".)")
        thenSelection("(bar baz)")
        thenCaretAtSelectionStart()

        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("[(")
        thenSelection("b")
        thenCaretAtSelectionStart()

        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("])")
        thenSelection("ar baz")
        thenCaretAtSelectionEnd()
    }

    fun `test given caret inside parens when dot r then bounds include the parens and select backward`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys(".r")
        thenSelection("(bar baz)")
        thenCaretAtSelectionStart()
    }

    fun `test given nested pairs when comma r then the innermost pair wins`() {
        given("nested", "(a (b<caret>) c)")
        whenKeys(",r")
        thenSelection("b")
    }

    fun `test given square and curly things then s and c select them`() {
        given("square", "a [b<caret> c] d")
        whenKeys(",s")
        thenSelection("b c")

        given("curly", "a {b<caret> c} d")
        whenKeys(".c")
        thenSelection("{b c}")
    }

    fun `test given caret inside square brackets when comma or dot with square brackets then performs same as s`() {
        given("square pair", "foo [b<caret>ar baz] qux")
        whenKeys(",[")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("square pair", "foo [b<caret>ar baz] qux")
        whenKeys(",]")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("square pair", "foo [b<caret>ar baz] qux")
        whenKeys(".[")
        thenSelection("[bar baz]")
        thenCaretAtSelectionStart()

        given("square pair", "foo [b<caret>ar baz] qux")
        whenKeys(".]")
        thenSelection("[bar baz]")
        thenCaretAtSelectionStart()

        given("square pair", "foo [b<caret>ar baz] qux")
        whenKeys("[[")
        thenSelection("b")
        thenCaretAtSelectionStart()

        given("square pair", "foo [b<caret>ar baz] qux")
        whenKeys("]]")
        thenSelection("ar baz")
        thenCaretAtSelectionEnd()
    }

    fun `test given caret inside curly brackets when comma or dot with curly brackets then performs same as c`() {
        given("curly pair", "foo {b<caret>ar baz} qux")
        whenKeys(",{")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("curly pair", "foo {b<caret>ar baz} qux")
        whenKeys(",}")
        thenSelection("bar baz")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("curly pair", "foo {b<caret>ar baz} qux")
        whenKeys(".{")
        thenSelection("{bar baz}")
        thenCaretAtSelectionStart()

        given("curly pair", "foo {b<caret>ar baz} qux")
        whenKeys(".}")
        thenSelection("{bar baz}")
        thenCaretAtSelectionStart()

        given("curly pair", "foo {b<caret>ar baz} qux")
        whenKeys("[{")
        thenSelection("b")
        thenCaretAtSelectionStart()

        given("curly pair", "foo {b<caret>ar baz} qux")
        whenKeys("]}")
        thenSelection("ar baz")
        thenCaretAtSelectionEnd()
    }

    fun `test given tag thing when comma t then selects between angle brackets and dot t selects the whole tag`() {
        given("tag", "foo <tag>con<caret>tent</tag> bar")
        whenKeys(",t")
        thenSelection("content")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        whenKeys(".t")
        thenSelection("<tag>content</tag>")
        thenCaretAtSelectionStart()
    }

    fun `test given nested tags when comma t then innermost tag is selected`() {
        given("nested tags", "<div><span>he<caret>llo</span></div>")
        whenKeys(",t")
        thenSelection("hello")
        whenKeys(".t")
        thenSelection("<span>hello</span>")
    }

    fun `test given tag with attributes when comma t and dot t then tag is properly selected`() {
        given("tag with attributes", "<tag class=\"foo\" attr='bar'>val<caret>ue</tag>")
        whenKeys(",t")
        thenSelection("value")
        whenKeys(".t")
        thenSelection("<tag class=\"foo\" attr='bar'>value</tag>")
    }

    fun `test given tag with attribute containing angle bracket when comma t then inner is selected`() {
        given("tag with angle in attribute", "<tag attr=\"a > b\">inn<caret>er</tag>")
        whenKeys(",t")
        thenSelection("inner")
        whenKeys(".t")
        thenSelection("<tag attr=\"a > b\">inner</tag>")
    }

    fun `test given caret on opening or closing tag when comma t then inner is selected`() {
        given("caret on open tag", "<<caret>div>hello</div>")
        whenKeys(",t")
        thenSelection("hello")

        given("caret on close tag", "<div>hello</di<caret>v>")
        whenKeys(",t")
        thenSelection("hello")
    }

    fun `test given open and close bracket t then selects to start and end of tag`() {
        given("tag", "foo <tag>con<caret>tent</tag> bar")
        whenKeys("[t")
        thenSelection("con")
        thenCaretAtSelectionStart()

        given("tag", "foo <tag>con<caret>tent</tag> bar")
        whenKeys("]t")
        thenSelection("tent")
        thenCaretAtSelectionEnd()
    }

    fun `test given a double quoted string when comma g then the quoted run is selected`() {
        given("string", "say \"hi th<caret>ere\" now")
        whenKeys(",g")
        thenSelection("hi there")
        whenKeys(".g")
        thenSelection("\"hi there\"")
    }

    fun `test given a single quoted string when comma g then inner selects the run and dot g keeps the quotes`() {
        given("single quotes", "say 'hi th<caret>ere' now")
        whenKeys(",g")
        thenSelection("hi there")
        whenKeys(".g")
        thenSelection("'hi there'")
    }

    fun `test given single or double quotes when comma or dot with quote chars then performs same as g`() {
        given("single quotes", "say 'hi th<caret>ere' now")
        whenKeys(",'")
        thenSelection("hi there")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("single quotes", "say 'hi th<caret>ere' now")
        whenKeys(".'")
        thenSelection("'hi there'")
        thenCaretAtSelectionStart()

        given("single quotes", "say 'hi th<caret>ere' now")
        whenKeys("['")
        thenSelection("hi th")
        thenCaretAtSelectionStart()

        given("single quotes", "say 'hi th<caret>ere' now")
        whenKeys("]'")
        thenSelection("ere")
        thenCaretAtSelectionEnd()

        given("double quotes", "say \"hi th<caret>ere\" now")
        whenKeys(",\"")
        thenSelection("hi there")
        thenSelType(SelType.TRANSIENT)
        thenCaretAtSelectionEnd()

        given("double quotes", "say \"hi th<caret>ere\" now")
        whenKeys(".\"")
        thenSelection("\"hi there\"")
        thenCaretAtSelectionStart()

        given("double quotes", "say \"hi th<caret>ere\" now")
        whenKeys("[\"")
        thenSelection("hi th")
        thenCaretAtSelectionStart()

        given("double quotes", "say \"hi th<caret>ere\" now")
        whenKeys("]\"")
        thenSelection("ere")
        thenCaretAtSelectionEnd()
    }

    fun `test given a backtick string when comma g then inner selects the run and dot g keeps the backticks`() {
        given("backticks", "say `hi th<caret>ere` now")
        whenKeys(",g")
        thenSelection("hi there")
        whenKeys(".g")
        thenSelection("`hi there`")
    }

    fun `test given a triple double quoted string when comma g then inner drops the quotes and dot g keeps them`() {
        given("triple double", "say \"\"\"hi th<caret>ere\"\"\" now")
        whenKeys(",g")
        thenSelection("hi there")
        whenKeys(".g")
        thenSelection("\"\"\"hi there\"\"\"")
    }

    fun `test given a triple single quoted string when comma g then inner drops the quotes and dot g keeps them`() {
        given("triple single", "say '''hi th<caret>ere''' now")
        whenKeys(",g")
        thenSelection("hi there")
        whenKeys(".g")
        thenSelection("'''hi there'''")
    }

    fun `test given a triple backtick fence when comma g then inner drops all three backticks and dot g keeps them`() {
        given("triple backtick", "say ```hi th<caret>ere``` now")
        whenKeys(",g")
        thenSelection("hi there")
        whenKeys(".g")
        thenSelection("```hi there```")
    }

    fun `test given a triple quoted docstring spanning lines when comma g then the whole multiline run is selected`() {
        given("multiline docstring", "x = \"\"\"\nhe<caret>llo\nworld\n\"\"\"")
        whenKeys(",g")
        thenSelection("\nhello\nworld\n")
        whenKeys(".g")
        thenSelection("\"\"\"\nhello\nworld\n\"\"\"")
    }

    fun `test given an apostrophe earlier on another line when comma g then the real string below still selects`() {
        given("stray apostrophe", "don't\nx = 'h<caret>i'")
        whenKeys(",g")
        thenSelection("hi")
    }

    fun `test given an unterminated quote when comma g then nothing is selected`() {
        given("unterminated", "it'<caret>s fine")
        whenKeys(",g")
        thenNoSelection()
    }

    fun `test given a symbol thing when comma e then the symbol is selected`() {
        given("symbol", "f<caret>oo_bar baz")
        whenKeys(",e")
        thenSelection("foo_bar")
    }

    fun `test given a paragraph when comma p then the block of lines is selected`() {
        given("paragraphs", "aaa\nb<caret>bb\n\nccc")
        whenKeys(",p")
        thenSelection("aaa\nbbb")
    }

    fun `test given a paragraph when dot p then trailing blank lines are included`() {
        given("paragraphs", "aaa\nb<caret>bb\n\nccc")
        whenKeys(".p")
        thenSelection("aaa\nbbb\n\n")
    }

    fun `test given a line thing then comma l excludes and dot l includes the newline`() {
        given("lines", "a<caret>b\ncd")
        whenKeys(",l")
        thenSelection("ab")
        whenKeys(".l")
        thenSelection("ab\n")
    }

    fun `test given the buffer thing when comma b then everything is selected`() {
        given("buffer", "on<caret>e\ntwo")
        whenKeys(",b")
        thenSelection("one\ntwo")
    }

    fun `test given sentences when comma dot then the sentence around point is selected`() {
        given("sentences", "One. Tw<caret>o. Three.")
        whenKeys(",.")
        thenSelection("Two.")
    }

    fun `test given a curly block in plain text when comma d then the defun fallback selects the braces`() {
        given("pseudo function", "fun x() {\n  bo<caret>dy\n}")
        whenKeys(",d")
        thenSelection("{\n  body\n}")
    }

    fun `test given open bracket r then selects from point back to the thing beginning with cursor at the beginning`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("[r")
        thenSelection("b")
        thenCaretAtSelectionStart()
    }

    fun `test given close bracket r then selects from point to the thing end with cursor at the end`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("]r")
        thenSelection("ar baz")
        thenCaretAtSelectionEnd()
    }

    fun `test given angle bracket aliases then they behave like square brackets`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("<r")
        thenCaretAtSelectionStart()
        thenSelection("b")
    }

    fun `test given no thing at point when comma r then the selection is unchanged`() {
        given("no parens", "he<caret>llo")
        whenKeys(",r")
        thenNoSelection()
    }

    fun `test given o then the enclosing block including delimiters is selected`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("o")
        thenSelection("(bar baz)")
        thenSelType(SelType.BLOCK)
    }

    fun `test given a block selection when o again then it expands to the parent block`() {
        given("nested", "((x<caret>))")
        whenKeys("o")
        thenSelection("(x)")
        whenKeys("o")
        thenSelection("((x))")
    }

    fun `test given a negative argument when o then the block selection is backward`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("-o")
        thenSelection("(bar baz)")
        thenCaretAtSelectionStart()
    }

    fun `test given O then selects from point to the end of the current block`() {
        given("round pair", "foo (b<caret>ar baz) qux")
        whenKeys("O")
        thenSelection("ar baz)")
        thenCaretAtSelectionEnd()
    }

    fun `test given m then the join region between this line and the previous non-empty one is selected`() {
        given("indented continuation", "one\n  t<caret>wo")
        whenKeys("m")
        thenSelType(SelType.JOIN)
        thenSelection("\n  ")
    }

    fun `test given the first line when m then nothing is selected`() {
        given("first line", "o<caret>ne\ntwo")
        whenKeys("m")
        thenNoSelection()
    }

    fun `test given negative argument when - m then the join region reaches forward instead`() {
        given("forward join", "o<caret>ne\n  two")
        whenKeys("-m")
        thenSelType(SelType.JOIN)
        thenSelection("\n  ")
    }
}
