# Roadmap

Development follows the numbered branch sequence below. Each step delivers a focused implementation, relevant checks, and evidence before the next integration step.

**Current checkpoint: 00 and 01 implemented and verified locally.** The foundation also passed [GitHub Actions](https://github.com/yurim0628/ticketing-lab/actions/runs/39998162332); its [initial local record](results/foundation.md) remains separate. The current branch, `feature/01-walking-skeleton`, has a completed [local verification record](results/walking-skeleton.md). Queue, reservation, payment, and load experiments remain planned.

## Branch sequence

| Step | Branch | Scope | Status |
|---|---|---|---|
| 00 | `feature/project-foundation` (planned name: `feature/00-skeleton`) | Multi-module build, shared runtime defaults, initial migration, secret validation, CI | Implemented; verified locally and in GitHub Actions |
| 01 | `feature/01-walking-skeleton` | Compose stack, correlated requests through profile-scoped stubs, smoke runner | Implemented; verified locally |
| 02 | `feature/02-queue-enter` | Atomic queue registration with Lua and waiting identity/cookie | Planned |
| 03 | `feature/03-booking-session` | Entry credential verification and booking access session | Planned |
| 04 | `feature/04-worker-lock` | Worker ownership and monotonic admission progress | Planned |
| 05 | `feature/05-queue-admit` | Admission decision and signed entry credential | Planned |
| 06 | `feature/06-booking-hold` | Inventory hold with a conditional database update | Planned |
| 07 | `feature/07-hold-expiry` | Hold expiry and competing state changes | Planned |
| 08 | `feature/08-pg-adapter` | Controllable fake payment provider and adapter | Planned |
| 09 | `feature/09-payment-unknown` | Orders, payment attempts, and unknown outcomes | Planned |
| 10 | `feature/10-recon-netcancel` | Reconciliation and compensating cancellation | Planned |
| 11 | `feature/11-outbox` | Durable event relay and notifications | Planned |
| 12 | `test/12-invariants-fault-gate` | Invariant checks and controlled failure regressions | Planned |
| 13 | `feature/13-queue-ops` | Queue administration and operational controls | Planned |
| 14 | `test/14-queue-edge-load` | Edge request limits and k6 queue load experiments | Planned |
| 15 | `feature/15-protection-resilience` | Load shedding and dependency circuit breaking | Planned |
| 16 | `feature/16-booking-cancel` | Cancellation, refunds, and an auditable ledger | Planned |
| 17 | `feature/17-booking-otp` | Phone verification and per-participant limits | Planned |
| 18 | `feature/18-settlement` | Daily transaction and ledger reconciliation | Planned |
| 19 | `feature/19-booking-admin` | Booking administration and recovery controls | Planned |
| 20 | `test/20-secrets-audit` | Credential and key handling audit | Planned |
| 21 | `test/21-load-fault-report` | Combined k6 load, failure, and recovery report | Planned |

Branch numbers determine the integration order. A completed earlier step provides only its stated boundary: a stub response in 01 is not queue registration, and the provider echo is not the payment simulator delivered in 08.

## Branch workflow

- Work on the numbered feature or test branch; do not commit implementation directly to `main`.
- Prepare the change and its validation evidence before requesting a merge.
- Merge into `main` in numeric order only after explicit user approval. Do not enable automatic merging.
- Preserve the step boundary with a non-fast-forward merge and a `step-NN` tag when that integration is approved.
- Keep each branch's goals and decisions under `docs/dev/branches/`; link its actual results from the branch record. Planned checks and executed evidence have separate statuses.

## 01 · Walking skeleton

The [01 branch record](dev/branches/01-walking-skeleton.md) defines the implemented topology: four application artifacts running as five processes, two Redis instances, MySQL, and nginx through `docker-compose.yml`.

The client first requests a waiting echo, then uses the same UUID for a booking request. Booking calls the fake provider and returns its response as a nested dependency result. These routes exist only under the `walking-skeleton` profile. The isolated `smoke-ci` also checks a provider outage and recovery; it does not implement queue position, admission, inventory, payment, or notification behavior.

The runner interface is `scripts/lab.sh up`, `status`, `smoke`, `down`, `reset`, and `smoke-ci`. Isolated setup, routing, dependency failure/recovery, storage persistence/reset, and cleanup passed locally. See the [verification record](results/walking-skeleton.md) for evidence and the branch record for the command contract.

## Verification topics and numbered steps

The detailed experiment documents retain their topic IDs. They describe correctness and recovery coverage within the numbered sequence; they do not introduce another branch order.

| Topic ID | Numbered steps | Verification scope |
|---|---|---|
| F0 | 00 | Runtime foundation; [recorded local evidence](results/foundation.md) |
| L1 | 01; extend tooling with later steps | Reproducible topology, isolated data, commands, and evidence collection |
| Q1 | 02, 12, 13 | Registration identity, ordering, lifecycle, and controlled races |
| Q2 | 04, 12, 13 | Admission budget, competing workers, and stale ownership |
| Q3 | 04, 13, 14 | Status visibility, freshness, polling cost, and operational controls |
| Q4 | 03, 05, 12, 20 | Credentials, replay, booking handoff, and security checks |
| B1 | 06, 07, 12 | Inventory conservation, idempotency, expiry, and transaction interruption |
| P1 | 08, 09, 10, 12 | Payment uncertainty, independently recorded provider effects, and recovery |
| P2 | 10, 16, 18 | Compensation, refunds, and reconciliation |
| D1 | 11, 12; extend for 16 events | Durable notification intent, relay recovery, and duplicate effects |
| S1 | 14, 15, 21 | Queue load, protection behavior, and integrated load/failure evidence |

Retain the controlled cases in the [queue plan](experiments/queue.md), [booking plan](experiments/booking.md), and [integrated flow plan](experiments/system-flow.md). Completing a step does not complete every topic that mentions it. For example, 01 establishes the executable path; later branches add clocks, barriers, state inspection, and provider fault controls when their behavior needs them.

## Evidence required to complete a step

1. Define the boundary being changed: identities, inputs, outcomes, state, time, and data authority where relevant.
2. Keep meaningful regressions with the implementation. Use deterministic concurrency or failure controls when a happy-path test cannot establish the claim.
3. Record the exact revision, commands, environment, observed outcomes, and untested limits. Use the [report template](experiments/TEMPLATE.md) for experiments.
4. Check effects and authoritative state as well as HTTP responses. Unknown outcomes remain visible until reconciled.
5. Mark implementation, execution, and approval separately. A planned runner, skipped check, or unavailable environment is not a passing result.

The [testing guide](testing.md#planned-validation-strategy) describes how short regressions and longer process experiments fit together. The existing foundation checks continue to run as new behavior is introduced.

k6 is the planned HTTP load tool for the queue workload in 14 and the combined workload in 21. Earlier steps use focused correctness tests and smoke checks; they do not add an intervening load branch. Each load report must define its workload and resource limits, distinguish offered from achieved traffic, check correctness, and retain unsuccessful runs. Shared CI timing is not a capacity benchmark.
