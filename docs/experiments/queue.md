# Queue experiments

**Status: planned.** The current waiting application has startup roles, configuration validation, and Redis connectivity tests. It has no queue entry, admission, status publication, or credential exchange implementation. None of the experiments below has run in this repository.

The queue needs its own correctness and recovery program. A successful Redis connection does not show that clients keep their position, competing workers respect admission limits, or booking accepts an admission only once.

## Define the contract before choosing the mechanism

Model the clients and sale explicitly. Resolve the following contract decisions before implementing the assertions:

| Decision | Questions the experiment must settle |
|---|---|
| Identity and retries | What identifies one queue participant? Does the client know that identity before the first request? What happens if the first response carrying a new identity is lost? |
| Ordering | Is order assigned when the authoritative store accepts registration? How are concurrent requests and ties resolved? Are gaps allowed? Client send times and HTTP response order are not the same ordering definition. |
| Lifecycle | What are the allowed opening, closing, pausing, and reopening transitions? Which transitions race with registration or admission? |
| Time and admission budget | Which clock advances the budget? How are delayed ticks, rate changes, and unused capacity handled? Is the limit on eligibility, credential issuance, or booking arrivals? |
| Ownership | Who may update admission progress? How does a resumed worker prove it still owns that right at the moment of mutation? |
| Retention and recovery | When can identity, progress, and consumed-credential records expire? How do their lifetimes cover credential validity, clock tolerance, and the promised retry window? Does a new sale have a distinct identity? Which data loss is tolerated? |
| Display and authority | What information can a client cache? Which server-side state decides whether admission is allowed? |
| Handoff | What is a credential bound to, where is it consumed, and what result does a legitimate retry receive after its response is lost? Is session uniqueness scoped to a participant and sale, including reissued credentials? |

For the initial experiment, admitting a participant grants access to a booking attempt. It does not reserve inventory. Cookie or request identity is a test identity; it does not establish one real person per entry or prevent someone from creating several identities.

## Q1 · Registration and lifecycle

**Question:** Can registration preserve the chosen identity and ordering contract when requests overlap and responses disappear?

| Case | Controlled scenario | Required observation |
|---|---|---|
| ENTRY-01 | Submit concurrent registrations with the same established identity through two application instances | One logical registration; every successful retry resolves to its original position |
| ENTRY-02 | Register distinct identities concurrently | Unique positions and a recorded history consistent with the chosen ordering rule; no missing acknowledged participant |
| ENTRY-03 | Commit registration, discard the response, then retry | The original result is recoverable when the request retains its identity; separately demonstrate the limitation when it does not |
| ENTRY-04 | Release registration and a lifecycle transition from a controlled barrier; repeat transitions | Every operation has a permitted outcome and no partial transition remains; opening twice cannot reset established progress |
| ENTRY-05 | Expire records or start a new sale while old requests, worker updates, credentials, and snapshots remain in flight | Old state cannot mutate or authorize entry into the new sale; retention does not create an unexplained second position within the promised retry window |

Compare a simple read-then-write baseline with a candidate atomic operation using the same history checker. Keep an unsafe baseline isolated in experiment fixtures. A failing baseline should demonstrate the race that the candidate intends to remove.

**Exit evidence:** request identities, invocation/completion history, authoritative membership and position snapshots, a controlled race regression, and a short ordering decision. Do not infer fairness from the order in which responses reached clients.

## Q2 · Admission progress and worker ownership

**Question:** Can admission advance at a defined rate without an expired owner corrupting progress or a pause releasing an unbudgeted burst?

| Case | Controlled scenario | Required observation |
|---|---|---|
| CONTROL-01 | Advance a controlled clock through regular ticks, delayed ticks, a pause, and a rate change | Progress obeys the declared time and budget model; rounding and delay do not create unexplained capacity |
| CONTROL-02 | Empty the queue, continue ticking, then register a new burst | The selected idle-budget policy is enforced; progress does not pass the last assigned position in the current sale under the chosen ordering model |
| CONTROL-03 | Start two workers; pause the first after it reads state, allow its ownership to expire, advance with the second, then resume the first | Stale writes are rejected at mutation time; progress never goes backwards and ownership changes do not double the budget; the old owner cannot renew or release its successor's ownership |
| CONTROL-04 | Stop a worker between related state updates, restart it, and retry its operation | Restart finds a coherent checkpoint or performs an explicit repair; repeating the operation does not advance twice |
| CONTROL-05 | Race an operational rate, pause, or close command with a worker update | The documented command ordering holds and a stale computation cannot reopen or reset the queue |

Use a reference calculation to check progress across generated tick schedules. Separate deterministic clock tests from process-pause experiments against real Redis. A test that runs only one worker cannot establish ownership safety.

Measure eligibility progress, issued credentials, and accepted booking handoffs separately. Client polling and network delays can make those rates differ even when the worker's arithmetic is correct.

**Exit evidence:** clock inputs, owner/version changes, accepted and rejected mutations, restart checkpoints, and tests at the state-read/state-write boundary. Candidate designs may use atomic conditional updates or versioned ownership; selecting a lease duration alone does not resolve the stale-writer experiment.

## Q3 · Status visibility and admission decisions

**Question:** Can status reads remain useful under heavy polling without becoming an authority for admission?

| Case | Controlled scenario | Required observation |
|---|---|---|
| STATUS-01 | Serve an old snapshot or supply a fabricated client position | Admission consults authoritative state; display data cannot grant access |
| STATUS-02 | Fail or interrupt snapshot publication; deliver updates out of order | Consumers see a complete version or a declared unavailable state; version and age are observable |
| STATUS-03 | Stop publication while admission control remains alive | Stale status is detectable; the declared degraded behavior and recovery path are testable |
| STATUS-04 | Drive polling, registration, and admission independently, then together | Request attribution shows which component does the work; polling does not silently consume the protected booking budget |

A cacheable shared snapshot is a candidate design, not a completed component. Compare it with a direct status endpoint under the same workload. If an intermediary cache is part of the experiment, include it in the executable topology and record cache hits, origin requests, and freshness. A local proxy experiment does not establish a hosted CDN's behavior.

**Exit evidence:** snapshot versions and timestamps, authoritative admission decisions, publication failures, origin request counts, and a report showing the cost and freshness tradeoff.

## Q4 · Admission credentials and booking handoff

**Question:** Can an eligible participant obtain one logical booking session despite duplicate requests, while invalid credentials are rejected?

| Case | Controlled scenario | Required observation |
|---|---|---|
| HANDOFF-01 | Present absent, altered, expired, wrong-purpose, wrong-sale, or wrong-participant credentials | Reject the request without creating a booking session or consuming another participant's right |
| HANDOFF-02 | Consume the same credential concurrently through two booking instances | At most one logical session is created; losing requests receive the specified retry or rejection outcome |
| HANDOFF-03 | Consume a credential, create a session, discard the response, and retry | An authorized retry can recover the established outcome without creating another session |
| HANDOFF-04 | Stop the process between consumption and session persistence, then restart | The selected protocol prevents duplicate access and either recovers the legitimate handoff or exposes a defined recovery state |
| HANDOFF-05 | Exercise expiry boundaries, attempt reuse after consumption records expire, and test any supported credential reissue or key rotation | Replay records cover the full credential validity and promised retry window, including clock tolerance; premature cleanup is detected and cannot permit a second admission |
| HANDOFF-06 | Request issuance or reissuance concurrently for one participant and sale, then consume the resulting distinct credentials | Logical session uniqueness holds across credentials; deduplicating only the same credential cannot satisfy this case |

Choose an opaque or signed credential explicitly. Add format-specific negative tests only after that choice. If consumption and session creation use separate stores, identify the crash gap and recovery protocol; two independently atomic operations do not demonstrate one atomic handoff.

**Exit evidence:** negative contract tests, concurrent consumption results, process restart evidence, and a trace from participant to credential to session without recording secret values.

## Queue recovery and load matrix

Run short deterministic regressions alongside each milestone. Add these longer experiments as soon as the relevant path exists; do not postpone all failure testing until the queue is otherwise considered complete.

| Experiment | Failure or workload | What must be checked |
|---|---|---|
| Application replacement | Terminate one API process under retries; restart it | Acknowledged registrations and successful handoffs remain consistent; recovery errors are reported in short time windows |
| Dependency interruption | Delay or disconnect Redis, then restore it | Calls terminate within declared budgets; failure does not grant admission; retries and pending work stay bounded |
| Memory pressure | Reach a configured datastore memory limit | Refusal or eviction behavior matches the chosen policy; identity/progress loss is visible and does not silently permit duplicates |
| Persistence recovery | Test graceful restart and abrupt termination separately | Compare acknowledged writes with restored state; state the persistence settings and actual loss window |
| Replica failover extension | Change the active datastore in an explicitly replicated topology | Reconnection, lost acknowledgements, and recovery are measured; a standalone restart is not reported as failover |
| Registration burst | Increase offered registrations, including a controlled duplicate ratio | Stored unique participants match accepted identities; report admission errors, retries, latency, and generator drops |
| Sustained admission | Maintain backlog, drain it, then refill it | Progress budget and identity invariants hold throughout; capture eligibility, issuance, and handoff rates separately |
| Polling and retry amplification | Compare jittered and synchronized clients; slow a dependency during load | Observe origin load, resource saturation, rejected work, retry growth, and time to recover |

Define correctness assertions and workload validity conditions before each run. Record offered and achieved rates, generator CPU, dropped work, latency distributions, resource limits, queue age, and short recovery windows. Expected waiting responses must be classified separately from dependency errors. A run that cannot generate its configured load cannot substantiate that load level.

Every report needs commands, a seed, fault timing, environment and persistence settings, per-run observations, raw artifacts, and the observations that would reject its hypothesis. Use the [report template](TEMPLATE.md). Define throughput, recovery, and data-loss criteria for the workload and environment under test.

## Completion and limits

The receiving service's inventory, payment, cancellation, and delivery cases are in the [booking plan](booking.md). The [integrated plan](system-flow.md) checks that queue handoff and booking agree through complete journeys and process failures.

Queue completion requires links from Q1–Q4 in the [roadmap](../roadmap.md) to implementation, executable regressions, and recorded evidence. Each recovery claim must identify the exact failure model tested. Planned cases and unavailable test environments remain explicit gaps.

Bot resistance, proof of real-person identity, geo-distributed ordering, and managed-service availability are separate research scopes. If they are later added, define new contracts and experiments rather than inferring them from these tests.
