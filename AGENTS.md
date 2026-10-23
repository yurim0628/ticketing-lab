# Working in Ticketing Lab

Ticketing Lab studies concurrency and failure through reproducible experiments. Read [README.md](README.md), [CONTRIBUTING.md](CONTRIBUTING.md), and the relevant documents in [docs/](docs/README.md) before changing behavior.

## Scope and evidence

- Steps 00 and 01 are implemented and verified locally; see [the 01 record](docs/results/walking-skeleton.md). Queue, reservation, payment, and recovery workflows remain planned.
- Follow the numbered branch sequence and approval boundaries in [the roadmap](docs/roadmap.md).
- Follow the user's requested scope. Preserve existing work and avoid unrelated cleanup.
- Use generated fixtures for experiments and keep credentials out of tracked files.
- Record measured results only with the environment, exact reproduction method, and limitations. Do not invent benchmark values, badges, or test results.

## Implementation

- Java 21, Spring Boot, and Gradle Groovy DSL are the current stack. Versions are defined in `gradle/libs.versions.toml` and the Gradle wrapper.
- Executable modules are `app-waiting`, `app-booking`, `app-worker`, and `fake-ext` (`tools/fake-ext`). Libraries are `core` and `test-support`.
- Apps must not depend directly on each other. Main code must not depend on `test-support`.
- `core` currently contains framework configuration and is not a pure domain module. Add domain and adapter boundaries when implemented behavior calls for them.
- Follow the existing formatting and meaningful naming.
- Generate test credentials at runtime. Avoid writing secret values or private key material into tracked fixtures.

## Verification

- Ordinary tests: `./gradlew test`, or a specific module's `test` task.
- Integration tests: `./gradlew integrationTest`; Docker is required and Testcontainers supplies disposable MySQL and Redis.
- Full local and GitHub verification: `scripts/ci.sh` (seven stages, ending with the isolated Compose smoke).
- Local stack: `scripts/lab.sh up`, `status`, `smoke`, and `down`; requires JDK 21, Docker Compose v2, and Python 3.
- `scripts/lab.sh smoke-ci` also tests provider outage/recovery, persistence, and isolated reset. `reset` deletes owned data, restarts, and runs the ordinary smoke.
- Manual stack state lives in ignored `.lab/`; generated environment values use file mode `0600`.
- Optional name checking: `scripts/name-scan.sh`, with a private local denylist supplied separately. Do not print that list or add it to the repository.
- For documentation-only changes, check facts, links, and formatting. Do not run the entire application suite without a reason.
- Report executed, skipped, cached, and unavailable checks accurately. A task with no tests is not proof of a behavior.

Publishing, merging, deploying, or rewriting Git history is separate from editing and verification; follow the user's authorization for those actions.
