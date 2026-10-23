# 01 · Walking skeleton

**Branch:** `feature/01-walking-skeleton`

**Status:** implemented and verified locally. All seven verification stages passed; see the [execution record](../../results/walking-skeleton.md). GitHub verification for this step has not been recorded.

## Goal and rationale

Make the application boundaries executable before adding queue or payment behavior. One local environment must start the application processes, provision their dependencies, route a correlated request through nginx, and expose a dependency failure and recovery.

The [00 foundation](../../results/foundation.md) established individual startup and infrastructure checks. This step connects the processes using their real runtime configuration. A UUID is a synthetic correlation value for the smoke; it is not a queue identity, admission credential, reservation, or payment identifier.

This is step 01 in the [numbered roadmap](../../roadmap.md). Queue registration starts in 02. The provider echo used here will later be extended into a controllable payment simulator in 08. k6 queue and combined load experiments belong to 14 and 21.

## Runtime topology

The root `docker-compose.yml` defines nine containers. Four application artifacts produce five application processes because the waiting artifact runs in two roles.

| Container role | Artifact or dependency | Responsibility in this step |
|---|---|---|
| Waiting API | `app-waiting` | Waiting echo; profiles `api,walking-skeleton` |
| Waiting worker | `app-waiting` | Separate worker-role startup; profiles `worker,walking-skeleton`; no stub API |
| Booking API | `app-booking` | Booking stub and outbound provider request; profile `walking-skeleton` |
| Booking worker | `app-worker` | Background-process startup against booking infrastructure |
| Fake provider | `fake-ext` | Provider echo; profile `walking-skeleton` |
| Queue Redis | Redis | Waiting-side infrastructure |
| Booking Redis | Redis | Booking-side infrastructure |
| MySQL | MySQL | Booking schema and application database connections |
| Edge | nginx | Client-facing routing to the waiting and booking APIs |

The ordinary local entry point binds nginx to loopback port `18080`. The CI smoke uses an isolated Compose project and an allocated host port. The two runs must not share volumes or reset each other's data.

The manual runner generates local environment values under ignored `.lab/` storage with file mode `0600`; CI uses a separate temporary state directory. It supplies the required credentials without committing them. Booking owns migration startup; the worker connects without running migrations. Existing [architecture boundaries](../../architecture.md) still apply: an HTTP call between applications does not permit a Java module dependency between them.

## Stub request contract

All stub routes require the `walking-skeleton` profile. Waiting additionally requires the `api` role; its worker process must not expose the waiting stub.

| Caller | Request | Required response |
|---|---|---|
| Smoke client through nginx | `GET /__lab/waiting/{uuid}` | Waiting service marker and the supplied UUID |
| Smoke client through nginx | `GET /__lab/booking/{uuid}` | Booking service marker, the same UUID, and a nested provider response |
| Booking API over the Compose network | `GET /__lab/provider/{uuid}` | Provider service marker and the same UUID |

The client makes the waiting request first and then the booking request with the same UUID. Waiting does not call booking. Booking makes the provider call, so its nested response demonstrates a real process-to-process dependency rather than a locally fabricated echo.

The isolated `smoke-ci` stops the fake provider, checks that the booking path reports `503`, restarts the provider, and checks that the path succeeds again. Ordinary `smoke` checks only healthy services, the correlated happy path, the initial migration, and empty inventory. This is a narrow dependency availability check. It does not demonstrate payment reconciliation, retry safety, failover, or durability.

Outside the stub profile, the lab routes must be absent. The smoke must not create queue entries, change inventory, issue credentials, or simulate a successful payment.

## Runner interface

`scripts/lab.sh` is the shell entry point backed by Python 3. It requires JDK 21, Docker with Compose v2, and Python 3. The completed local execution is recorded in the [verification report](../../results/walking-skeleton.md).

| Command | Contract |
|---|---|
| `scripts/lab.sh up` | Prepare local configuration, build the required artifacts/images, and start the stack with readiness checks |
| `scripts/lab.sh status` | Report this Compose project's containers and readiness |
| `scripts/lab.sh smoke` | Check readiness, correlated stub responses, one successful initial migration, and empty inventory against the running stack |
| `scripts/lab.sh down` | Stop and remove this project's containers and network while retaining its volumes |
| `scripts/lab.sh reset` | Remove owned data volumes, immediately restart using existing images, and run the ordinary smoke |
| `scripts/lab.sh smoke-ci` | Create an isolated project and allocated port; test the happy path, provider outage/recovery, persistence, reset, and unrelated-volume survival; collect diagnostics and clean up |

The manual sequence is `up`, `status`, `smoke`, then `down`. `reset` returns a running clean stack; it does not leave the stack stopped. `smoke-ci` checks that down/up preserves data, then reset clears both Redis instances and MySQL while preserving an unrelated control volume. It writes diagnostics to `build/reports/compose-smoke/` before cleaning up its own resources and control volume. The default CI pipeline runs it as the seventh, `compose`, stage.

## Completion evidence

| Check | Evidence required | Current status |
|---|---|---|
| Clean startup | All nine containers start from the documented setup; all five application health checks are ready | Passed locally |
| Waiting role separation | API and worker processes start independently; the worker exposes no waiting stub | Passed locally |
| Correlated route | The same UUID appears in waiting, booking, and the nested provider response through nginx | Passed locally |
| Profile boundary | Stub routes are unavailable without the explicit profile | Passed locally |
| Dependency failure/recovery | Booking reports `503` while the provider is stopped and succeeds after recovery | Passed locally |
| Isolated lifecycle | Down/up preserves all three stores; reset clears owned data and restarts; an unrelated volume survives; CI resources are cleaned up | Passed locally |
| Existing checks | All seven local stages; 60 ordinary and 21 integration tests, with no failures, errors, or skips | Passed locally |

The [verification record](../../results/walking-skeleton.md) records the tested state, commands, environment, outcomes, and artifacts. The seven stages were executed across three invocations. See the [testing guide](../../testing.md) for each check's scope.

Local verification is complete. Integration into `main` still requires explicit user approval under the [branch workflow](../../roadmap.md#branch-workflow).

## Limits

The local result demonstrates reproducible startup, role configuration, edge routing, correlation across one HTTP dependency, and the recorded smoke scenarios. It establishes no queue ordering, admission limit, reservation atomicity, payment outcome, event delivery guarantee, or throughput target. The detailed [queue](../../experiments/queue.md), [booking](../../experiments/booking.md), and [integrated-flow](../../experiments/system-flow.md) checks remain attached to the later numbered steps.
