# Roadmap

The lab progresses through executable experiments. Each milestone must connect a design decision to an implementation, a reproducible failure case, and evidence. A topic name or a passing startup test is not completion of a workflow.

**Current checkpoint: F0 only.** The [foundation record](results/foundation.md) contains 41 ordinary tests and 21 integration tests from a local run. They verify startup, configuration, infrastructure, and packaging. All workflow milestones below are planned; there are no queue or booking experiment results yet.

## Milestones and dependencies

| ID | Milestone | Prerequisite | Demonstration required for completion |
|---|---|---|---|
| F0 | Runtime foundation | None | Existing [local verification](results/foundation.md); rerun for the revision being evaluated |
| L1 | Reproducible experiment environment | F0 | A clean checkout can start the required apps and stores, seed synthetic data, capture evidence, and reset only its own resources |
| Q1 | Queue registration and lifecycle | L1 | Concurrent entry, lost responses, and opening/closing races preserve the declared identity and ordering contract |
| Q2 | Admission progress and ownership | Q1 | Controlled time, competing workers, stale ownership, and restart cannot violate the selected admission budget |
| Q3 | Status visibility and polling cost | Q2; instrumentation starts in L1 | Stale or failed publication is visible, display data cannot authorize entry, and polling cost is measured separately |
| Q4 | Admission credentials and booking handoff | Q2; L1 booking host | Invalid credentials are rejected; concurrent consumption and a crash during handoff cannot create duplicate sessions |
| B1 | Inventory holds and expiry | L1; Q4 for the HTTP entry path | Last-unit contention, retries, expiry, and transaction interruption preserve inventory and a legal reservation state |
| P1 | Payment outcomes and reconciliation | B1; controllable fake provider | Success with a lost response, delayed events, and restart converge without duplicate charges, or remain visibly unresolved |
| P2 | Cancellation and compensation | P1 | The chosen cancellation scope handles duplicate requests, late success, and lost refund responses without excess refund or double inventory release |
| D1 | Durable event delivery | P1; exercise P2 events when available | Committed work survives relay crashes; redelivery and exhausted retries follow an explicit policy |
| S1 | Combined load and recovery | Q3, Q4, B1, P1, P2, D1 | The complete synthetic flow retains its invariants during contention, dependency slowdown, interruption, and recovery |

Every row after F0 is **planned**. An independent datastore experiment can begin before its HTTP integration prerequisite, but its milestone stays incomplete until the claimed boundary is tested. Q3 and Q4 can proceed in parallel after Q2. Recovery and measurement start within each milestone; S1 combines evidence rather than introducing those concerns for the first time.

## L1 · Make experiments reproducible

Build a local execution path for the existing application modules, MySQL, the two Redis roles, and the future fake provider. Choose and document the orchestration mechanism; a complete stack is not available today.

Deliver the setup/reset/collection commands with their implementation. Generate local credentials and synthetic fixtures. Give each run isolated resource names and preserve failure artifacts before cleanup. Setup must provision the required databases and dependencies from a clean checkout.

Add test controls when their first experiment needs them: an injectable clock, barriers around meaningful operation boundaries, recorded request histories, and a way to delay or discard responses. Add baseline metrics for request outcomes, dependency time, queue age, and pending work. `fake-ext` needs actual simulation endpoints before payment failures can be exercised.

**Completion:** another developer can execute the documented commands from a clean checkout and inspect the resulting artifacts. An application health response alone is insufficient.

## Q1–Q4 · Treat the queue as a system of its own

The [queue experiment plan](experiments/queue.md) defines registration, lifecycle, time, ownership, status, security, handoff, and failure cases in detail. Implement one behavior with its regression before expanding the workload.

| Work area | Primary modules | Decision and evidence |
|---|---|---|
| Registration | `app-waiting`, `test-support` | Define participant identity and ordering; compare a failing non-atomic baseline with the selected protocol |
| Progress | `app-waiting`, `test-support` | Define a time/budget model; test two workers and resumption after ownership expires |
| Visibility | `app-waiting`, local experiment tooling | Compare direct polling with a candidate snapshot/cache design; observe freshness and authoritative decisions independently |
| Handoff | `app-waiting`, `app-booking` | Define credential scope, replay behavior, and retry recovery; test through separate instances and across a process interruption |

Queue evidence must distinguish eligibility progress, credential issuance, and actual booking arrival rate. It must also distinguish an established client's retry from a first response lost before the client learns its identity.

Q1 is the first business-behavior milestone. Begin with a small deterministic contention test, not a headline throughput target. Measure each path when it becomes executable, including failed runs and generator limitations.

## B1 · Inventory, idempotency, and time

The [booking experiment plan](experiments/booking.md) gives the controlled cases, invariants, and evidence for B1, P1, P2, and D1. The [integrated plan](experiments/system-flow.md) connects them to queue admission.

Define the reservation states and allowed transitions, the source of time, the retry identity and retention window, and the authoritative inventory model before building the endpoint. State whether one request can hold several units. A database constraint on remaining inventory is only one part of this protocol.

Exercise competing requests for the last unit through independent connections and application instances. Reuse the same request identity, then reuse it with a different payload. Race expiry with confirmation and cancellation at the exact time boundary. Interrupt the transaction between inventory and reservation writes; verify rollback and recovery from a lost commit acknowledgement.

Compare a conditional update with a locking alternative under identical assumptions. Keep state-transition tests and real-database regressions with the implementation. Check stored reservations, remaining capacity, and the allowed state history together, during the run where observable and after it settles.

**Completion:** a defined inventory conservation rule, legal transition histories, rejected unauthorized mutations, reproducible races, and an initial contention report. No overselling claim is supported by a single sequential SQL constraint test.

## P1 · Payment uncertainty and recovery

Implement a synthetic provider with independently inspectable operations and controls for delay, duplicate or reordered events, rejection, and success followed by response loss. It must preserve its own record across the caller's restart so it can expose a duplicate operation.

Define stable payment identity, provider/local authority, unresolved states, and recovery ownership. Test caller termination after provider success but before local persistence, an unavailable inquiry endpoint, stale attempts completing late, and expiry racing with payment success. Unknown outcomes must not silently become failed payments or new payment attempts.

Durable recovery work belongs in this milestone. Restart must recover pending investigation without relying on in-memory timers. Record unresolved age, retry count, and the stopping condition for automatic recovery; include the simulated operator path when a case cannot converge automatically.

**Completion:** provider and local histories agree for resolvable outcomes; unresolved cases remain visible; the approved recovery policy prevents a second charge. Each failure boundary has a regression or an explicit untested limit.

## P2 · Cancellation and compensation

Start with full cancellation through the fake provider. Define the cancellation deadline, refund outcome, and inventory-release point as explicit experiment inputs.

Exercise concurrent cancellations, refund success with a lost response, restart during recovery, and payment success after the reservation can no longer be confirmed. Verify that recorded refunds do not exceed the captured amount, capacity is returned at most once, and late events cannot overwrite a newer valid outcome.

**Completion:** a state and amount reconciliation report across local records and the fake provider, including explicitly unresolved compensation. Partial refunds and disputes remain extensions unless a later experiment defines them.

## D1 · Delivery after commit

Implement durable notification intent with the booking state change, then test relay termination before sending and after sending but before recording completion. Add competing relays, stale ownership, delayed delivery, and poison messages that exhaust the retry policy.

Show the difference between preventing duplicate local work and preventing a receiver's duplicate effect. Use a fake receiver to record both delivery attempts and applied effects. A transactional outbox does not by itself establish exactly-once external delivery.

**Completion:** no silently lost committed work within the tested failure model, inspectable duplicate handling, backlog age, bounded retries, and an executable replay/recovery procedure.

## S1 · Integrate and challenge the design

Follow the [queue-to-booking scenarios](experiments/system-flow.md) for boundary contracts, independent process topology, correlated evidence, and execution order.

Run queue → handoff → hold → payment → notification, plus cancellation, as separate processes with real stores and synthetic external dependencies. Include a complete low-load scenario before increasing demand.

Run sustained load, bursts, skewed contention on a small inventory, retry amplification, dependency slowdown, and recovery after demand falls. Bound the resources and observe accepted, rejected, timed-out, and never-generated work separately. Check correctness alongside latency and throughput; report short failure windows rather than hiding them in a whole-run average.

Separate graceful restart, abrupt termination, datastore restore, and replica failover. Each requires its own executable setup and evidence. Replication and restore experiments are extensions until their topology and persistence assumptions exist; restart results cannot stand in for them.

**Completion:** repeatable commands, raw per-run evidence, invariant checks, bottleneck analysis, and a recovery account tied to the tested revision and environment. Shared CI timing is not a capacity benchmark. No fixed throughput or latency target is adopted before a workload and environment are specified.

## The completion rule for every milestone

1. **Contract:** define identities, input/output errors, state transitions, time, data authority, and the boundary of the guarantee.
2. **Design:** explain one consequential choice and an alternative; update architecture boundaries when code makes them real.
3. **Counterexample:** reproduce a failure with controlled concurrency, time, or interruption. Check effects as well as responses.
4. **Recovery:** specify what happens after a retry or restart, what can remain unresolved, and how to observe it.
5. **Evidence:** link source, regression checks, actual reproduction commands, raw artifacts, and a report using the [template](experiments/TEMPLATE.md).
6. **Status:** mark implemented and measured work separately. An unrun test plan, an inconclusive run, or an unavailable environment stays visible.

Keep short deterministic regressions in the proposed CI test strategy described in [testing](testing.md#planned-validation-strategy). Longer process-failure and performance experiments need explicit execution and archived evidence; their runners are not implemented yet.

## Next concrete changes

1. **L1 environment:** add the reproducible topology, isolated reset, and artifact collection with an executed setup report.
2. **Q1 contract and failing example:** define synthetic identity and ordering, introduce a history checker, and demonstrate a registration race in an isolated fixture.
3. **Q1 implementation:** add the selected atomic registration protocol, duplicate/lifecycle regressions, and the first reproducible registration report.

The initial scope covers queue admission, reservations, payment recovery, and delivery using simulated external dependencies. Extend it when a new research question calls for additional behavior and evidence.
