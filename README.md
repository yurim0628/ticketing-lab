# Ticketing Lab

**An experimental Java backend for studying concurrency, failure, and recovery in ticketing systems.**

Limited inventory, impatient clients, and unreliable dependencies make a useful systems laboratory. This project develops those problems as small, reproducible experiments: state an invariant, create a failure scenario, compare designs, and keep the evidence.

**Current step: 01 walking skeleton, verified locally.** The Compose runner and profile-scoped stub path connect the applications. All seven local verification stages passed; queue admission, reservation workflows, payments, and load experiments remain on the [numbered roadmap](docs/roadmap.md).

[Start locally](docs/running.md) · [Understand the architecture](docs/architecture.md) · [Inspect the tests](docs/testing.md) · [Explore the experiments](docs/experiments/README.md)

## What the lab investigates

| Question | Evidence an experiment must produce |
|---|---|
| Can concurrent clients enter a queue without duplicate positions? | A reproducible contention test and the ordering it actually guarantees |
| Can a stalled worker move admission backwards? | A controlled lease-expiry scenario and an explicit ownership rule |
| Can the last available ticket be sold twice? | Competing reservations, database outcomes, and an inventory invariant |
| What if a payment succeeds after the caller times out? | A provider simulation, recorded state transitions, and a recovery path |
| What survives a crash between a database commit and a notification? | A replayable delivery test with documented duplicate handling |
| Where does throughput stop improving? | Workload, environment, latency distributions, resource use, and raw observations |

These are research questions, not claims that all six guarantees have already been implemented. [Experiment plans](docs/experiments/README.md) define the next pieces of evidence to collect.

## Implemented scope

The foundation and walking skeleton provide:

- A Compose topology with five application processes, two Redis instances, MySQL, and nginx.
- Profile-scoped UUID echoes across waiting and booking/provider, without creating business records.
- Missing or invalid credentials stop startup; secret wrappers redact their values.
- Waiting API and worker roles cannot be activated together.
- Booking owns a Flyway migration with capacity and remaining-inventory constraints.
- Integration tests run against disposable MySQL and separate queue/booking Redis instances.
- Architecture checks reject cross-application dependencies and test-support leakage. Deliberately invalid fixtures verify that those checks can fail.
- Container checks verify a non-root process, a read-only application JAR, startup, and missing-secret failure behavior.

The [testing guide](docs/testing.md) links each claim to its implementation and explains its limits. The first [foundation verification record](docs/results/foundation.md) reports **41 ordinary tests and 21 integration tests**, all passing in a local run. There are no published business-workflow benchmarks yet.

## Run the walking skeleton

Use **JDK 21, Docker with Compose v2, and Python 3**. From the repository root:

```bash
scripts/lab.sh up
scripts/lab.sh status
scripts/lab.sh smoke
```

The runner builds and starts nine containers, with nginx at `http://127.0.0.1:18080`. It generates local credentials in ignored `.lab/` files. The smoke checks readiness, a waiting echo followed by a booking-to-provider call with the same UUID, the initial migration, and an empty inventory table.

```bash
scripts/lab.sh down
```

`down` retains data volumes. `reset` deletes this project's data, immediately restarts the stack, and reruns the smoke. See [running the project](docs/running.md) for the command contract, configuration, and the standalone application option.

The isolated check also exercises provider outage/recovery, data persistence, reset, and cleanup:

```bash
scripts/lab.sh smoke-ci
```

For ordinary tests or the full local/GitHub verification pipeline:

```bash
./gradlew test
scripts/ci.sh
```

The full pipeline also requires Git and OpenSSL. `./gradlew build` includes Docker-backed integration tests. The [testing guide](docs/testing.md) distinguishes the image check, Compose smoke, and planned business-flow experiments. The [walking-skeleton record](docs/results/walking-skeleton.md) reports 60 ordinary tests, 21 integration tests, and the isolated Compose checks.

## Repository map

| Path | Current responsibility |
|---|---|
| [`app-waiting/`](app-waiting/) | API/worker roles, queue Redis configuration, and the profile-scoped waiting stub |
| [`app-booking/`](app-booking/) | Database infrastructure, migrations, and the booking-to-provider stub call |
| [`app-worker/`](app-worker/) | Background-process host connected to booking infrastructure |
| [`core/`](core/) | Validated configuration, secret handling, datasource setup, runtime defaults |
| [`test-support/`](test-support/) | Disposable infrastructure, generated test credentials, shared assertions |
| [`tools/fake-ext/`](tools/fake-ext/) | Profile-scoped provider echo; payment simulation remains planned |
| [`src/test/`](src/test/) | Repository-wide architecture checks and violation fixtures |
| [`scripts/`](scripts/) | Local stack runner, verification pipeline, and optional Git hooks |
| [`infra/`](infra/) | nginx routing and container health checks |
| [`docs/`](docs/README.md) | Architecture, reproducible commands, experiment plans, and evidence |

**Stack:** Java 21 · Spring Boot 3.5.16 · Gradle 8.14.3 (Groovy DSL) · MySQL 8.4 · Redis 7.4 · JUnit 5 · Testcontainers · ArchUnit.

## Read it as an engineering notebook

1. **Start with the boundary.** [Architecture](docs/architecture.md) describes the current modules and distinguishes them from proposed mechanisms.
2. **Follow a question.** [Experiments](docs/experiments/README.md) name the invariant, failure scenario, and evidence needed to choose a design.
3. **Reproduce the check.** [Testing](docs/testing.md) explains what runs, what infrastructure it uses, and what a green result does not prove.
4. **Challenge the conclusion.** Every completed experiment should record assumptions, limitations, and observations that would invalidate its interpretation.

No throughput or latency number is meaningful without its workload and environment. Measurements from a local development machine are not production capacity estimates.

## Contributing

Include a focused reproduction or test with behavior changes. Read [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow and the [experiment report template](docs/experiments/TEMPLATE.md) for new investigations.
