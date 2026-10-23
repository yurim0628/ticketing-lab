# syntax=docker/dockerfile:1
#
# Runtime image for one app module. The jar is built on the host first (./gradlew bootJar); this image only packages it,
# so no source code, Gradle cache or secret ever enters a layer. The build context is the repo root, and .dockerignore
# lets in nothing but the */build/libs/app.jar files.
#
#   docker build --build-arg APP_DIR=app-booking -t ticketing-lab/app-booking:dev .
#   APP_DIR: app-waiting | app-booking | app-worker | tools/fake-ext
#
# Secrets are never baked in: they arrive as environment variables at run time, and the app refuses to start without them.
FROM eclipse-temurin:21-jre

# No usable default: without --build-arg APP_DIR the COPY below fails with this name in its "not found" error.
ARG APP_DIR=APP_DIR-build-arg-is-required

# Non-root runtime user with a fixed UID/GID, no home directory and no login shell.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin app

WORKDIR /app
# Owned by root and read-only, so the app user cannot replace its own jar.
COPY --chown=root:root --chmod=0444 ${APP_DIR}/build/libs/app.jar /app/app.jar

# Use ZGC and size the heap to 70% of the container memory limit. Set a memory limit on every container.
# Keep these JVM options fixed when comparing runs; record any overrides with the experiment results.
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Dnetworkaddress.cache.ttl=5"

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
