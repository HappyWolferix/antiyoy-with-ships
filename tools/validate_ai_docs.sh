#!/bin/sh
# validate-ai-docs — the docs feedback loop.
#
# Checks, without any dependencies beyond POSIX sh + grep/sed/find:
#   1. every repo path mentioned in docs/ai/manifest.yml exists
#   2. every AGENTS.md in the tree is registered in the manifest (root one excepted)
#   3. every relative markdown link in docs/ and in AGENTS.md files resolves
#
# Exit code 0 = clean, 1 = problems found (each printed with a reason).

set -u
cd "$(dirname "$0")/.." || exit 1

MANIFEST=docs/ai/manifest.yml
fail=0

problem() {
    echo "PROBLEM: $1"
    fail=1
}

[ -f "$MANIFEST" ] || { problem "missing $MANIFEST"; exit 1; }

# 1. Every path-looking token in the manifest must exist.
#    Matches values like docs/foo.md, core/AGENTS.md, RUNNING.md inside lists or scalars.
grep -oE '(docs|core|desktop|tools|assets)/[A-Za-z0-9_./-]+|(RUNNING|README|AGENTS)\.md' "$MANIFEST" \
    | sort -u | while read -r p; do
    # skip directory-style entries (implementation plan dirs)
    if [ ! -e "$p" ]; then
        echo "PROBLEM: manifest references missing path: $p"
    fi
done | grep . && fail=1

# 2. Every AGENTS.md overlay must be registered in the manifest.
find core desktop tools assets docs -name AGENTS.md 2>/dev/null | while read -r f; do
    if ! grep -q "$f" "$MANIFEST"; then
        echo "PROBLEM: overlay not registered in manifest: $f"
    fi
done | grep . && fail=1

# 3. Relative markdown links must resolve.
#    Extracts ](target) links, ignores http(s) and pure anchors.
{ find docs -name '*.md' 2>/dev/null; find . -maxdepth 8 -name AGENTS.md -not -path './build/*' -not -path './.gradle/*'; } \
    | sort -u | while read -r doc; do
    dir=$(dirname "$doc")
    grep -oE '\]\([^)#]+' "$doc" | sed 's/^](//' | while read -r target; do
        case "$target" in
            http*|mailto*) continue ;;
        esac
        if [ ! -e "$dir/$target" ] && [ ! -e "$target" ]; then
            echo "PROBLEM: $doc links to missing target: $target"
        fi
    done
done | grep . && fail=1

if [ "$fail" -eq 0 ]; then
    echo "validate-ai-docs: OK"
else
    echo "validate-ai-docs: FAILED"
fi
exit "$fail"
