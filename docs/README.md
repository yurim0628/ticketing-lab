# Documentation

The foundation and step 01 walking skeleton have completed local verification. The walking skeleton connects profile-scoped stubs through Compose. Queueing, reservation, payment, and notification workflows remain planned.

Read the documentation in this order:

1. [Run the project](running.md) to start the Compose stack, a standalone app, or the checks.
2. [Understand the architecture](architecture.md) to see the current modules and dependency boundaries.
3. [Inspect the tests](testing.md) to understand what the existing checks establish.
4. [Follow the roadmap](roadmap.md) for the 00–21 branch sequence and completion criteria.
5. [Explore the experiments](experiments/README.md) for hypotheses and the evidence needed to evaluate them.

The [01 branch record](dev/branches/01-walking-skeleton.md) defines the topology, stub contract, and command interface. Its [verification record](results/walking-skeleton.md) contains the completed local checks.

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
