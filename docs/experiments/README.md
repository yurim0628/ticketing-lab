# Experiment backlog

The following investigations are planned. Their hypotheses identify behavior to test; they do not describe implemented guarantees or completed measurements. Use the [report template](TEMPLATE.md) when an investigation has executable steps and evidence.

The [roadmap](../roadmap.md) defines prerequisites and completion criteria; the [testing strategy](../testing.md#planned-validation-strategy) distinguishes regressions from process-failure and load experiments.

| Detailed plan | Milestones | Focus |
|---|---|---|
| [Queue experiments](queue.md) | Q1–Q4 | Registration, lifecycle, admission rate, ownership, status freshness, credentials, and handoff |
| [Booking experiments](booking.md) | B1, P1, P2, D1 | Inventory, expiry, idempotency, payment uncertainty, cancellation, reconciliation, and delivery |
| [Queue-to-booking experiments](system-flow.md) | S1; integrated increments start earlier | Shared identities, complete journeys, process failures, correlated evidence, and combined load/recovery |

All three plans describe future work. Contract decisions called out on these pages must be resolved before their assertions are implemented; detailed scenarios do not make those decisions or test results complete.

| Investigation | Hypothesis to test | Failure scenario | Evidence to collect |
|---|---|---|---|
| Repeated queue entry | An explicitly defined client identity and atomic operation can make repeated entry requests return the same queue position | Many concurrent requests, a lost response, then retries | Per-request identities and outcomes, stored positions, duplicate count, the chosen ordering definition |
| Queue lifecycle and retention | A declared state model and retention policy can preserve registration semantics across transitions | Registration races with opening or closing; an old request arrives during a new sale | Transition history, participant membership, expiry boundaries, rejected cross-sale requests |
| Admission rate | A time-based budget can preserve the selected admission limit during scheduling delay and idle periods | Pause ticks, change the rate, drain the queue, then refill it | Reference calculation, clock inputs, eligibility progress, credentials issued, and booking arrivals |
| Competing admission workers | An update protocol can prevent a worker with stale ownership from moving admission backwards | Pause one worker beyond its lease, let another advance, then resume the first | Ownership changes, proposed and accepted cursor updates, a deterministic regression test |
| Status visibility | A candidate snapshot/cache path can reduce polling cost while keeping authority at admission | Serve stale status, fail publication, or submit a forged client position | Snapshot age/version, authoritative decisions, origin load, freshness/cost comparison |
| Booking handoff | A defined consumption and retry protocol can create one logical booking session per admission | Concurrent credential reuse, response loss, or a crash between consumption and session creation | Credential/session relationship, rejected invalid inputs, recovery state, separate-process observations |
| Inventory contention | Conditional updates and idempotent transitions can preserve capacity under concurrent reservation attempts | Requests exceed capacity; retries overlap reservation expiry | Initial/final state, successful reservations, affected-row counts, transition history, invariant checks |
| Unknown payment outcome | Stable request identity and provider status inquiry can resolve an accepted payment whose response was lost | Provider records success but the caller times out; client retries or the worker restarts | Provider-side operations, local transitions, inquiry results, final agreement or an explicit unresolved state |
| Expiry and compensation | An explicit resolution policy can keep inventory and payment obligations consistent across late outcomes | Expiry races with payment success; cancellation or refund succeeds before its response is lost | Winning transitions, capacity conservation, provider/local amounts, outstanding compensation, retry identity |
| Outbox recovery | Persisting work with the state change can prevent committed events from being silently lost | Stop the relay before send, after send, and before acknowledgement | Stored events, delivery attempts, acknowledgements, duplicate handling, backlog after recovery |
| Datastore interruption | Declared persistence and recovery assumptions can make loss and unavailability detectable | Disconnect a store, perform graceful restart and abrupt termination separately | Acknowledged/restored state comparison, reconnection, explicit degraded behavior, recovery windows |
| Load and recovery | Bounded resource use and explicit overload behavior can preserve correctness as demand exceeds service rate | Increase concurrency, slow a dependency, then restore it | Offered/achieved rate, errors, latency distribution, resource use, correctness checks, recovery duration |

Before implementing an experiment, define what counts as success and what observation would falsify the hypothesis. Specify which failures are injected and which are outside the test model. For example, a single-process concurrency test does not establish behavior across process crashes or datastore failover.

Compare alternatives under the same workload and constraints. Preserve raw logs or data alongside the analysis, state the commit and exact command, and report repeated runs rather than selecting only the best result. Do not infer a production capacity from a local machine benchmark.

The existing checks cover the [runtime foundation](../testing.md). No report is listed here until a real run has evidence that another developer can inspect or reproduce.
