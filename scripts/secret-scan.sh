#!/usr/bin/env bash
# Secret scan. Called by ci.sh; can also run standalone: scripts/secret-scan.sh
#
#   1) Self-test    : create a key and a secret value in a temp directory outside the repo and check that
#                     gitleaks (dir) and the key grep catch them. If they don't, the scanner is broken, so fail.
#                     The temp files are never committed and are deleted on exit.
#   2) Tracked files: fail if *.pem, *.key, *.jwt, .env, .secrets/ etc. are tracked by git.
#   3) Key body grep: fail if a tracked or new non-ignored file contains a private key header.
#   4) gitleaks     : available history (git) plus tracked and new non-ignored working-tree files (dir).
#
# gitleaks is not installed locally; it runs from a Docker image.
set -euo pipefail

GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-zricethezav/gitleaks:v8.30.1}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
source "$ROOT/scripts/lib/scan-files.sh"
scripts/scanner-selftest.sh

# The pattern is split like this so that this script does not match itself.
KEY_HEADER_RE='PRIVATE[[:space:]]KEY'
FORBIDDEN_PATH_RE='(\.(pem|key|p12|jks|jwt)$)|(^|/)\.env$|(^|/)\.env\.[^/]*$|(^|/)\.secrets/|(^|/)dev-keys/|(^|/)\.forbidden-words$'

fail=0
tmp="$(mktemp -d "${TMPDIR:-/tmp}/secret-scan.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT

gitleaks() {
  # $1 = host directory to mount, rest = gitleaks arguments
  local dir="$1"; shift
  docker run --rm \
    -v "$dir:/scan:ro" \
    -v "$ROOT/.gitleaks.toml:/gitleaks.toml:ro" \
    "$GITLEAKS_IMAGE" "$@" --config /gitleaks.toml --no-banner --redact --ignore-gitleaks-allow --log-level warn
}

if ! docker info >/dev/null 2>&1; then
  echo "secret-scan: Docker not available, cannot run gitleaks (treating as failure)" >&2
  exit 1
fi

# ── 1) Self-test ─────────────────────────────────────────────
selftest="$tmp/selftest"
mkdir -p "$selftest"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$selftest/throwaway.key" 2>/dev/null
printf 'WAIT_SECRET=%s\n' "$(openssl rand -base64 32)" > "$selftest/throwaway.env"

set +e
gitleaks "$selftest" dir /scan --report-format json --report-path - > "$tmp/selftest.json" 2>"$tmp/selftest.err"
rc=$?
set -e
found_rules="$( (grep -o '"RuleID": *"[^"]*"' "$tmp/selftest.json" || true) | sed 's/.*: *"//; s/"$//' | sort -u | tr '\n' ' ')"
if [[ $rc -ne 1 ]] || [[ "$found_rules" != *private-key* ]] || [[ "$found_rules" != *ticketing-lab-env-secret-value* ]]; then
  echo "secret-scan: self-test failed — gitleaks did not catch the throwaway key or secret (exit=$rc, rules=[$found_rules])" >&2
  cat "$tmp/selftest.err" >&2 || true
  exit 1
fi
if ! grep -q -E "$KEY_HEADER_RE" "$selftest/throwaway.key"; then
  echo "secret-scan: self-test failed — the key header grep did not catch the throwaway key" >&2
  exit 1
fi
echo "secret-scan: self-test passed (gitleaks caught the throwaway key and secret, rules: $found_rules)"

# ── 2) Files that must not be tracked ────────────────────────
git ls-files -z > "$tmp/tracked"
tracked_bad=0
while IFS= read -r -d '' file; do
  if [[ "$file" =~ $FORBIDDEN_PATH_RE && "${file##*/}" != .env.example ]]; then
    printf 'secret-scan: file must not be tracked: %q\n' "$file" >&2
    tracked_bad=$((tracked_bad + 1))
    fail=1
  fi
done < "$tmp/tracked"
[[ "$tracked_bad" -ne 0 ]] || echo "secret-scan: 0 tracked key or secret files"

# ── 3) Key body grep (tracked + new non-ignored files) ───────
candidates="$tmp/candidates.txt"
git ls-files -z --cached --others --exclude-standard > "$candidates"
tree="$tmp/tree"
copy_scan_files "$ROOT" "$tree" "$candidates"
key_hits=0
while IFS= read -r -d '' file; do
  [[ -f "$tree/$file" ]] || continue
  if grep -I -E "$KEY_HEADER_RE" "$tree/$file" >/dev/null; then
    printf 'secret-scan: private key header found: %q\n' "$file" >&2
    key_hits=$((key_hits + 1))
    fail=1
  else
    rc=$?
    [[ "$rc" -eq 1 ]] || { echo "secret-scan: could not inspect a snapshot file" >&2; exit "$rc"; }
  fi
done < "$candidates"
[[ "$key_hits" -ne 0 ]] || echo "secret-scan: 0 private key headers"

# ── 4) gitleaks: available history + working-tree snapshot ─
if git rev-parse --verify -q HEAD >/dev/null; then
  if gitleaks "$ROOT" git /scan --log-opts=--all; then
    echo "secret-scan: gitleaks found 0 leaks in history ($(git rev-list --all --count) commits)"
  else
    echo "secret-scan: history scan failed (finding or scanner error)" >&2
    fail=1
  fi
fi

# Ignored paths are excluded unless tracked. Symlink targets are inspected as text, not dereferenced.
if gitleaks "$tree" dir /scan; then
  echo "secret-scan: gitleaks found 0 leaks in the working tree ($SCAN_COPIED_FILES files)"
else
  echo "secret-scan: working-tree scan failed (finding or scanner error)" >&2
  fail=1
fi

exit $fail
