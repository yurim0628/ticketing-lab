#!/usr/bin/env bash
# Image check for the non-root Dockerfile. Called by ci.sh (stage "image"); can run standalone after ./gradlew bootJar.
#
#   1) Builds one image per app module with a unique tag from jars already built on the host.
#   2) Every image: configured user is 10001:10001, `id -u` inside is not 0, the jar is not writable by that user,
#      and JAVA_TOOL_OPTIONS carries the configured JVM flags.
#   3) fake-ext (no external dependencies): starts under a 512m memory limit with a read-only root
#      filesystem, answer /actuator/health from inside the container, PID 1 runs as UID 10001, and the JVM's max heap
#      is about 70% of the limit (MaxRAMPercentage=70).
#   4) app-waiting with the api profile and no secrets: the container exits non-zero with the startup failure report.
#
# No host ports are published: HTTP checks run inside the container (bash /dev/tcp, since the image has no curl).
# Only container IDs and unique image tags created by this invocation are removed on exit.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_DIRS=(app-waiting app-booking app-worker tools/fake-ext)
RUN_APPS=(fake-ext)
MEMORY_LIMIT_MB=512
EXPECTED_USER="10001:10001"
EXPECTED_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Dnetworkaddress.cache.ttl=5"
STARTUP_WAIT_S=60
RUN_ID="$(date +%s)-$$-$(openssl rand -hex 6)"

created=()
created_images=()
cleanup() {
  if [[ ${#created[@]} -gt 0 ]]; then
    docker rm -f "${created[@]}" >/dev/null 2>&1 || true
  fi
  if [[ ${#created_images[@]} -gt 0 ]]; then
    docker image rm "${created_images[@]}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "image-smoke: FAIL — $*" >&2
  exit 1
}

image_of() { echo "ticketing-lab/$(basename "$1"):smoke-$RUN_ID"; }

create_container() {
  local name="$1"; shift
  CURRENT_CONTAINER="$(docker create --name "$name" --label lab.ticketing.role=image-smoke \
    --label "lab.ticketing.run=$RUN_ID" "$@")" || return
  created+=("$CURRENT_CONTAINER")
  docker start "$CURRENT_CONTAINER" >/dev/null
}

if ! docker info >/dev/null 2>&1; then
  fail "Docker not available"
fi
for dir in "${APP_DIRS[@]}"; do
  [[ -f "$dir/build/libs/app.jar" ]] || fail "$dir/build/libs/app.jar missing; run ./gradlew bootJar first"
done

# Bound each request so an unresponsive process cannot stall the readiness loop indefinitely.
http_in() {
  local container="$1" path="$2"
  docker exec "$container" timeout 3s bash -c '
    exec 3<>/dev/tcp/127.0.0.1/8080
    printf "GET %s HTTP/1.0\r\nHost: localhost\r\n\r\n" "$1" >&3
    cat <&3
  ' _ "$path" 2>/dev/null
}

# ── 1) + 2) Build every image and check its user ─────────────
for dir in "${APP_DIRS[@]}"; do
  image="$(image_of "$dir")"
  if docker image inspect "$image" >/dev/null 2>&1; then
    fail "generated image tag already exists; refusing to overwrite it"
  fi
  start=$(date +%s)
  docker build -q --build-arg APP_DIR="$dir" --label lab.ticketing.image=ci \
    --label "lab.ticketing.run=$RUN_ID" -t "$image" . >/dev/null
  created_images+=("$image")
  took=$(( $(date +%s) - start ))

  user="$(docker image inspect -f '{{.Config.User}}' "$image")"
  [[ "$user" == "$EXPECTED_USER" ]] || fail "$image: configured user is '$user', expected $EXPECTED_USER"
  uid="$(docker run --rm --network none --entrypoint id "$image" -u)"
  [[ "$uid" != "0" ]] || fail "$image: runs as root"
  docker run --rm --network none --entrypoint sh "$image" -c 'test ! -w /app/app.jar' \
    || fail "$image: /app/app.jar is writable by the runtime user"
  env_line="$(docker image inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$image" | grep '^JAVA_TOOL_OPTIONS=' || true)"
  [[ "$env_line" == "JAVA_TOOL_OPTIONS=$EXPECTED_TOOL_OPTIONS" ]] || fail "$image: unexpected $env_line"
  size_mb=$(( $(docker image inspect -f '{{.Size}}' "$image") / 1024 / 1024 ))
  echo "image-smoke: $image built in ${took}s, ${size_mb} MB, user $user (id -u = $uid), jar read-only"
done

# ── 3) Run the apps that need no external dependency ─────────
limit_bytes=$(( MEMORY_LIMIT_MB * 1024 * 1024 ))
for app in "${RUN_APPS[@]}"; do
  image="$(image_of "$app")"
  name="ticketing-lab-smoke-$app-$RUN_ID"
  create_container "$name" --memory "${MEMORY_LIMIT_MB}m" --read-only --tmpfs /tmp "$image"
  cid="$CURRENT_CONTAINER"

  healthy=0
  deadline=$((SECONDS + STARTUP_WAIT_S))
  while (( SECONDS < deadline )); do
    [[ "$(docker inspect -f '{{.State.Running}}' "$cid")" == "true" ]] || break
    if http_in "$cid" /actuator/health | grep -q '"status":"UP"'; then
      healthy=1
      break
    fi
    sleep 1
  done
  if [[ $healthy -ne 1 ]]; then
    docker logs --tail 50 "$cid" >&2 || true
    fail "$name did not report UP within ${STARTUP_WAIT_S}s"
  fi

  pid1_uid="$(docker exec "$cid" awk '/^Uid:/ {print $2}' /proc/1/status)"
  [[ "$pid1_uid" == "10001" ]] || fail "$name: PID 1 runs as UID $pid1_uid"
  logs="$(docker logs "$cid" 2>&1)"
  grep -q 'Picked up JAVA_TOOL_OPTIONS' <<<"$logs" || fail "$name: JAVA_TOOL_OPTIONS not picked up"
  prom="$(http_in "$cid" /actuator/prometheus)"
  ready_s="$(awk '/^application_ready_time_seconds\{/ {print $NF; exit}' <<<"$prom")"
  heap="$(awk '/^jvm_memory_max_bytes\{/ && /area="heap"/ {sum += $NF} END {printf "%.0f", sum}' <<<"$prom")"
  [[ -n "$heap" && "$heap" -gt 0 ]] || fail "$name: could not read jvm_memory_max_bytes"
  ratio_pct=$(( heap * 100 / limit_bytes ))
  if (( ratio_pct < 65 || ratio_pct > 71 )); then
    fail "$name: heap max $heap bytes is ${ratio_pct}% of the ${MEMORY_LIMIT_MB}m limit, expected about 70%"
  fi
  mem="$(docker stats --no-stream --format '{{.MemUsage}}' "$cid")"
  echo "image-smoke: $name UP (ready in ${ready_s:-?}s), PID 1 UID $pid1_uid, heap max $(( heap / 1024 / 1024 )) MiB = ${ratio_pct}% of ${MEMORY_LIMIT_MB}m, memory $mem"
  docker rm -f "$cid" >/dev/null
done

# ── 4) Missing secret → the container stops with a startup failure ──
image="$(image_of app-waiting)"
name="ticketing-lab-smoke-failfast-$RUN_ID"
create_container "$name" --network none --memory "${MEMORY_LIMIT_MB}m" \
  --read-only --tmpfs /tmp -e SPRING_PROFILES_ACTIVE=api "$image"
cid="$CURRENT_CONTAINER"
for _ in $(seq 1 "$STARTUP_WAIT_S"); do
  [[ "$(docker inspect -f '{{.State.Running}}' "$cid")" == "true" ]] || break
  sleep 1
done
[[ "$(docker inspect -f '{{.State.Running}}' "$cid")" == "false" ]] || fail "$name still running after ${STARTUP_WAIT_S}s without secrets"
exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$cid")"
logs="$(docker logs "$cid" 2>&1)"
[[ "$exit_code" != "0" ]] || fail "$name exited with 0 without secrets"
grep -q 'APPLICATION FAILED TO START' <<<"$logs" || fail "$name: no startup failure report"
grep -q 'lab.waiting.secrets' <<<"$logs" || fail "$name: failure report does not name the missing secret"
echo "image-smoke: app-waiting (api) without secrets exited with code $exit_code and a startup failure report naming lab.waiting.secrets"

echo "image-smoke: all checks passed"
