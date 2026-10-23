#!/usr/bin/env bash
# Installs the git hooks by using this repo's .githooks/ as the hooks path. Run once after cloning.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
chmod +x .githooks/*
git config core.hooksPath .githooks
# Treat Unicode file names the same on macOS (NFD) and Linux (NFC), and print paths unescaped.
git config core.precomposeunicode true
git config core.quotepath false
echo "install-hooks: core.hooksPath=$(git config core.hooksPath)"
if [[ -z "${FORBIDDEN_WORDS:-}" && ! -f .forbidden-words ]]; then
  echo "install-hooks: optional name check is not configured. Set FORBIDDEN_WORDS or create .forbidden-words (one pattern per line, gitignored)."
fi
echo "install-hooks: Docker enables staged gitleaks checks; default CI requires Docker but no private name list."
