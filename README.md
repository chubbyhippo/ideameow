# ideameow — Meow QWERTY modal editing for IntelliJ

An IntelliJ Platform plugin implementing [meow](https://github.com/meow-edit/meow)'s
suggested **QWERTY layout** as a native modal editing engine — no IdeaVim
involved (disable IdeaVim while this is enabled; both intercept typing).

States: **NORMAL** (keys are commands, block cursor), **INSERT** (`i a c I A`
enter, `ESC` leaves), **MOTION** (read-only editors: `j`/`k`/`SPC` by
default), **KEYPAD** (`SPC` leader dispatching IDE actions), and a **BEACON**
approximation built on IntelliJ's native multiple carets. The status bar shows
the current state. Like meow itself, the plugin binds **no keys in code**: the
whole keymap — the NORMAL/MOTION layout *and* the `SPC` keypad table — lives
in an `.ideameowrc` bundled with the plugin (meow's suggested QWERTY layout,
written as `meow-normal-define-key`-style lines), and `~/.ideameowrc`
overrides it entry by entry.

Meow runs in main file editors and in multi-line writable dialog fields such
as the VCS commit message box (like IdeaVim's `ideavimsupport=dialog`);
one-line fields, consoles, and diff viewers keep native editing.

## Build & install

```bash
cd ideameow
./setup.sh                  # build + install into every detected 2026.1+ IDE
                            # (Linux, macOS, and Windows IDEs from WSL) and
                            # install the default ~/.ideameowrc
./setup.sh --list           # just show which IDE dirs it would target
gradle buildPlugin          # or: gradle runIde  (sandbox IDE for a test drive)
```

Then *Settings → Plugins → ⚙ → Install Plugin from Disk…* and pick
`build/distributions/ideameow-0.1.0.zip`. Requires a JDK 21 toolchain (emits Java 21 bytecode);
targets IDE 2026.1 and anything newer.

## The default layout (meow KEYBINDING_QWERTY, verified against meow's source)

There is no key table in the plugin's code: this whole layout is the repo's
`.ideameowrc`, bundled into the plugin jar as the default layer and written
as `nmap <key> <meow-command>` lines, so any key can be rebound from
`~/.ideameowrc` (Dvorak, Colemak, or piecemeal) — see the configuration
guide below.

Movement/selection: `h j k l` move (a char-selection survives, anything else
is cancelled), `H J K L` extend a char selection, `w/W` mark word/symbol (and
push it to the search ring), `e/E`·`b/B` next/back word/symbol — these extend
the selection after `w/W` (meow's `(expand . word)` rule), `x` line
(repeat/digits extend), `Q`/`X` goto-line, `f`/`t` find/till char, `o`/`O`
block / to-block, `m` join-region, `,` `.` `[` `]` inner/bounds/begin/end of
thing with meow's char table (`r s c g e w b p l v d .`), `;` reverse,
`z` pop selection, `v` visit (regexp), `n` search (backward when the selection
is reversed), digits = expand-by-N with painted hints (`0` = 10) or a count
when nothing is selected, `-` negative argument.

Editing: `i/a` insert/append, `I/A` open above/below, `c` change, `s` kill,
`d/D` delete forward/backward, `y` save, `p` yank, `r` replace-with-clipboard,
`u` undo, `'` repeat, `g` cancel, `q` close tab, `ESC` back to NORMAL.

Grab/beacon: `G` grabs the selection (highlighted secondary region; with a
grab active, any selection made inside it — `w`, `x`, `f`… — drops a caret on
every similar range: edit them all at once, `ESC` collapses). `R` swap-grab
exchanges selection and grab text, `Y` sync-grab re-stashes.

Keypad: `SPC b/x/c/m/w …` mirror the Emacs/Meow keypad of the companion
`.ideavimrc`/`init.el` (GotoFile, SaveAll, splits, font size…), `SPC 1-9`
digit argument, `SPC ?` cheatsheet, `SPC /` describe-key, `SPC c v` / `SPC c V`
edit / reload the config file.

## ~/.ideameowrc — the configuration guide

ideameow reads an `.ideavimrc`-style file from your home directory:
`~/.ideameowrc` on Linux/macOS, `C:\Users\<you>\.ideameowrc` on Windows.

**Getting started**

1. The repo's `.ideameowrc` ships *inside* the plugin as the default keymap
   (the QWERTY layout, the keypad table, the ported `.ideavimrc` leader
   scheme), so a home file only needs the lines you want changed. Press
   `SPC c v` in the IDE to create and open it — or copy the whole shipped
   file there and edit anything.
2. Edit, then reload with `SPC c V` (or *Find Action → Reload .ideameowrc*).
   A balloon confirms how many mappings loaded; parse problems are listed
   with their line numbers.

**Syntax reference**

| Line | Meaning |
|---|---|
| `" text` or `# text` | comment (also allowed at the end of a line: `nmap S <action>(X) " jump`) |
| `nmap <key> <meow-command>` | bind a NORMAL-mode key to a named meow command, e.g. `nmap n meow-mark-word` — this is how the whole layout is remapped |
| `nmap <key> <action>(ActionId)` | NORMAL-mode key runs an IDE action (overrides the default meow key) |
| `nmap <key> <keys>` | NORMAL-mode key replays a meow key sequence, e.g. `nmap Z ,b` |
| `nnoremap` / `noremap` | like `nmap`/`map`, but the replayed keys resolve through the bundled defaults, ignoring your other mappings |
| `mmap` / `mnoremap` | the same three target forms, for MOTION mode (read-only editors) |
| `map <leader><seq> <action>(Id)` | keypad entry: `SPC` + sequence runs the action (yours override the bundled defaults) |
| `map <leader><seq> <keys>` | keypad entry replaying meow keys after the keypad closes |
| `desc <leader><seq> <text>` | which-key label for an entry (exact seq) or a group (prefix) |
| `let g:WhichKeyDesc_x = "<leader>x text"` | same as `desc` — paste `.ideavimrc` lines unchanged |
| `set timeoutlen=300` | which-key popup delay in milliseconds (the bundled default sets 300) |
| `set which-key` / `set nowhich-key` | popup on/off (default on) |

Key notation: plain printable characters, plus `<Space>` and `<lt>`. Find an
action's id via *Tools → Internal Actions* or IdeaVim's `:actionlist` —
they're the same ids `.ideavimrc` uses in `<action>(...)`.

**Relayouting (Dvorak, Colemak, …)**

The layout section of the bundled `.ideameowrc` IS the default keymap — an
`nmap`/`mmap` line per key, exactly like a `meow-normal-define-key` /
`meow-motion-define-key` block in Emacs. The command names are meow's own
(`meow-next-word`, `meow-kill`, …) plus `repeat` and `ignore` — that section
doubles as the full command reference. A right-hand side that names a known
command binds the command; `ignore` disables a key; a misspelled `meow-*`
name is reported as an error; anything else is replayed as keys. In a home
`~/.ideameowrc`, a key you don't mention keeps its bundled binding — rebind
it or `ignore` it to change that.

**Semantics worth knowing**

- Mapped keys work with `'` (repeat): repeating a mapped key re-runs its
  binding. Key-replay mappings are recursion-guarded (a self-referencing map
  stops at depth 8 with a hint instead of freezing the IDE).
- `repeat` itself is a bindable command — put it on any key and the default
  `'` can be reassigned like everything else.
- Reserved: keypad `0-9` (digit argument), `?` (cheatsheet), `/` (describe
  key); `SPC` itself can't be remapped (it is always the keypad key in both
  NORMAL and MOTION). Only printable keys reach the modal engine, so `<CR>`,
  `<Esc>`, and modifier chords can't be a LHS — put those on the IDE keymap
  instead.
- Unknown `set` options and `let` lines are ignored, so pasting your whole
  `.ideavimrc` won't error — only the lines ideameow understands take effect.

**which-key**

On any pending prefix — a keypad `SPC` sequence, or the `,` `.` `[` `]`
thing table — a non-focusable popup lists the continuations after
`timeoutlen` ms: terminal entries show their `desc` (falling back to the
action id or key sequence), groups show the group's `desc` (falling back to
`+more`). It never takes focus; just keep typing. `SPC ?` still opens the
full cheatsheet and `SPC / <key>` describes one prefix in detail.

**What the bundled default gives you**

The repo's `.ideameowrc` opens with the full meow QWERTY layout as
`nmap`/`mmap` lines and the complete keypad (`SPC`) table as
`map <leader>` lines — these ARE the plugin's defaults, bundled into the jar —
then ports the companion `.ideavimrc` 1:1 where the modal models overlap: all
the IntelliJ leader groups (`SPC .` settings, `SPC a` tool windows,
`SPC d/e/f/g/h/i/j/k/l/n/o/p/q/r/s/t/u/v` …) with which-key labels for every
group, `S`/`Q` as the avy jumps (AceJump plugin), split resizing on `=` `_`
`+`, and `SPC ]`/`SPC [` for next/prev change, diff, and error (the
`]c [c ]d [d ]e [e` of the vim config). Deliberately not ported —
with reasons in the file's footer — are the meow emulation itself (native
here), vim/ex options and commands, and modifier-key bindings. Two
divergences: `-` keeps meow's negative-argument instead of split-resize,
because unlike vim this engine has real negative counts; and since a later
line for the same key wins within a file, the avy section makes
`Q = AceLineAction` the effective default — `nmap Q meow-goto-line` in your
home rc restores meow's own binding (`X` always has it).

## Known deviations from meow (documented, not accidental)

- `U` (meow-undo-in-selection) falls back to plain undo — IntelliJ's undo
  stack cannot be scoped to a region.
- Beacon uses native multiple carets instead of kmacro recording; `SPC c j`
  avy jumps need the AceJump plugin (otherwise a hint appears).
- Block/string/defun "things" use a text scan (same-line strings skipped) plus
  a PSI heuristic for defun — close to, but not literally, Emacs' syntax-ppss.
- The kill-ring is the system clipboard (`meow-use-clipboard` behavior);
  `kill-line` does not append consecutive kills.
- MOTION covers read-only file editors; IDE tool windows keep their own keys
  (the commit message box is the exception — it gets full meow editing).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) for the full text.
