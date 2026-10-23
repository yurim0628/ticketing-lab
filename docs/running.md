# Running the project

Run all commands from the repository root. The examples use Bash or Zsh on macOS or Linux.

## Prerequisites

- JDK 21, selected through `JAVA_HOME` or available to Gradle's Java toolchain.
- Network access for the first Gradle and dependency downloads. The wrapper provides Gradle 8.14.3; a global Gradle installation is unnecessary.
- A running Docker engine for integration tests, secret scanning, and image checks. The first run downloads container images.
- Git and OpenSSL for the full verification script. `curl` is used only in the manual HTTP examples below.

The project uses Spring Boot 3.5.16. Dependency versions are recorded in the [version catalog](../gradle/libs.versions.toml).

## Start the smallest application

`fake-ext` currently contains only the application and its operational endpoints. It requires no database, Redis, or secrets, making it a useful first startup check.

```bash
./gradlew :fake-ext:bootRun --args='--server.address=127.0.0.1 --server.port=18080'
```

In a second terminal:

```bash
curl --fail http://127.0.0.1:18080/actuator/health
curl --fail http://127.0.0.1:18080/actuator/health/liveness
curl --fail http://127.0.0.1:18080/actuator/health/readiness
curl --fail http://127.0.0.1:18080/actuator/prometheus
```

The health endpoint should report `UP` after startup. There is no application endpoint at `/`, and no simulated payment API yet. Stop `bootRun` with Ctrl-C.

The equivalent packaged run is:

```bash
./gradlew :fake-ext:bootJar
java -jar tools/fake-ext/build/libs/app.jar --server.address=127.0.0.1 --server.port=18080
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

The repository does not yet provide a Compose stack. For manual runs of waiting, booking, or the worker, provision the services below yourself. The test environment uses Redis 7.4 and MySQL 8.4. Testcontainers provisioning applies to tests, not `bootRun`.

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
docker run --rm --read-only --tmpfs /tmp --memory 512m -p 127.0.0.1:18080:8080 ticketing-lab/fake-ext:local
```

Use the same health request as in the first example. The image smoke check is available through `scripts/ci.sh image`; its precise coverage is described in [testing](testing.md).
