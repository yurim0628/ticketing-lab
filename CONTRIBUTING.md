# Contributing to Ticketing Lab

The unit of progress in this repository is an explained, reproducible result. A small experiment with a clear limit is more useful than an unverified performance claim.

## Choose a focused change

- **Experiment:** state a hypothesis, invariant, failure scenario, and measurement method before implementation. Use the [report template](docs/experiments/TEMPLATE.md).
- **Bug fix:** capture a failing case, fix the cause, and keep the regression check.
- **Documentation:** verify statements against the current code and commands. Label proposed behavior as proposed.
- **Refactoring:** keep behavior stable and explain which dependency or responsibility becomes clearer.

Discuss changes to public contracts or experimental assumptions in the issue or pull request that introduces them. Routine implementation choices can be documented with the change.

## Local workflow

1. Create a focused branch such as `experiment/queue-contention`, `fix/startup-validation`, or `docs/architecture`.
2. Read the affected module and its existing tests. The [architecture guide](docs/architecture.md) describes current boundaries.
3. Implement the smallest meaningful change and run its relevant tests.
4. Review both staged and unstaged changes. Include the reproduction, validation results, and limitations in the pull request.

```bash
./gradlew :core:test
./gradlew :app-booking:integrationTest
scripts/ci.sh
```

Only run the commands relevant to your change; the full pipeline needs Docker. Test results reported as `NO-SOURCE`, skipped, or reused from cache are not newly executed tests. GitHub Actions runs the shared verification entry point and retains test reports.

## Code and documentation

- Java and Gradle Groovy scripts use four spaces; YAML uses two. Preserve UTF-8 and final newlines.
- Use descriptive names, constructor injection, and the narrowest useful visibility. Keep application modules independent.
- Put infrastructure details at the boundary that owns them. Do not introduce unused abstractions or modules to match an aspirational diagram.
- Never let production code depend on `test-support`.
- Write tests around observable behavior, invariants, and failure cases rather than reproducing the implementation.

## Local configuration and hooks

Credentials used by tests are generated at runtime. `.env.example` lists variable names without values. Keep local configuration and test output out of commits.

```bash
scripts/install-hooks.sh
```

Hooks are optional and their output states which checks ran. An owner-maintained name denylist can be used locally through `scripts/name-scan.sh`; it is not required to build the project or run public CI. The list itself must remain untracked.

## Pull requests

Explain the problem and resulting behavior first. Include the commands actually executed, their results, and any relevant limits. Link an experiment report when the change makes a design or performance claim.

Use concise commit subjects such as `fix(config): reject placeholder credentials` or `docs: explain the experiment workflow`. Keep a behavior change and its regression test together. Do not split commits by file count alone.

Do not attach raw logs containing secrets or identifying data. Review exported reports before sharing them.
