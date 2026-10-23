# Documentation

This is an experimental backend, with an implemented startup and verification foundation. Queueing, reservation, payment, and notification workflows are planned work. A design proposal is not evidence that its behavior has been implemented or measured.

Read the documentation in this order:

1. [Run the project](running.md) to start the smallest application or run the checks.
2. [Understand the architecture](architecture.md) to see the current modules and dependency boundaries.
3. [Inspect the tests](testing.md) to understand what the existing checks establish.
4. [Follow the roadmap](roadmap.md) for milestone dependencies, concrete next changes, and completion criteria.
5. [Explore the experiments](experiments/README.md) for hypotheses and the evidence needed to evaluate them.

The detailed plans cover three connected scopes:

| Plan | Scope |
|---|---|
| [Queue](experiments/queue.md) | Registration, admission control, status visibility, credential handoff, and failure/load scenarios |
| [Booking](experiments/booking.md) | Inventory holds, expiry, payment uncertainty, cancellation, reconciliation, and durable delivery |
| [Queue to booking](experiments/system-flow.md) | Shared contracts, complete journeys across independent processes, correlated evidence, and combined load/recovery |

These are implementation and verification plans. Their scenarios remain unimplemented and unmeasured until linked code and execution evidence demonstrate otherwise.

The [foundation verification record](results/foundation.md) contains the first local test and container-check results, with [machine-readable test totals](results/foundation-summary.json). It is functional evidence, not a business-workflow benchmark.

The [experiment report template](experiments/TEMPLATE.md) records the environment, exact commands, raw evidence, and limits of a completed investigation. It contains no sample measurements.

The [repository README](../README.md) provides the project overview.
