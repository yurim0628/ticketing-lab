#!/usr/bin/env bash
# Regression checks for the file snapshot used by secret-scan. No Docker or Git repository required.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib/scan-files.sh"
tmp="$(mktemp -d "${TMPDIR:-/tmp}/scanner-selftest.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT

fail() { echo "scanner-selftest: FAIL — $*" >&2; exit 1; }
mkdir -p "$tmp/source/nested" "$tmp/destination"
cd "$tmp"

# These names must remain literal data, including characters that used to reach sh -c.
special='quoted" $(touch SCAN_FILENAME_EXECUTED) `touch SCAN_BACKTICK_EXECUTED`.txt'
names=("$special" 'nested/file with spaces.txt' '-leading-dash.txt' $'line\nbreak.txt')
: > "$tmp/files"
for name in "${names[@]}"; do
  printf 'safe fixture\n' > "$tmp/source/$name"
  printf '%s\0' "$name" >> "$tmp/files"
done
printf 'deleted.txt\0' >> "$tmp/files"
printf 'external fixture\n' > "$tmp/external.txt"
ln -s ../external.txt "$tmp/source/link.txt"
printf 'link.txt\0' >> "$tmp/files"

copy_scan_files "$tmp/source" "$tmp/destination" "$tmp/files"
[[ "$SCAN_COPIED_FILES" -eq 5 ]] || fail "unexpected snapshot size"
for name in "${names[@]}"; do
  cmp -s "$tmp/source/$name" "$tmp/destination/$name" || fail "filename or content changed"
done
[[ ! -e "$tmp/SCAN_FILENAME_EXECUTED" && ! -e "$tmp/SCAN_BACKTICK_EXECUTED" ]] || fail "filename executed as code"
[[ ! -e "$tmp/destination/deleted.txt" ]] || fail "deleted file was copied"
[[ ! -L "$tmp/destination/link.txt" ]] || fail "snapshot retained a symlink"
[[ "$(cat "$tmp/destination/link.txt")" == '../external.txt' ]] || fail "symlink target was followed"

# An empty candidate list is valid and cannot cause grep or xargs to wait for stdin.
: > "$tmp/empty-list"
copy_scan_files "$tmp/source" "$tmp/empty" "$tmp/empty-list"
[[ "$SCAN_COPIED_FILES" -eq 0 ]] || fail "empty snapshot was not empty"
echo "scanner-selftest: filename, deletion, symlink and empty-list checks passed"
