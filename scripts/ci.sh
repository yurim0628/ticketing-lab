#!/usr/bin/env bash
# CI entry point. GitHub Actions calls this script, so local and hosted runs use the same checks.
#
# Usage: scripts/ci.sh [stage ...]      (no argument = the seven default stages below)
#   secret-scan  scanner self-test, tracked key files, gitleaks       scripts/secret-scan.sh
#   hooks        pre-commit hook self-test in a throwaway repo         scripts/hook-selftest.sh
#   build        compile everything and build the boot jars (no tests)
#   unit         unit tests (*Test, no Docker)                         gradle test --rerun
#   it           integration tests (*IT, Testcontainers)               gradle integrationTest --rerun
#   image        non-root image build and container smoke checks      scripts/image-smoke.sh
#   compose      isolated stack, request flow, recovery and reset      scripts/lab.sh smoke-ci
# Optional explicit stage: name-scan, using FORBIDDEN_WORDS or a gitignored .forbidden-words file.
# It fails without a nonempty list. The default stages need no private list or repository secrets.
#
# Test tasks run with --rerun so a green result always means the tests ran, never a build-cache hit.
# Needs: JDK 21, Docker with Compose, Python 3, git, openssl and Bash.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEFAULT_STAGES=(secret-scan hooks build unit it image compose)
ALL_STAGES=("${DEFAULT_STAGES[@]}" name-scan)
GRADLE=(./gradlew --console=plain)
if [[ -n "${CI:-}" ]]; then
  GRADLE+=(--no-daemon)
fi

stages=("$@")
if [[ ${#stages[@]} -eq 0 ]]; then
  stages=("${DEFAULT_STAGES[@]}")
fi
for s in "${stages[@]}"; do
  if [[ " ${ALL_STAGES[*]} " != *" $s "* ]]; then
    echo "ci: unknown stage '$s' (known: ${ALL_STAGES[*]})" >&2
    exit 2
  fi
done

# Sums the JUnit XML reports of one Gradle test task name across all modules: "tests failures errors skipped".
test_totals() {
  local task="$1" tests=0 failures=0 errors=0 skipped=0 line
  while IFS= read -r line; do
    tests=$((tests + $(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$line")))
    failures=$((failures + $(sed -E 's/.* failures="([0-9]+)".*/\1/' <<<"$line")))
    errors=$((errors + $(sed -E 's/.* errors="([0-9]+)".*/\1/' <<<"$line")))
    skipped=$((skipped + $(sed -E 's/.* skipped="([0-9]+)".*/\1/' <<<"$line")))
  done < <(find . -path "*/build/test-results/$task/TEST-*.xml" -not -path './.gradle/*' -exec grep -h -m1 '<testsuite ' {} +)
  echo "$tests tests, $failures failures, $errors errors, $skipped skipped"
}

run_stage() {
  case "$1" in
    name-scan)   scripts/name-scan.sh ;;
    secret-scan) scripts/secret-scan.sh ;;
    hooks)       scripts/hook-selftest.sh ;;
    build)       "${GRADLE[@]}" assemble testClasses ;;
    unit)        "${GRADLE[@]}" test --rerun && echo "ci: unit — $(test_totals test)" ;;
    it)          "${GRADLE[@]}" integrationTest --rerun && echo "ci: it — $(test_totals integrationTest)" ;;
    image)       "${GRADLE[@]}" bootJar && scripts/image-smoke.sh ;;
    compose)     scripts/lab.sh smoke-ci ;;
  esac
}

summary=()
overall=0
for stage in "${stages[@]}"; do
  echo "ci: ── $stage ──────────────────────────────"
  start=$(date +%s)
  if run_stage "$stage"; then
    result=passed
  else
    result=FAILED
    overall=1
  fi
  summary+=("$(printf '%-12s %-7s %4ss' "$stage" "$result" "$(( $(date +%s) - start ))")")
  if [[ $overall -ne 0 ]]; then
    break   # later stages would only add noise; fix this one first
  fi
done

echo "ci: ── summary ──────────────────────────────"
printf 'ci:   %s\n' "${summary[@]}"
if [[ $overall -eq 0 ]]; then
  echo "ci: all ${#summary[@]} stages passed"
else
  echo "ci: stage failed; remaining stages not run" >&2
fi
exit $overall
