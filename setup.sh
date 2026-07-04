#!/bin/sh
# setup.sh — build and install the ideameow plugin from source. POSIX sh.
#
#   ./setup.sh                 build, install plugin + default ~/.ideameowrc
#   ./setup.sh --list          show detected IDE plugin directories and exit
#   ./setup.sh --build-only    just produce build/distributions/ideameow-*.zip
#   ./setup.sh --rc-only       only install the default .ideameowrc
#   ./setup.sh --skip-build    install the already-built zip
#   ./setup.sh --plugins-dir DIR   install into DIR instead of auto-detecting
#   ./setup.sh --force-rc      overwrite an existing .ideameowrc
#
# Detection covers Linux (~/.local/share/JetBrains/<Product><ver> — plugins
# live directly in that directory), macOS (~/Library/Application Support/
# JetBrains/<Product>/plugins) and WSL (/mnt/c/Users/<user>/AppData/Roaming/
# JetBrains/<Product>/plugins). Only IDEs >= 2026.1 are targeted, matching
# the plugin's since-build=261.
#
# SPDX-License-Identifier: GPL-3.0-or-later

set -eu

MIN_MAJOR=2026
MIN_MINOR=1

here=$(cd "$(dirname "$0")" && pwd)
cd "$here"

do_build=1 do_plugin=1 do_rc=1 force_rc=0 list_only=0
explicit_dir=""

while [ $# -gt 0 ]; do
    case "$1" in
        --list)        list_only=1 ;;
        --build-only)  do_plugin=0 do_rc=0 ;;
        --rc-only)     do_build=0 do_plugin=0 ;;
        --skip-build)  do_build=0 ;;
        --force-rc)    force_rc=1 ;;
        --plugins-dir) shift; explicit_dir="${1:?--plugins-dir needs a path}" ;;
        -h|--help)     sed -n '2,16p' "$0"; exit 0 ;;
        *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
    shift
done

info()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33mwarn:\033[0m %s\n' "$*" >&2; }

# targets are handled as one path per line (paths may contain spaces, e.g.
# "Application Support", but never newlines)
nl='
'

# ---------------------------------------------------------------- detection

ver_ok() { # accepts a directory name like IntelliJIdea2026.1
    v=$(printf '%s' "$1" | sed -n 's/.*\(20[0-9][0-9]\.[0-9][0-9]*\).*/\1/p')
    [ -n "$v" ] || return 1
    major=${v%%.*}
    minor=${v#*.}
    minor=${minor%%.*}
    [ "$major" -gt "$MIN_MAJOR" ] || { [ "$major" -eq "$MIN_MAJOR" ] && [ "$minor" -ge "$MIN_MINOR" ]; }
}

detect_plugin_dirs() {
    # Linux: plugins live directly under the product directory
    for d in "$HOME"/.local/share/JetBrains/*/; do
        [ -d "$d" ] || continue
        ver_ok "$(basename "$d")" && printf '%s\n' "${d%/}"
    done
    # macOS
    for d in "$HOME/Library/Application Support/JetBrains"/*/; do
        [ -d "$d" ] || continue
        ver_ok "$(basename "$d")" && printf '%s\n' "${d%/}/plugins"
    done
    # WSL -> Windows IDEs
    if grep -qi microsoft /proc/version 2>/dev/null; then
        for d in /mnt/c/Users/*/AppData/Roaming/JetBrains/*/; do
            [ -d "$d" ] || continue
            ver_ok "$(basename "$d")" && printf '%s\n' "${d%/}/plugins"
        done
    fi
    return 0
}

# Windows user profiles that own a detected IDE (for the Windows-side rc)
detect_windows_homes() {
    grep -qi microsoft /proc/version 2>/dev/null || return 0
    for d in /mnt/c/Users/*/AppData/Roaming/JetBrains/*/; do
        [ -d "$d" ] || continue
        ver_ok "$(basename "$d")" || continue
        printf '%s\n' "$d" | sed 's|^\(/mnt/c/Users/[^/]*\)/.*|\1|'
    done | sort -u
}

if [ -n "$explicit_dir" ]; then
    targets=$explicit_dir
else
    targets=$(detect_plugin_dirs | sort -u)
fi

if [ "$list_only" -eq 1 ]; then
    info "detected plugin directories (>= ${MIN_MAJOR}.${MIN_MINOR}):"
    if [ -n "$targets" ]; then
        printf '%s\n' "$targets" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
    exit 0
fi

# ------------------------------------------------------------------- build

# The build's toolchain needs the exact java major pinned in mise.toml; a
# different PATH java runs the wrapper but then fails toolchain resolution.
req_java=$(sed -n 's/^java *= *"\([0-9][0-9]*\).*/\1/p' mise.toml 2>/dev/null || true)
req_java=${req_java:-21}

java_ok() { # a java that runs (macOS ships a /usr/bin/java stub that doesn't) AND matches the pin
    jv=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
    [ -n "$jv" ] && [ "$jv" -eq "$req_java" ]
}

if [ "$do_build" -eq 1 ]; then
    info "building the plugin"
    build_via=""
    if [ -x ./gradlew ]; then
        if java_ok; then
            build_via=wrapper
        elif command -v mise >/dev/null 2>&1; then
            info "no java $req_java on PATH — running the wrapper via mise (mise.toml pins java $req_java)"
            build_via=mise-wrapper
        fi
    else
        if java_ok && command -v gradle >/dev/null 2>&1; then
            build_via=gradle
        elif command -v mise >/dev/null 2>&1; then
            info "no gradle wrapper — running gradle via mise (mise.toml pins java + gradle)"
            build_via=mise-gradle
        fi
    fi
    case "$build_via" in
        wrapper)      ./gradlew -q buildPlugin ;;
        mise-wrapper) mise exec -- ./gradlew -q buildPlugin ;;
        gradle)       gradle -q buildPlugin ;;
        mise-gradle)  mise exec -- gradle -q buildPlugin ;;
        *) echo "no way to build: need java $req_java (for ./gradlew or gradle) or mise on the PATH" >&2; exit 1 ;;
    esac
fi

zip=""
if [ "$do_plugin" -eq 1 ]; then
    zip=$(ls -1 build/distributions/ideameow-*.zip 2>/dev/null | sort -V | tail -1 || true)
    [ -n "$zip" ] || { echo "no build/distributions/ideameow-*.zip — run without --skip-build" >&2; exit 1; }
    info "installing $(basename "$zip")"
fi

# ----------------------------------------------------------------- install

installed=0
if [ "$do_plugin" -eq 1 ]; then
    if [ -z "$targets" ]; then
        warn "no IDE plugin directory (>= ${MIN_MAJOR}.${MIN_MINOR}) detected."
        warn "install manually: Settings > Plugins > Install Plugin from Disk > $zip"
    fi
    # split $targets on newlines only, no globbing (both happen once, at the
    # `for` expansion; every path in the body is quoted)
    old_ifs=$IFS
    IFS=$nl
    set -f
    for dir in $targets; do
        mkdir -p "$dir"
        rm -rf "$dir/ideameow"
        unzip -q -o "$zip" -d "$dir"
        info "installed into $dir"
        installed=$((installed + 1))
    done
    set +f
    IFS=$old_ifs
fi

# --------------------------------------------------------------------- rc

install_rc() {
    if [ -f "$1" ] && [ "$force_rc" -eq 0 ]; then
        warn "$1 exists — kept (use --force-rc to overwrite)"
    else
        cp .ideameowrc "$1"
        info "installed default rc: $1"
    fi
}

if [ "$do_rc" -eq 1 ]; then
    install_rc "$HOME/.ideameowrc"
    detect_windows_homes | while IFS= read -r winhome; do
        if [ -d "$winhome" ]; then
            install_rc "$winhome/.ideameowrc"
        fi
    done
fi

# ------------------------------------------------------------------- done

echo
info "done."
if [ "$installed" -gt 0 ]; then
    echo "  * restart the IDE(s) to load the plugin"
fi
echo "  * disable IdeaVim if it is enabled — both intercept typing"
echo "  * in the IDE: SPC ? shows the cheatsheet, SPC c v edits ~/.ideameowrc"
