# Walking-skeleton verification

**Recorded:** 2026-10-24, Asia/Seoul. **Outcome:** all seven verification stages passed locally.

Step 01 connects the application processes before queue registration begins. This run demonstrates startup, edge routing, one outbound HTTP dependency, and an isolated data lifecycle. It is a functional check, not a load experiment.

## Reproduction and environment

The local stages were executed in three invocations from the repository root, using a populated Gradle cache:

```bash
scripts/ci.sh build unit it
scripts/ci.sh compose
scripts/ci.sh secret-scan hooks image
```

`scripts/ci.sh` runs all seven stages in their default order. `scripts/lab.sh smoke-ci` runs just the isolated Compose check, including the application build. The [running guide](../running.md) describes the persistent manual stack.

| Component | Recorded value |
|---|---|
| Host | macOS, arm64 |
| Host JDK | 21.0.9 |
| Gradle / Spring Boot | 8.14.3 / 3.5.16 |
| Docker Engine / Compose | 29.1.3 / 2.40.3-desktop.1 |
| Docker resources | 10 CPUs, 8,217,448,448 bytes of memory |
| Python | 3.14.0 |
| Runtime images | Redis 7.4 Alpine, MySQL 8.4, nginx unprivileged 1.28 Alpine, Temurin 21 JRE |
| Source state | Pre-commit working tree based on `716ba27a4a3c09fbff632b62b49b0508a648b91b` |

The [machine-readable record](walking-skeleton-summary.json) retains JUnit totals, the Compose result, and observed image digests. Image tags can move; the digests identify this run. Test tasks used `--rerun`; unchanged compilation tasks could use Gradle's cache. Documentation and cleanup-error handling were finalized after the local run. The branch's GitHub workflow checks the committed revision separately.

## Observations

| Check | Observed result |
|---|---|
| Secret scanning | Scanner self-tests passed; no reported leaks in source or available history |
| Git hooks | All 8 regression cases passed |
| Build | All application JARs and test classes compiled |
| Ordinary tests | 60 executed; no failures, errors, or skipped tests |
| Integration tests | 21 executed; no failures, errors, or skipped tests |
| Images | Four images passed non-root and read-only JAR checks; fake provider started; missing waiting credentials prevented startup |
| Stack readiness | All nine services reported healthy, including both waiting roles and both booking processes |
| Correlation | Waiting and booking/provider returned the supplied UUID through nginx |
| Profile isolation | MVC context tests returned 404 without the stub profile and for the waiting worker role |
| Provider outage | Stopping the provider produced the specified booking `503`; restarting it restored the correlated response |
| Persistence | `down` followed by `up` retained probe data in both Redis instances and MySQL |
| Reset | Reset cleared all three stores, reapplied the initial migration, and preserved an unrelated control volume |
| Cleanup | The isolated project's containers, network, data volumes, and application image tags were removed |

The smoke checked one successful V1 migration and an empty inventory table after each complete journey. Its data probes were created by the test runner; stub requests did not create business records.

Generated diagnostics live under `build/reports/compose-smoke/`: `summary.json` contains checks and outcome, and `compose.log` contains redacted container logs. GitHub Actions uploads that directory alongside JUnit reports. Generated environment files are excluded from those artifacts.

## Interpretation and limits

The applications can run together and expose a failed HTTP dependency through the edge. Both Redis instances and MySQL preserve data across an ordinary shutdown and can be reset within the generated project boundary.

The UUID is a correlation value, not a waiting identity or admission credential. No queue ordering, reservation atomicity, payment outcome, scheduled business work, or throughput guarantee is established. Profile-isolation checks use application contexts; the full Compose run intentionally enables the stub profile.

The next numbered step is [02 · Queue registration](../roadmap.md#branch-sequence). The provider becomes a controllable payment simulator in 08. k6 workload measurements remain in 14 and 21.
