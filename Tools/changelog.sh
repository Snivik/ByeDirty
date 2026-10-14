#!/usr/bin/env bash
# Reads CHANGELOG.md, where each release is a "## <version> — <date>" section, newest first.
#
#   Tools/changelog.sh version         top section's version, e.g. 0.1.0
#   Tools/changelog.sh release-notes   top section's body, for the GitHub release
#   Tools/changelog.sh next [minor]    the version after the top one (patch bump by default)
set -euo pipefail
cd "$(dirname "$0")/.."
file=CHANGELOG.md

top_version() { grep -m1 -E '^## [0-9]+\.[0-9]+\.[0-9]+' "$file" | sed -E 's/^## ([0-9]+\.[0-9]+\.[0-9]+).*/\1/'; }

case "${1:-}" in
  version) top_version ;;
  release-notes)
    awk '/^## [0-9]+\.[0-9]+\.[0-9]+/ { if (seen) exit; seen = 1; next } seen' "$file" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
    ;;
  next)
    IFS=. read -r major minor patch <<< "$(top_version)"
    if [ "${2:-patch}" = minor ]; then echo "$major.$((minor + 1)).0"; else echo "$major.$minor.$((patch + 1))"; fi
    ;;
  *) echo "usage: $0 version | release-notes | next [minor]" >&2; exit 2 ;;
esac
