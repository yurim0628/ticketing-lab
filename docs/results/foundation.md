# Foundation verification

**Recorded:** 2026-10-24, Asia/Seoul. **Outcome:** all six verification stages passed locally.

This record covers the initial runtime and testing foundation. It is a functional verification run, not a queue, booking, payment, or performance experiment. The GitHub-hosted workflow was not executed as part of this run.

## Reproduction and environment

From the repository root, with JDK 21, Docker, Git, and OpenSSL available:

```bash
CI=true scripts/ci.sh
```

| Component | Recorded value |
|---|---|
| Host | macOS, arm64 |
| JDK | 21.0.9 |
| Gradle | 8.14.3, wrapper |
| Spring Boot | 3.5.16 |
| Docker Engine | 29.1.3 |
| Integration infrastructure | MySQL 8.4, Redis 7.4, provisioned by Testcontainers |
| Source state | Initial working tree before the first commit |

The Gradle dependency cache was populated before the run. Test stages used `--rerun`, so the test counts below are from newly executed tests. This run does not measure first-download time or prove compatibility with every operating system.

## Observed results

| Stage | Result | Evidence |
|---|---|---|
| Secret scanning | Passed | Scanner self-tests detected generated probe credentials; current source files contained no reported leaks |
| Git hook regression checks | Passed | All 8 cases, including partial staging, hostile filenames, and optional policy configuration |
| Build | Passed | All application JARs and test classes compiled |
| Ordinary tests | Passed | 41 executed, 0 failures, 0 errors, 0 skipped |
| Integration tests | Passed | 21 executed, 0 failures, 0 errors, 0 skipped |
| Container images | Passed | Four images built; non-root user and read-only JAR checks passed |

The image checks successfully started `fake-ext` with a read-only root filesystem and a 512 MiB memory limit. Health reported `UP`, PID 1 ran as UID 10001, and the reported heap limit matched the configured memory fraction. Waiting's API role exited nonzero when its required credentials were omitted.

Selected output from the run:

```text
hook-selftest: all 8 cases passed
ci: unit — 41 tests, 0 failures, 0 errors, 0 skipped
ci: it — 21 tests, 0 failures, 0 errors, 0 skipped
image-smoke: all checks passed
ci: all 6 stages passed
```

[Machine-readable test totals](foundation-summary.json) were extracted from the generated JUnit XML reports. Counts are grouped by Gradle test task and module. Modules without test sources contribute no executed tests.

## Interpretation and limits

The startup/configuration contract, migration constraints, selected timeout behavior, module boundaries, and packaging checks are reproducible on the recorded environment. The [testing guide](../testing.md) maps those checks to their source files.

No business HTTP workflow, concurrent reservation protocol, payment recovery process, or throughput target is established by this run. The image check does not start a complete multi-service environment. There was no Git history to scan in the initial repository; future runs scan the history available in the checkout.

Use the [experiment template](../experiments/TEMPLATE.md) for future behavioral and performance investigations, including controlled failures and raw measurements. Re-run the verification command after source changes instead of treating this historical record as a current CI result.
