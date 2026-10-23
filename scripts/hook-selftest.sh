#!/usr/bin/env bash
# Self-test for .githooks/pre-commit. Called by ci.sh (stage "hooks"); can run standalone.
#
# Each case builds a throwaway git repo in a temp directory outside this repo, installs a copy of the hook the same way
# scripts/install-hooks.sh does (core.hooksPath), stages one problem and runs `git commit`. The commit must be blocked
# for the expected reason. Key material and secret values are generated at run time and deleted on exit; nothing is
# committed to this repo. A hook that lets everything through would fail this test.
#
#   1) a *.pem file                           → blocked by the path rule
#   2) a private key body inside a .txt file  → blocked by the key-header check
#   3) a secret value in a YAML config        → blocked by gitleaks (repo rule ticketing-lab-env-secret-value)
#   4) a forbidden word                       → blocked, and the word itself is never printed
#   5) a clean file without a private list    → commit succeeds
#   6) a filename with shell metacharacters   → remains literal, commit succeeds
#   7) clean index with an unstaged key       → commit succeeds; only the index is inspected
#   8) an explicitly configured empty list    → commit is blocked
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d "${TMPDIR:-/tmp}/hook-selftest.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT

if ! docker info >/dev/null 2>&1; then
  echo "hook-selftest: Docker not available, cannot exercise the gitleaks step (treating as failure)" >&2
  exit 1
fi

n=0
new_repo() {
  n=$((n + 1))
  repo="$tmp/repo$n"
  mkdir -p "$repo/.githooks"
  cp "$ROOT/.githooks/pre-commit" "$repo/.githooks/pre-commit"
  cp "$ROOT/.gitleaks.toml" "$repo/.gitleaks.toml"
  chmod +x "$repo/.githooks/pre-commit"
  git -C "$repo" init -q
  git -C "$repo" config core.hooksPath .githooks
  git -C "$repo" config user.name hook-selftest
  git -C "$repo" config user.email hook-selftest@example.invalid
  git -C "$repo" config commit.gpgsign false
}

# commit_in <expect: blocked|allowed> <case label> <expected message, blocked only>; uses $repo and $probe_word
commit_in() {
  local expect="$1" label="$2" message="${3:-}" rc out
  set +e
  out="$(cd "$repo" && FORBIDDEN_WORDS="$probe_word" git commit -q -m "hook self-test" 2>&1)"
  rc=$?
  set -e
  if [[ "$expect" == allowed ]]; then
    if [[ $rc -ne 0 ]]; then
      echo "hook-selftest: FAIL — $label: commit was blocked" >&2
      printf '%s\n' "$out" >&2
      exit 1
    fi
  else
    if [[ $rc -eq 0 ]]; then
      echo "hook-selftest: FAIL — $label: commit went through" >&2
      exit 1
    fi
    if ! grep -q -F -- "$message" <<<"$out"; then
      echo "hook-selftest: FAIL — $label: blocked, but not with '$message'" >&2
      printf '%s\n' "$out" >&2
      exit 1
    fi
  fi
  last_output="$out"
  echo "hook-selftest: ok — $label"
}

# A forbidden word that exists only for this run. The hook reads FORBIDDEN_WORDS before .forbidden-words.
probe_word="probe$(openssl rand -hex 6)"

# 1) *.pem path
new_repo
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$repo/leak.pem" 2>/dev/null
git -C "$repo" add leak.pem
commit_in blocked "*.pem file" "these files must not be committed"

# 2) A large staged file still fails after its working-tree content has been replaced.
new_repo
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$repo/notes.txt" 2>/dev/null
head -c 131072 /dev/zero | tr '\0' x >> "$repo/notes.txt"
git -C "$repo" add notes.txt
printf 'clean unstaged replacement\n' > "$repo/notes.txt"
commit_in blocked "large staged key with clean working tree" "private key header found: notes.txt"

# 3) secret value in a config file (only gitleaks catches this one)
new_repo
mkdir -p "$repo/config"
printf 'lab:\n  waiting:\n    WAIT_SECRET: %s\n' "$(openssl rand -hex 24)" > "$repo/config/app.yml"
git -C "$repo" add config/app.yml
commit_in blocked "secret value in config/app.yml" "gitleaks found a leak"
# gitleaks must have reported a finding, not merely failed to run (case 5 shows it runs cleanly in such a repo).
grep -q -F "leaks found: 1" <<<"$last_output" || { echo "hook-selftest: FAIL — gitleaks exited non-zero without a finding" >&2; exit 1; }

# 4) forbidden word; the output must show the location only
new_repo
printf 'line one\nmentions %s here\n' "$probe_word" > "$repo/readme.txt"
git -C "$repo" add readme.txt
commit_in blocked "forbidden word in readme.txt" "forbidden word in contents — readme.txt, lines 2"
if grep -q -F -- "$probe_word" <<<"$last_output"; then
  echo "hook-selftest: FAIL — the hook printed the forbidden word itself" >&2
  exit 1
fi

# 5) Public contributors need no private list.
probe_word=""
new_repo
printf 'nothing to see\n' > "$repo/ok.txt"
git -C "$repo" add ok.txt
commit_in allowed "clean file without a private list"
[[ "$(git -C "$repo" rev-list --count HEAD)" == "1" ]] || { echo "hook-selftest: FAIL — clean commit missing" >&2; exit 1; }

# 6) Filenames are arguments, never shell source.
new_repo
special='quoted" $(touch HOOK_FILENAME_EXECUTED) `touch HOOK_BACKTICK_EXECUTED`.txt'
printf 'safe fixture\n' > "$repo/$special"
git -C "$repo" add -- "$special"
commit_in allowed "filename with shell metacharacters"
[[ ! -e "$repo/HOOK_FILENAME_EXECUTED" && ! -e "$repo/HOOK_BACKTICK_EXECUTED" ]] || {
  echo "hook-selftest: FAIL — filename executed as code" >&2; exit 1;
}

# 7) An unstaged edit cannot replace the content that is about to be committed.
new_repo
printf 'clean staged content\n' > "$repo/notes.txt"
git -C "$repo" add notes.txt
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$repo/notes.txt" 2>/dev/null
commit_in allowed "clean index with an unstaged key"

# 8) Configuring an empty list must not silently disable a requested check.
new_repo
printf '# no patterns\n' > "$repo/.forbidden-words"
printf 'safe fixture\n' > "$repo/ok.txt"
git -C "$repo" add ok.txt
commit_in blocked "configured empty name list" "configured name list is empty"

echo "hook-selftest: all 8 cases passed"
