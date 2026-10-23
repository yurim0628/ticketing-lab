# Booking experiments

**Status: planned.** Booking currently has application startup, datastore configuration, and an initial inventory table with SQL constraints. The worker has scheduling infrastructure but no business jobs. There are no reservation, payment, cancellation, reconciliation, or outbox implementations, and none of the experiments below has run.

The [queue plan](queue.md) covers eligibility and credential handoff. A booking session means the planned Q4 access session; the existing `bk_ticket_session` table represents time-slot inventory, not access. Admission grants permission to attempt a reservation; it is neither an inventory allocation nor proof of payment. The [system flow plan](system-flow.md) will connect these experiments through separate processes.

## Choose a small synthetic policy

The initial planned study uses fixed capacity and a fixed unit price per synthetic ticket session, one unit per reservation, and one currency represented in integer minor units. It uses a fake provider with a single charge operation rather than separate authorization and capture. Here, captured amount means the amount the fake provider records as successfully charged; settlement is not modeled. Cancellation is full cancellation only; partial refunds, fees, disputes, capacity edits, and real provider integration are outside this initial study.

These are deliberate experiment boundaries, not implemented behavior. The decisions below must be recorded before their expected outcomes become assertions.

| Decision | Questions the experiment must settle |
|---|---|
| Business state | Which reservation, order, and cancellation transitions are legal? Which states retain an inventory claim? What makes a transition terminal? |
| Time | Which clock is authoritative? Does equality with a deadline mean expired? What happens when payment starts before expiry but its outcome arrives after expiry? |
| Inventory release | Is release synchronous or deferred? When can a cancelled or expired claim be reused? What happens to a late successful charge after that reuse? |
| Request identity | How is a key scoped to actor, sale, operation, and payload? Can different keys refer to the same logical order or payment? What is the authorized retry result? |
| Retention | How long are retry results and operation identities retained? What rejects or resolves an old retry after cleanup? Unresolved financial work must not disappear when a client retry window ends. |
| Authority | Which records decide allocation, business state, money movement, and delivery? How are conflicting or missing records surfaced? |
| Provider contract | What does the fake provider guarantee about idempotency, key retention, status inquiry, event authenticity, and delayed results? When is repeating a request actually safe? |
| Recovery | Who owns pending investigation? Which failures are retryable, when does automation stop, and what synthetic operator action resolves an exceptional case? |

Use server-owned price and currency to construct a charge. A client's amount, a browser redirect, or a successful HTTP response is not independently authoritative evidence of payment. Authentication and resource ownership apply to retries, status lookup, and recovery commands as well as initial mutations.

## Separate the evidence domains

Use MySQL as the planned authority for inventory claims and local business state. Treat Redis session or cache state according to its explicit role; loss of a cache must not become a new reservation or charge. The fake provider keeps a separately inspectable operation history that survives a caller restart.

Record payment and refund facts with stable operation identities. Business status, monetary records, and outbox delivery status describe different things: a cancelled reservation can still have unresolved compensation, and a notification failure must not erase a confirmed booking. Do not collapse those states into a single success/failure flag.

| Check | Required interpretation |
|---|---|
| Inventory conservation | `available + allocated = fixed capacity`, with each unit counted once. Define allocated to include every unreleased claim required by the selected policy, including unresolved-payment claims when applicable. |
| Transition legality | Stored outcomes and recorded transitions follow the chosen state model; competing commands cannot resurrect or apply a terminal transition twice. |
| Monetary consistency | Known refunds cannot exceed the corresponding captured amount. Compare local and provider facts by operation, amount, and currency; unknown outcomes remain explicit discrepancies. |
| Delivery accountability | Every committed notification intent is pending, completed, or in an explicit recovery state. Delivery attempts and receiver effects are counted separately. |

Read conservation checks from a consistent database snapshot or a recorded operation history. Combining unrelated reads taken during concurrent updates can manufacture a discrepancy. Separate immediately enforceable safety properties from eventual recovery claims and state the conditions needed for convergence.

## B1 · Reservation, inventory, and expiry

**Question:** Can competing requests allocate and release inventory without losing the relationship between the response, reservation, and stored capacity?

| Case | Controlled scenario | Required observation |
|---|---|---|
| HOLD-01 | Release more independent buyers than remaining units from a barrier through two app instances and independent database connections | Simultaneously allocated units never exceed capacity; successful operations map to stored reservations and the conservation rule holds. |
| HOLD-02 | Submit the same scoped key and payload concurrently, discard a committed response, then retry | One logical reservation and one allocation; an authorized retry recovers its established outcome without presenting an expired reservation as newly active. |
| HOLD-03 | Reuse a key with a different ticket session or payload; try another actor's reservation and an old sale's key | The declared conflict or authorization outcome has no inventory or monetary side effects; knowing a key alone grants no access. |
| HOLD-04 | Race expiry with confirmation, release, and repeated worker execution immediately before, at, and after the deadline | The chosen deadline policy decides the winner; each claim is released or retained exactly according to that transition, without double release. |
| HOLD-05 | Interrupt the transaction between inventory, reservation, retry-result, and any required durable-work writes; also discard a commit acknowledgement | Rollback leaves no partial committed allocation; an ambiguous commit is resolved by operation identity before retrying the mutation. |
| HOLD-06 | Force competing lock acquisition, a deadlock, a statement timeout, and pool acquisition exhaustion in separate runs | Retry decisions distinguish those failures; attempts are bounded, no duplicate effect occurs, and connections and locks become usable after recovery. |
| HOLD-07 | Skew application clocks, delay the expiry worker, and expire or remove a session/cache record | The declared time and authority model controls validity; delayed cleanup and missing cache state cannot fabricate available inventory or revive an expired right. |
| HOLD-08 | Exercise generated sequences of reserve, retry, expire, confirm, and cancel against a small reference state model | Every observed transition is permitted; invocation order alone is not mistaken for commit order, and all unresolved operations remain identifiable. |
| HOLD-09 | Retry around the documented retention boundary while a reservation or payment remains unresolved | Cleanup follows the declared identity policy; forgetting a response or key cannot silently create a second allocation or erase recovery work. |

Compare a conditional-update protocol with a locking alternative under the same policy and workload. Keep deliberately unsafe implementations in isolated experiment fixtures. A sequential SQL constraint test is useful evidence about the schema, not a proof of the reservation protocol.

**Exit evidence:** the chosen state and time model, scoped request identities, database transaction outcomes, reservation and capacity snapshots, controlled race histories, and a small contention report. Test the endpoint through the queue handoff before claiming the full HTTP reservation path is complete.

## P1 · Payment uncertainty and reconciliation

**Question:** Can the system account for a charge when the caller cannot tell whether the provider applied it?

The fake provider needs durable, inspectable records and independent controls for accepting a request, recording an outcome, sending an event, and returning a response. A mock that only throws a timeout cannot demonstrate success followed by response loss. Define provider key-retention and inquiry behavior before relying on them for recovery.

| Case | Controlled scenario | Required observation |
|---|---|---|
| PAY-01 | Start payment concurrently for one logical order using both the same and different client request keys | The order/attempt model prevents unintended multiple charges; HTTP-key deduplication alone is insufficient. |
| PAY-02 | Record provider success, discard its response, then repeat the client request and run inquiry | The existing operation is recovered or remains explicitly unknown; no new charge identity is invented merely because a timeout occurred. |
| PAY-03 | Fail before provider acceptance, after acceptance, and while inquiry is unavailable; return delayed or initially inconclusive inquiry results | Observations are interpreted under the declared provider contract; silence or an inconclusive lookup is not automatically treated as definitive failure. |
| PAY-04 | Terminate the caller after persisting intent but before sending, and after provider success but before local completion | Durable recovery finds the unfinished attempt; restart does not rely on an in-memory timer or lose the stable provider operation identity. |
| PAY-05 | Deliver duplicate, reordered, delayed, forged, wrong-order, wrong-currency, or mismatched-amount events | Invalid events have no authorized effect; legitimate late facts are recorded and reconciled without overwriting a newer valid business outcome. |
| PAY-06 | Reach expiry while payment is unresolved, attempt another allocation, then deliver the original payment's success | If the policy releases the claim, late success cannot confirm against reallocated inventory and follows compensation or exception handling. If it retains the claim, reallocation is rejected while the allocation and recovery work remain inspectable. |
| PAY-07 | Pause a reconciliation worker after reading an attempt, let a successor act, then resume it | Conditional mutations reject stale work; an old owner cannot renew or release its successor's ownership or create a new charge. |
| PAY-08 | Keep inquiry unavailable across restarts and automatic retry exhaustion, then restore it or invoke the defined operator path | Pending work and its age remain durable and visible; retries stay bounded and the eventual resolution is auditable rather than silently discarded. |

External provider calls must not hold the business database transaction open. Pause a provider response and verify that the application has released the relevant database transaction and locks; also measure pool occupancy and unrelated request progress. A configured SQL timeout does not establish this boundary.

Repeating a provider request with the same stable key can be valid only within the documented provider guarantees. Distinguish that operation from blindly submitting a fresh charge. An older attempt completing late remains a financial fact even if it can no longer confirm the reservation.

**Exit evidence:** joined local/provider operation histories, crash-point traces, inquiry and event observations, unresolved-age metrics, and a reconciliation report. Every converged outcome must be supported by facts; every unconverged outcome must remain inspectable.

## P2 · Full cancellation and compensation

**Question:** Can cancellation release the intended inventory and account for a full refund despite repeated commands and uncertain external results?

Fix the cancellation deadline, authorization rules, and inventory-release point before testing. A cancellation request, a locally cancelled booking, a submitted refund, and a confirmed refund are distinct observations. The initial policy excludes partial refunds but still needs a response to an unexpected or inconsistent provider amount.

| Case | Controlled scenario | Required observation |
|---|---|---|
| CANCEL-01 | Cancel an unpaid reservation and a successfully paid booking, before and at the chosen deadline | The permitted transition follows the synthetic policy; an unpaid case causes no refund, and each claim is released at most once. |
| CANCEL-02 | Race user cancellation, repeated client keys, different keys for the same cancellation, and a recovery worker | One logical cancellation and the intended full-refund operation; request-key deduplication cannot substitute for business-operation uniqueness. |
| CANCEL-03 | Record a successful refund, lose the response, then terminate and restart the caller | Recovery uses the established refund identity and provider evidence; the response loss cannot create an additional refund. |
| CANCEL-04 | Reject or delay the refund and keep its outcome unknown while cancellation processing continues | Business, inventory, and monetary states follow their declared relationship; unresolved compensation stays visible rather than being labelled refunded. |
| CANCEL-05 | Deliver an old payment or refund event after cancellation, or discover a successful charge after a reservation is no longer confirmable | Valid financial facts are retained; the compensation path cannot resurrect the booking, double-release inventory, or hide the charge. |
| CANCEL-06 | Attempt excess refund, alter client price/currency, target another buyer's order, and return inconsistent provider totals | Unauthorized requests cause no external operation; local constraints and reconciliation expose impossible amounts without forcing records to agree. |

Check captured and refunded totals per operation and currency, including duplicates in the event stream. Count a refund as confirmed only when the provider contract supplies that evidence. If compensation remains unresolved, the experiment must report that result instead of claiming automatic convergence.

**Exit evidence:** cancellation/compensation transitions, inventory release history, captured and refunded amounts, failed and unresolved cases, and the executable recovery procedure.

## D1 · Durable notification intent and delivery

**Question:** Does committed work remain accountable across relay crashes, duplicate delivery, and exhausted retries?

Persist notification intent with the corresponding local business transition. Use a fake receiver that records both delivery attempts and applied effects. A successful send followed by a lost acknowledgement is not the same as a failed send, and a transactional outbox does not itself guarantee exactly-once external effects.

| Case | Controlled scenario | Required observation |
|---|---|---|
| DELIVERY-01 | Interrupt the business transaction before and after writing its event, then roll back or commit | A rolled-back transition produces no committed delivery intent; every committed transition that requires notification has a durable intent. |
| DELIVERY-02 | Terminate the relay before sending and after receiver acceptance but before recording completion | Work is recovered; duplicate attempts are observable, and receiver effects follow its explicitly tested deduplication contract. |
| DELIVERY-03 | Run two relays, expire one claim, and resume the stale owner while the successor processes the event | Stale local completion, renewal, or release cannot corrupt the successor's work; possible duplicate sends remain accounted for. |
| DELIVERY-04 | Inject a permanent payload failure and repeated transient failures; exhaust retries and later replay the item | Retry classification and limits are enforced; blocked work is inspectable, replay preserves the required identity, and no item disappears silently. |
| DELIVERY-05 | Deliver confirmation and cancellation notifications out of order, then duplicate both | The receiver's ordering/version policy is explicit; duplicate suppression alone does not guarantee a correct final presentation. |
| DELIVERY-06 | Slow or stop the receiver while new bookings commit, then restore it | Business transactions are not held open for delivery; backlog and oldest-item age are visible, and recovery proceeds within the declared resource limits. |

**Exit evidence:** business/event transaction outcomes, claim and attempt history, receiver effects, backlog age, exhausted items, and a replay trace. State which duplicate effects can be prevented by the receiver and which remain outside its contract.

## Recovery and load matrix

Run deterministic regressions during each milestone. Add process interruption and workload experiments as soon as their paths exist; the final integrated milestone combines those results rather than introducing recovery testing for the first time.

| Experiment | Controlled setup | Required observation |
|---|---|---|
| API/worker replacement | Terminate separate app processes with in-flight operations and pending durable work | Retry identities and recovery records survive; acknowledged, committed-but-unacknowledged, failed, and unknown operations are reconciled separately. |
| Datastore interruption | Separately delay/disconnect MySQL and Redis, exhaust the connection pool, then restore service | Admission to work, timeouts, retries, and recovery follow explicit bounds; missing dependencies never create inventory or monetary success. |
| Persistence recovery | Test graceful restart and abrupt termination with recorded durability settings | Compare acknowledged writes with restored records; report any observed loss and its effect on pending provider operations. |
| Replication/restore extension | Add a defined replica topology or restore procedure before exercising it | Measure its distinct failure and loss model; a standalone restart cannot substantiate failover or backup-recovery claims. |
| Hot inventory | Concentrate requests on a small session while other sessions receive traffic | Inventory invariants hold; lock waits, conflicts, pool use, fairness observations, and unrelated work remain measurable. |
| Retry storm and slow provider | Mix original requests, repeated keys, new-key conflicts, delayed inquiries, and notifications | Logical operations and physical attempts are counted separately; amplification, pending age, and resource saturation are visible. |
| Recovery after overload | Reduce demand and restore dependencies after a sustained backlog | Correctness still holds; durable work drains or reaches an explicit exception state, with measured recovery time and no silent deletion. |

Record offered and achieved rates, generator limitations, outcomes by operation, latency distributions, database/connection waits, provider requests, retry counts, unresolved ages, and delivery backlog. Expected sold-out, expired, and conflict responses are not interchangeable with dependency failures. Capacity claims require a workload and environment; configuration values alone establish none.

Use durable identities to reconcile operations whose responses were lost. Counting only successful HTTP responses understates committed allocations and charges. Inspect invariants during a run using consistent observations, then reconcile all stores after it settles or record why it cannot settle.

## Completion and limits

Link B1, P1, P2, and D1 in the [roadmap](../roadmap.md) to their implementation, regression checks, actual commands, and evidence recorded with the [report template](TEMPLATE.md). Follow the [planned validation strategy](../testing.md#planned-validation-strategy); no new test runner or command is implied by this document.

Complete the low-load [system flow](system-flow.md) before claiming the queue-to-booking path works, then combine contention and selected failures. Mark implemented behavior and measured behavior separately. Planned scenarios, inconclusive runs, and untested provider assumptions remain explicit gaps.

Multi-currency accounting, partial refunds, and disputes require additional state models and experiments. The initial study covers one currency and full cancellation through the fake provider.
