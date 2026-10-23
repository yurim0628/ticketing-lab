# Architecture

The current system is a Java 21, Spring Boot backend with six Gradle modules. Four modules produce executable applications; `core` and `test-support` are libraries. The modules establish separate processes and testable boundaries before business workflows are added.

## Current modules

| Module | Implemented responsibility | Current limit |
|---|---|---|
| [`core`](../core/) | Configuration validation, masked secret values, shared runtime defaults, MySQL datasource construction | Contains no queue, reservation, payment, or notification logic |
| [`app-waiting`](../app-waiting/) | API/worker roles, queue Redis, secret validation, and profile-scoped waiting echo | No queue registration, admission decisions, tokens, or scheduled business jobs |
| [`app-booking`](../app-booking/) | Booking Redis/MySQL, Flyway, and a profile-scoped HTTP call to the provider stub | No reservation or payment workflows |
| [`app-worker`](../app-worker/) | Application startup, booking Redis and MySQL configuration, scheduling enabled | No scheduled business jobs; does not run schema migrations |
| [`test-support`](../test-support/) | Testcontainers setup, generated test credentials, startup and configuration assertions, HTTP test helpers | Used by application tests, excluded from application runtime dependencies |
| [`fake-ext`](../tools/fake-ext/) | Actuator endpoints and a profile-scoped provider echo | No payment operations or programmable provider faults |

The diagram shows production code dependencies, not request traffic. Every executable application depends on `core`; none depends on another application module. Application tests separately depend on `test-support`.

```mermaid
flowchart LR
    Waiting[app-waiting] --> Core[core]
    Booking[app-booking] --> Core
    Worker[app-worker] --> Core
    Fake[fake-ext] --> Core
    Tests[Application tests] -.-> Support[test-support]
```

These boundaries let a future workflow change within its owning application without introducing direct Java dependencies between applications. The root [architecture test](../src/test/java/lab/ticketing/arch/ArchitectureTest.java) checks cross-application dependencies, reverse dependencies from `core`, and production imports of test support. Negative fixtures verify that the checks can detect violations.

## Walking-skeleton runtime

[`docker-compose.yml`](../docker-compose.yml) runs nine containers: waiting API and worker, booking API and worker, fake provider, queue Redis, booking Redis, MySQL, and nginx. Waiting uses one artifact in two mutually exclusive roles. Only nginx publishes a host port, bound to loopback `18080` for the manual stack.

Under the `walking-skeleton` profile, the client requests `/__lab/waiting/{uuid}`, then `/__lab/booking/{uuid}` through nginx. Waiting returns an echo. Booking calls the internal `/__lab/provider/{uuid}` endpoint and returns that response nested under `dependency`. Every response carries the same `requestId` and identifies its `service` and `mode: stub`. Waiting additionally requires the `api` profile, so its worker exposes no stub route.

These calls create no queue, booking, or payment state. The provider stub is an echo, not a payment simulator. Application code still communicates across process boundaries by HTTP rather than importing another app module.

The [runner](../scripts/lab.py) assigns separate project identities to the manual and CI stacks. Its ordinary smoke checks readiness, correlation, migration, and empty inventory; its CI smoke additionally stops/restarts the provider and verifies persistent storage and owned-volume reset. The [01 verification record](results/walking-skeleton.md) documents the completed local run.

## Data and configuration ownership

The waiting application configures a queue Redis connection. Booking and its worker configure a separate booking Redis connection and MySQL access. Compose and integration tests create distinct Redis containers for those roles. Separate host-process runs must supply distinct endpoints if both roles are run together; both Redis ports default to `6379`.

Booking owns the [Flyway migrations](../app-booking/src/main/resources/db/migration/). The initial table stores a ticket session with capacity and remaining inventory. SQL constraints enforce nonnegative capacity and `0 <= remaining <= capacity`. This protects individual stored values; it does not establish a complete reservation workflow or prove correctness under concurrent requests. The worker does not apply migrations, so booking must initialize a shared database first.

Shared defaults are loaded by `core` with lower precedence than application configuration and explicit environment or command-line settings. They enable virtual threads, graceful shutdown, bounded client/database timeouts, and Actuator health and metrics. Pool sizes and timeout values are starting configuration choices, not measured capacity limits.

## Planned behavior

The [roadmap](roadmap.md) proposes queue admission, conditional inventory updates, uncertain payment outcomes, and an outbox relay. Those mechanisms have no runtime implementation yet. nginx and Compose currently serve the walking skeleton; there is no load generator yet.

Each implementation stage should add a focused reproducer and then document the observed result. A future dependency or protocol belongs in this page when its code exists; alternatives and untested assumptions belong in [experiments](experiments/README.md).
