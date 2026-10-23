#!/usr/bin/env bash
# Optional custom name check. Run scripts/name-scan.sh or scripts/ci.sh name-scan explicitly.
#
# The forbidden-word list is not in the repo. It is looked up in this order:
#   1) Environment variable FORBIDDEN_WORDS (newline- or comma-separated)
#   2) .forbidden-words at the repo root (gitignored; one word per line)
# If neither exists, this explicit check fails. Default public CI does not invoke it.
#
# Scope: contents and paths of tracked and new non-ignored files, commit messages, and commit author and committer names and emails.
# Historical file contents and historical paths are outside this check's scope.
# Output shows only locations (file:line, commit hash), never the words themselves.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export LC_ALL=C   # byte-wise comparison (multibyte UTF-8 words match as is); case folding applies to ASCII only

tmp="$(mktemp -d "${TMPDIR:-/tmp}/name-scan.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT
words="$tmp/words"

if [[ -n "${FORBIDDEN_WORDS:-}" ]]; then
  printf '%s\n' "$FORBIDDEN_WORDS" | tr ',' '\n' > "$tmp/raw"
elif [[ -f .forbidden-words ]]; then
  cp .forbidden-words "$tmp/raw"
else
  echo "name-scan: no forbidden-word list (FORBIDDEN_WORDS or .forbidden-words); treating as failure" >&2
  exit 1
fi
# Blank lines must be removed: an empty pattern makes grep -f match every line.
tr -d '\r' < "$tmp/raw" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | grep -v -E '^(#|$)' > "$words" || true
count="$(wc -l < "$words" | tr -d ' ')"
if [[ "$count" -eq 0 ]]; then
  echo "name-scan: forbidden-word list is empty; treating as failure" >&2
  exit 1
fi

# ── Self-test: each listed word must be caught in a probe file ──
i=0
while IFS= read -r w; do
  i=$((i + 1))
  printf 'prefix %s suffix\n' "$w" > "$tmp/probe"
  if ! grep -q -i -F -f "$words" "$tmp/probe"; then
    echo "name-scan: self-test failed — word #${i} in the list was not caught" >&2
    exit 1
  fi
done < "$words"
echo "name-scan: self-test passed (all ${count} forbidden words caught)"

fail=0

# ── File paths and contents ──────────────────────────────────
git ls-files -z --cached --others --exclude-standard > "$tmp/files"
nfiles="$(tr -cd '\0' < "$tmp/files" | wc -c | tr -d ' ')"

# Printing a path that contains a forbidden word would leak the word, so only its index is printed.
n=0
path_hit_list="$tmp/path_hits"
: > "$path_hit_list"
while IFS= read -r -d '' f; do
  n=$((n + 1))
  if printf '%s\n' "$f" | grep -q -i -F -f "$words"; then
    echo "name-scan: forbidden word in file path — git ls-files entry #$n" >&2
    printf '%s\n' "$f" >> "$path_hit_list"
    fail=1
  fi
done < "$tmp/files"

content_hits="$tmp/content_hits"
(xargs -0 grep -H -n -I -i -F -f "$words" -- < "$tmp/files" 2>/dev/null || true) | cut -d: -f1,2 > "$content_hits"
if [[ -s "$content_hits" ]]; then
  while IFS= read -r loc; do
    file="${loc%:*}"
    if grep -q -x -F -- "$file" "$path_hit_list"; then
      echo "name-scan: forbidden word in contents — (file with redacted path):${loc##*:}" >&2
    else
      echo "name-scan: forbidden word in contents — $loc" >&2
    fi
  done < "$content_hits"
  fail=1
fi

# ── Commit messages, authors and committers ──────────────────
ncommits=0
if git rev-parse --verify -q HEAD >/dev/null; then
  ncommits="$(git rev-list --all --count)"
  while IFS= read -r c; do
    if git show -s --format='%an%n%ae%n%cn%n%ce%n%B' "$c" | grep -q -i -F -f "$words"; then
      echo "name-scan: forbidden word in commit message or author — ${c:0:10}" >&2
      fail=1
    fi
  done < <(git rev-list --all)
fi

if [[ $fail -eq 0 ]]; then
  echo "name-scan: 0 forbidden words (${nfiles} files, ${ncommits} commits)"
fi
exit $fail
