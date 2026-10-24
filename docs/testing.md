# Testing and evidence

The suite covers the foundation and profile-scoped walking-skeleton contracts. A Compose smoke runner adds process wiring, dependency availability, and storage lifecycle checks. It does not exercise a user booking flow or establish throughput, queue fairness, payment correctness, or availability guarantees.

The initial [foundation verification record](results/foundation.md) includes the environment, observed outcomes, and test totals from a real local run. It is a historical record; run the checks for the source revision you are evaluating.

The recorded 41 ordinary tests and 21 integration tests belong to the historical foundation run. The [walking-skeleton record](results/walking-skeleton.md) reports 60 ordinary and 21 integration tests, all passing with no errors or skips, plus all seven local verification stages. Business-flow and recovery scenarios below remain planned; the stub journey is not their implementation.

## Test boundaries

| Command | What runs | External requirements |
|---|---|---|
| `./gradlew test` | Tests excluding classes named `*IT`: configuration, startup validation, shared defaults, architecture, and stub contracts | JDK 21; dependency downloads on first use |
| `./gradlew integrationTest` | `*IT` classes: application contexts against Redis/MySQL, Flyway constraints, statement timeout behavior, health and metrics | Docker; Testcontainers downloads images as needed |
| `./gradlew check` | Both test groups | Docker |
| `./gradlew build` | Assembly and `check` | Docker |

Test tasks remove the application environment keys listed in [`.env.example`](../.env.example) from their child JVMs. Tests supply their own generated secrets and connection properties, so a developer's exported credentials cannot silently satisfy a missing-secret test.

## Existing evidence in code

| Behavior checked | Test source | What it does not establish |
|---|---|---|
| Missing, blank, unresolved, and undersized configuration values are rejected; secret rendering is masked | [`core` tests](../core/src/test/java/lab/ticketing/core/) and application startup tests | Complete protection against every possible secret disclosure |
| Shared defaults load and can be overridden | [`core` configuration tests](../core/src/test/java/lab/ticketing/core/config/) | Optimal pool sizes or timeout values under load |
| Waiting requires exactly one runtime role; secrets are required for its API role | [WaitingStartupTest](../app-waiting/src/test/java/lab/ticketing/waiting/WaitingStartupTest.java) | Queue entry or admission correctness |
| Database-backed startup, Redis connectivity, virtual-thread configuration, server settings, health, and metrics | [Waiting](../app-waiting/src/test/java/lab/ticketing/waiting/), [booking](../app-booking/src/test/java/lab/ticketing/booking/), and [worker](../app-worker/src/test/java/lab/ticketing/worker/) integration tests | Behavior during failover or sustained contention |
| The initial migration is applied and invalid remaining inventory is rejected by MySQL | [FlywayMigrationIT](../app-booking/src/test/java/lab/ticketing/booking/FlywayMigrationIT.java) | A reservation protocol or concurrent overselling prevention |
| Slow reads and writes are interrupted through the configured database/JDBC mechanisms | [StatementTimeoutIT](../app-booking/src/test/java/lab/ticketing/booking/StatementTimeoutIT.java) | A bound on total request latency, external-call duration, or every transaction |
| No cross-application Java dependencies, reverse dependencies from `core`, or production imports of test support | [ArchitectureTest](../src/test/java/lab/ticketing/arch/ArchitectureTest.java) | Network isolation or every possible architecture rule |
| No synchronized methods or monitor blocks in the inspected project classes | [ArchitectureTest](../src/test/java/lab/ticketing/arch/ArchitectureTest.java) and [Monitors](../src/test/java/lab/ticketing/arch/Monitors.java) | Absence of virtual-thread pinning in third-party libraries or all runtime paths |

Architecture checks include deliberately invalid fixtures, so a rule that stops detecting those violations fails its own test. The test-only thread probe is imported by test contexts and is not an application API.

Gradle writes HTML reports under each participating project's `build/reports/tests/test/` and `build/reports/tests/integrationTest/`; XML results are under `build/test-results/`. Root architecture reports are under the root `build/` directory. Generated reports are local artifacts, not committed proof of a particular run.

## Verification script

[`scripts/ci.sh`](../scripts/ci.sh) is the shared local and GitHub Actions entry point. With no arguments, it runs these stages in order and stops after the first failing stage:

| Stage | Scope |
|---|---|
| `secret-scan` | Scanner self-tests, forbidden key-file checks, Gitleaks over Git history and current files |
| `hooks` | Pre-commit hook self-tests in a temporary repository |
| `build` | `assemble testClasses`; compiles artifacts and test classes without executing tests |
| `unit` | `test --rerun` |
| `it` | `integrationTest --rerun` |
| `image` | Builds application JARs and runs the image smoke script |
| `compose` | Runs `scripts/lab.sh smoke-ci` against an isolated stack |

Run a subset by naming stages, for example `scripts/ci.sh build unit`. The default pipeline requires no private policy list. The optional `scripts/ci.sh name-scan` stage requires a caller-supplied nonempty `FORBIDDEN_WORDS` value or ignored `.forbidden-words` file. It is not a prerequisite for a public checkout's default checks.

Install local Git hooks with `scripts/install-hooks.sh`. Installation is explicit; cloning alone does not activate hooks. A local hook can skip Docker-dependent scanning when Docker is unavailable, so it does not replace the full verification script.

## Image smoke scope

[`image-smoke.sh`](../scripts/image-smoke.sh) builds the four application images and checks their configured runtime user, JAR permissions, and JVM options. It starts `fake-ext` with a read-only root filesystem and memory limit, checks health, process identity, and reported heap sizing, then checks that waiting's API role fails without its required secrets.

This verifies packaging and selected startup behavior. It does not start a complete multi-service environment or exercise successful waiting, booking, and worker workflows together. Startup timing printed by the script is diagnostic output, not a repeatable performance benchmark.

## Compose smoke scope

`smoke` checks an already running local stack; `smoke-ci` creates its own stack. Both require Python 3 and Docker Compose in addition to the build prerequisites. They are separate from the image packaging check above.

| Check | `scripts/lab.sh smoke` | `scripts/lab.sh smoke-ci` |
|---|---|---|
| Nine healthy services, correlated waiting/booking/provider responses | Yes | Yes |
| Initial migration applied once; inventory table remains empty | Yes | Yes |
| Provider stopped: booking returns `503`; restored provider succeeds | No | Yes |
| Shutdown/restart preserves both Redis stores and MySQL data | No | Yes |
| Reset clears owned data, reapplies migration, and preserves an unrelated control volume | No | Yes |
| Separate project/allocated port, diagnostic capture, and cleanup | Uses the existing stack | Yes |

The CI smoke writes `build/reports/compose-smoke/summary.json` and `compose.log`, including failure diagnostics. Its source revision and working-tree status identify the tested state. The [01 verification record](results/walking-skeleton.md) contains the observed local outcomes and cleanup result.

The stub controller tests cover profile exclusion, UUID validation, correlation, and bounded provider failures. The Compose check exercises the real nginx/application/store topology. Neither establishes queue registration, payment recovery, throughput, or datastore failover. Reset is a destructive clean-state check, not a backup restoration test.

## Planned validation strategy

This section proposes future coverage; it adds no tests, CI jobs, or executable commands. Follow the [roadmap](roadmap.md), the [queue plan](experiments/queue.md), the [booking plan](experiments/booking.md), and the [integrated scenarios](experiments/system-flow.md), then link each implemented check and recorded result when it exists.

Before implementing a stage, define its public contract, state transitions, authoritative data, request identity, time source, and invariants. Include rejected transitions, deadline boundaries, retry semantics, and explicitly excluded failures. Expected outcomes must follow that contract rather than whichever result the implementation happens to produce.

### Validation layers

| Layer | Planned checks | Limit of the evidence |
|---|---|---|
| State model | Allowed and rejected transitions, deadline edges, repeated operations, and invariant checks using controlled inputs | Does not establish persistence, isolation, or network behavior |
| Contract | Request/response and error semantics, identity scope, retries after a lost response, and admission authorization at the receiving service | A mocked peer does not prove the real integration works |
| Real store, single process | Competing transactions and conditional operations against the actual Redis/MySQL versions and constraints | Multiple threads do not establish correctness across process interruption or independent workers |
| Multiple processes and crashes | Independent workers, stale ownership, interruption before/after commit or external acceptance, restart and replay | Process restart is distinct from datastore failover or loss of durable state |
| Integrated functional flow | Public interfaces from admission through reservation and payment resolution, using a controllable fake external service | A fake service establishes only the documented simulated provider contract |
| Load and recovery | Sustained arrivals, bursts, dependency slowdown, overload, restoration, and correctness during and after recovery | A local result does not establish production capacity or availability |

Each layer should retain the smallest regression check that reproduces a discovered failure. Add a broader test only when the missing behavior crosses a boundary that the smaller test cannot exercise.

### Controlled asynchronous tests

Use injected clocks for application-owned time and explicit barriers or latches to arrange competing operations. Do not use `Thread.sleep` to establish ordering. A bounded wait should observe a named condition, fail at a defined deadline, and include the last observed state in its failure output.

Advancing an application clock does not advance Redis expiry, database time, or a fake provider's clock. State which component owns each deadline and control or observe it accordingly. Collect every worker result and exception; a test must not pass while background work has failed or is still running.

For crash tests, identify the exact boundary being interrupted: before persistence, after commit but before response, before external acceptance, or after acceptance but before local acknowledgement. A delayed thread is not evidence of process-crash recovery.

### Failure and replay matrix

These are proposed cases. Define the expected transition and compensation policy before writing assertions; no particular payment or cancellation policy is established here.

| Stage or boundary | Cases to arrange | Required observations |
|---|---|---|
| Queue and admission handoff | Repeated entry, stale worker, lost grant response, concurrent grant consumption, expired or mismatched authorization | Defined ordering, accepted/rejected grants, ownership changes, and whether a retry repeats an earlier result |
| Reservation and expiry | Capacity contention, repeated reservation/release, expiry racing with confirmation, restart during cleanup | Capacity conservation, the winning transition, and absence of repeated release or stranded state |
| Payment and expiry | External acceptance before/after expiry, lost acceptance response, delayed or duplicate notification, conflicting inquiry results | External operations, local transition order, unresolved outcomes, and the specified resolution or compensation |
| Cancellation and compensation | Cancellation racing with confirmation, compensation timeout, lost acknowledgement, replay after restart | Chosen terminal outcome, stable retry identity, compensation attempts, and any remaining obligation |
| Notification delivery | Interruption before send, after send, and before acknowledgement; duplicate replay | Durable pending work, delivery attempts, consumer handling, backlog, and recovery progress |

Repeat relevant cases with duplicate and out-of-order messages. Check that a retry does not create another logical operation, and that unresolved work remains observable until the contract permits resolution or explicit intervention. Do not equate local rollback with reversal of an accepted external operation.

### Evidence required at each stage

For every stage, record the invariant, injected failure boundary, retry owner and limits, recovery procedure, and observation that would falsify the claim. Introduce the necessary logs and metrics with the behavior instead of waiting for the final load stage.

Keep request identities, transition histories, relevant state snapshots, worker outcomes, and external-service observations correlated without recording credentials or personal data. Prefer bounded metric labels; request identities belong in traces or diagnostic records rather than metric dimensions.

Preserve the commit, configuration, seed, exact commands, per-run outcomes, and raw artifacts through the [report template](experiments/TEMPLATE.md). Include unsuccessful and inconclusive runs. Distinguish source code, a passing regression check, and a measured experimental result; none substitutes for the other two.

### Current CI and proposed experiment execution

| Execution context | Current state or proposed scope |
|---|---|
| Current default CI | Seven stages: scanners, hooks, build, ordinary tests, integration tests, images, and Compose stub/lifecycle smoke; no business-flow or load checks |
| Proposed PR checks | Short state/contract regressions and bounded real-store concurrency checks, added with each implementation |
| Proposed explicit or scheduled runs | Multi-process fault injection and integrated recovery scenarios, with isolated state, bounded duration, and retained diagnostics |
| Proposed controlled load runs | Repeated baseline/comparison runs and sustained-load evidence on a recorded environment; not absolute latency gates on a shared CI runner |

A load report should separate offered and achieved rate, rejected or dropped work, errors and timeouts, queue wait, and service latency. Capture resource and backlog time series through overload and recovery, and check invariants during the run as well as at its end. Select workload, duration, repetitions, and acceptance criteria before measuring.

## Adding an experiment

Start from a stated invariant and a failure that could violate it. Make the failure reproducible with controlled inputs, then keep the regression check with the implementation. Record performance or fault-injection investigations using the [report template](experiments/TEMPLATE.md), including unsuccessful runs and the limits of the evidence.
