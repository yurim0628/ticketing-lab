# Running the project

Run all commands from the repository root. The examples use Bash or Zsh on macOS or Linux.

## Prerequisites

- JDK 21, selected through `JAVA_HOME` or available to Gradle's Java toolchain.
- Network access for the first Gradle and dependency downloads. The wrapper provides Gradle 8.14.3; a global Gradle installation is unnecessary.
- A running Docker engine for integration tests, scanning, images, and the local stack. The first run downloads container images.
- Docker Compose v2 (`docker compose`) and Python 3 for `scripts/lab.sh` and the full pipeline.
- Git and OpenSSL for the full verification script. `curl` is used only in the manual HTTP examples below.

The project uses Spring Boot 3.5.16. Dependency versions are recorded in the [version catalog](../gradle/libs.versions.toml).

## Start the Compose stack

```bash
scripts/lab.sh up
scripts/lab.sh status
scripts/lab.sh smoke
```

The [Compose file](../docker-compose.yml) starts five application processes from four artifacts, two Redis instances, MySQL, and nginx. nginx binds to `127.0.0.1:18080`; application and datastore ports remain inside the Compose network. Each local stack has a generated project identity and ownership labels.

The runner generates `.lab/runtime.env` and `.lab/state.json` with mode `0600`. `.lab/` is ignored. Existing values are reused across restarts. `LAB_HTTP_PORT` can select a different port when the local state is first created; the saved value is used afterwards.

| Command | Behavior |
|---|---|
| `scripts/lab.sh up` | Build application JARs/images, start the stack, and wait for readiness |
| `scripts/lab.sh status` | Show container state and health |
| `scripts/lab.sh smoke` | Check all nine services, UUID correlation through waiting and booking/provider, one successful initial migration, and an empty inventory table |
| `scripts/lab.sh down` | Stop the stack and retain its data volumes |
| `scripts/lab.sh reset` | Delete owned data volumes, immediately restart using existing images, and run the smoke |
| `scripts/lab.sh smoke-ci` | Build a separate temporary project with an allocated loopback port; test the flow, provider outage/recovery, persistence and reset; collect diagnostics and clean up |

`smoke-ci` stores `summary.json` and redacted `compose.log` under `build/reports/compose-smoke/`. It verifies that shutdown/restart preserves data in both Redis instances and MySQL, then that reset clears that data and preserves an unrelated control volume. Its temporary credentials and project are separate from the manual `.lab/` stack. Cleanup removes the CI project's resources and owned images, plus the control volume created by that check. If cleanup fails, private recovery state is retained at the reported location.

The stub routes require the `walking-skeleton` profile; the waiting route also requires `api`. The smoke first calls waiting, then booking with the same UUID. Booking calls the provider and returns a nested response. This checks wiring, not queue or payment behavior. The [walking-skeleton record](results/walking-skeleton.md) contains the completed local verification.

## Start one application without Compose

Without `walking-skeleton`, `fake-ext` exposes its operational endpoints only. It requires no database, Redis, or secrets. This standalone example uses port `18085` so it can coexist with the Compose edge.

```bash
./gradlew :fake-ext:bootRun --args='--server.address=127.0.0.1 --server.port=18085'
```

In a second terminal:

```bash
curl --fail http://127.0.0.1:18085/actuator/health
curl --fail http://127.0.0.1:18085/actuator/health/liveness
curl --fail http://127.0.0.1:18085/actuator/health/readiness
curl --fail http://127.0.0.1:18085/actuator/prometheus
```

The health endpoint should report `UP` after startup. There is no application endpoint at `/`, and no simulated payment API yet. Stop `bootRun` with Ctrl-C.

The equivalent packaged run is:

```bash
./gradlew :fake-ext:bootJar
java -jar tools/fake-ext/build/libs/app.jar --server.address=127.0.0.1 --server.port=18085
```

The explicit loopback address keeps this local example on the host. Without overrides, Spring Boot uses port `8080`; health, info, and Prometheus are exposed on that same listener. There is no separate management port or authentication layer in the current applications.

## Run checks

```bash
./gradlew test
```

This runs tests other than classes named `*IT`, including startup and architecture checks, without Docker. With Docker running:

```bash
./gradlew integrationTest
```

Integration tests provision MySQL and Redis through Testcontainers, using generated credentials and random host ports. They do not require manually created databases or exported application secrets. Containers are started on demand per test JVM.

`./gradlew build` includes integration tests through `check`, so it also needs Docker. To build artifacts without running tests, use `./gradlew assemble`. See [testing](testing.md) for the scope and limits of each check.

## Run applications with external dependencies

The Compose runner provisions dependencies automatically. For separate host-process runs with `bootRun`, supply the services below yourself. The test environment uses Redis 7.4 and MySQL 8.4. Testcontainers provisioning applies to tests, not `bootRun`.

| Application | Dependencies for a healthy runtime | Required environment | Optional connection overrides |
|---|---|---|---|
| Waiting, `api` profile | Queue Redis | `WAIT_SECRET` (at least 32 UTF-8 bytes), `QUEUE_ADMIN_KEY` (at least 16 UTF-8 bytes) | `QUEUE_REDIS_HOST`, `QUEUE_REDIS_PORT` |
| Waiting, `worker` profile | Queue Redis | None | `QUEUE_REDIS_HOST`, `QUEUE_REDIS_PORT` |
| Booking | MySQL database and booking Redis | `BOOKING_DB_USERNAME`, `BOOKING_DB_PASSWORD` | `BOOKING_DB_URL`, `BOOKING_REDIS_HOST`, `BOOKING_REDIS_PORT` |
| Worker | MySQL database and booking Redis | `WORKER_DB_USERNAME`, `WORKER_DB_PASSWORD` | `WORKER_DB_URL`, `BOOKING_REDIS_HOST`, `BOOKING_REDIS_PORT` |

Both database URLs default to `jdbc:mysql://localhost:3306/ticketing`. The database must already exist. Database passwords must be at least eight UTF-8 bytes, and the username must not be `root`. Booking applies its Flyway migrations; the worker does not. Use the same database when running booking and its worker together, and start booking first to initialize the schema.

Redis hosts default to `localhost` and ports to `6379`. Configure separate queue and booking Redis endpoints when running both roles. The [environment variable list](../.env.example) contains names only: Spring Boot does not load that file automatically. Export the variables you need in your shell; leave optional variables unset to use defaults rather than exporting empty values.

For a local waiting API run, generate disposable secrets in the shell without writing values to the repository:

```bash
export WAIT_SECRET="$(openssl rand -hex 32)"
export QUEUE_ADMIN_KEY="$(openssl rand -hex 16)"
./gradlew :app-waiting:bootRun --args='--spring.profiles.active=api --server.address=127.0.0.1 --server.port=18081'
```

With the required dependencies and environment supplied, run the other applications in separate terminals as needed:

```bash
./gradlew :app-waiting:bootRun --args='--spring.profiles.active=worker --server.address=127.0.0.1 --server.port=18082'
./gradlew :app-booking:bootRun --args='--server.address=127.0.0.1 --server.port=18083'
./gradlew :app-worker:bootRun --args='--server.address=127.0.0.1 --server.port=18084'
```

The waiting application requires exactly one of `api` or `worker`; neither and both are rejected. These commands start application foundations, not a working booking flow.

## Build and run a container

The [Dockerfile](../Dockerfile) packages an already built JAR and runs it as a non-root user. Build the host artifact before building the image:

```bash
./gradlew :fake-ext:bootJar
docker build --build-arg APP_DIR=tools/fake-ext -t ticketing-lab/fake-ext:local .
docker run --rm --read-only --tmpfs /tmp --memory 512m -p 127.0.0.1:18085:8080 ticketing-lab/fake-ext:local
```

Use the same health request as in the first example. The image smoke check is available through `scripts/ci.sh image`; its precise coverage is described in [testing](testing.md).
