# Queue-to-booking experiments

**Status: planned.** The repository currently verifies its runtime foundation. It has no complete queue-to-booking workflow, provider simulation, orchestration stack, or runner for the experiments on this page. These scenarios are completion criteria for future implementation, not recorded results.

The [queue plan](queue.md) checks admission and handoff. The [booking plan](booking.md) checks inventory, payment, cancellation, and delivery. This plan checks the assumptions that cross those boundaries. Passing the two component suites alone will not complete S1 in the [roadmap](../roadmap.md).

## Establish the shared contract

Admission permits a booking attempt; it does not promise available inventory. Preserve this distinction in both responses and test assertions. A displayed queue position is informational and cannot authorize a booking mutation.

Queue ordering also does not imply reservation completion order: eligible clients can arrive at booking at different times. A booking session on this page means an access/session concept that is not implemented yet. The existing `bk_ticket_session` migration models time-slot inventory; it is not the Q4 access session.

Before an integrated run, record the sale, participant, admission, session, reservation, payment, refund, and event identities that the implementation uses. Define which identities survive a retry, a new admission credential, session expiry, and process replacement. A new request identifier must not erase an existing obligation or disguise a duplicate payment.

| Boundary | Decision to fix before execution | Joint assertion |
|---|---|---|
| Queue → booking session | Credential scope, consumption authority, retry outcome, and logical session uniqueness | Concurrent credentials for one permitted admission cannot create more logical sessions than the contract allows |
| Session → reservation | Ownership checks, session lifetime, reservation lifetime, and idempotency scope | Another participant cannot mutate the reservation; access expiry alone does not invent a reservation transition |
| Reservation → provider | Inventory ownership while payment is unresolved, stable external identity, trusted amount/currency | An ambiguous response does not authorize a new charge or silently release capacity against the chosen policy |
| Provider → local state | Authenticated event/lookup contract, event ordering, reconciliation owner | A stale result cannot overwrite a newer valid decision; disagreement remains visible until resolved |
| Local commit → delivery | Durable event identity, relay ownership, receiver deduplication contract | Notification attempts can repeat without being mistaken for new bookings or new payment outcomes |

The runner generates participant identities, inventory, and monetary amounts from a reproducible seed. Fix the policy in the booking plan before execution.

## Minimum executable topology

Run waiting API, waiting worker, booking, background worker, and the fake provider/receiver as independently controllable processes, with the actual MySQL and separate queue/booking Redis roles. The two waiting roles must remain separate launches of `app-waiting`. Add a second API or worker instance when a case depends on competing owners; threads in one process are not a substitute.

Provision only the dependencies needed by the selected case. Keep the fake provider's operation history alive when terminating an application; deleting it would hide duplicate external effects. Include a cache/proxy only when testing status publication or ingress behavior, and record its configuration.

The future runner must provide isolated setup, seed/reset, execution, evidence collection, and cleanup commands. Cleanup must target resources created for that run and happen after failure artifacts are preserved. No such command is documented here until the implementation exists.

## Integrated scenarios

| Case | Controlled scenario | Required observations |
|---|---|---|
| FLOW-01 | One participant registers, becomes eligible, exchanges admission, reserves, pays, and receives a notification | Correlate one logical journey across all boundaries; inspect capacity, reservation, provider operation, and delivery effect rather than HTTP success alone |
| FLOW-02 | Admit more distinct participants than the available inventory and release their reservation requests together | Admitted participants can receive a defined sold-out result; successful reservations never exceed capacity and rejected attempts create no payment obligation |
| FLOW-03 | Lose responses after registration, handoff, reservation commit, and provider acceptance in separate runs; retry with the original logical identity | Each boundary recovers its established result or exposes its declared unresolved state; retries do not multiply positions, sessions, allocations, or charges |
| FLOW-04 | Obtain multiple credentials through concurrent issuance/reissuance, exchange them through independent booking instances, then submit conflicting reservation payloads | Session and idempotency scope remain consistent across services; invalid payload reuse and another participant's requests cause no unauthorized effects |
| FLOW-05 | Reach the hold deadline while the payment outcome is unknown and another participant requests that unit; deliver the original success late | If the policy releases the claim, test reallocation and require late success to follow compensation/exception handling without double allocation. If it retains the claim, reject reallocation while unresolved and keep its recovery state visible |
| FLOW-06 | Terminate booking after provider acceptance but before local completion; start two recovery workers, pause one beyond its ownership, then resume it | Durable investigation survives restart; stale recovery cannot replace a newer outcome, duplicate the external operation, or release capacity twice |
| FLOW-07 | Cancel a confirmed reservation; let the fake provider record a refund and drop the response; restart and replay cancellation | Refund totals remain within the captured amount per payment/currency; the same refund result is reconciled and capacity follows the chosen release policy once |
| FLOW-08 | Deliver an event, terminate the relay before its acknowledgement is stored, then replay; deliver an older confirmation after cancellation; exhaust retries for a deliberately rejected event | Distinguish delivery attempts from receiver effects; apply the declared event-ordering policy, retain or visibly park work, and prevent replay from creating a new booking |
| FLOW-09 | Expire or remove session/cache data while durable reservation or payment work exists | Recovery follows durable ownership and the documented access policy; cache loss neither erases a financial obligation nor silently grants replacement access |
| FLOW-10 | Slow a dependency and increase arrivals until protection engages; restore it and lower load | Rejections, timeouts, backlog age, and recovery are observable; accepted work remains reconcilable and restored capacity does not trigger an uncontrolled retry surge |

Include paused clients that resume and exchange credentials together in FLOW-10. Earlier controlled admission does not by itself establish a bound on the later booking burst; measure that arrival pattern and test the booking-side protection.

Run the low-load journey and boundary regressions before load variants. Repeat crash cases at named barriers, not merely at random elapsed times. A finite run may leave a deliberately unresolved obligation; report that state with its age and recovery path instead of counting it as a successful payment or silently discarding it.

## Evidence across the system

Collect the following for each scenario, with a run identifier and secret-free correlation identifiers:

- Client invocation/completion history, retries, accepted/rejected responses, and lost-response controls.
- Queue membership and progress changes; credential issuance, consumption, and logical session creation.
- Reservation transitions, allocation/release records, and consistent database snapshots for the declared inventory equation.
- Provider-side operations and local attempts, captured/refunded amounts by payment and currency, reconciliation results, and unresolved obligations.
- Durable event records, relay claims, delivery attempts, receiver effects, and parked work.
- Fault trigger/release times, process identities, dependency connection failures, pool use, pending-work age, and recovery timelines.

During a run, use transactionally consistent snapshots or a versioned event history for conservation checks. Independently sampling counters at different times can manufacture a discrepancy. After quiescence, compare durable records again, including unresolved work. Do not infer correctness from final remaining inventory alone or from net monetary totals that could hide a duplicate charge followed by a refund.

For a complete successful scenario, the history must explain every accepted operation and its permitted effects. For interrupted scenarios, it must explain both resolved outcomes and outstanding obligations. A health endpoint returning `UP` is not evidence that recovery has finished.

## Load and recovery protocol

Define these inputs before each measured run: initial capacity, synthetic participant count, duplicate-request ratio, arrival pattern, client timeout/retry policy, dependency delays, duration, warm-up, repetition count, seed, and resource limits. Record versions and image digests with the [report template](TEMPLATE.md).

Start with the same low-load reference scenario for each design comparison. Change one design choice or configuration dimension at a time. Then test sustained arrivals, a burst, concentrated inventory contention, delayed dependencies, and a recovery period. Do not choose a capacity target after seeing the best result.

Report offered and achieved work, generator drops, rejection categories, timeouts, latency distributions, queue waiting time, dependency duration, and backlog age. Separately count queue eligibility, credentials issued, sessions created, successful holds, confirmed payments, refunds, and applied notification effects; none is an interchangeable throughput measure.

Check invariants during observable consistent checkpoints and after the run. Record short windows around failures as well as full-run totals. Document generator saturation and competing host workloads. Keep failed or inconclusive runs alongside successful ones.

Graceful restart, abrupt process termination, datastore restart, backup restore, and replica failover are different experiments. Restore/failover remain extensions until their topology, persistence guarantees, and reconciliation procedure exist. A fake provider's contract and a local test environment limit every conclusion on this page.

## Completion and implementation order

1. Deliver L1's isolated topology and artifact collection, then the component contracts and minimal state controls.
2. Implement queue Q1–Q4 and booking B1 with their focused regressions; execute admission → hold before adding payment.
3. Add P1 and its restart/reconciliation cases, then P2 and D1; preserve an executable low-load path as each boundary is introduced.
4. Execute the applicable FLOW cases through independent processes, then run controlled load/recovery comparisons with the same correctness checks.
5. Link each case to its implementation, runner, regression where appropriate, and measured report. List unimplemented cases, excluded failures, and unavailable environments explicitly.

The [testing strategy](../testing.md#planned-validation-strategy) separates short PR regressions from longer fault-injection and load runs. Completing these documents establishes the experiment plan; it does not establish implemented functionality or a tested reliability guarantee.
