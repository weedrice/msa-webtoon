#!/usr/bin/env bash
set -euo pipefail

# Helper to run Gradle with a specific JDK without changing system JAVA_HOME.
# Usage:
#   ./scripts/gradlew-java.sh /usr/lib/jvm/temurin-21 --version
#   ./scripts/gradlew-java.sh "" clean test jacocoTestReport

JAVA_HOME_HINT=${1:-}
shift || true

resolve_java_home() {
  local hint="$1"
  if [[ -n "$hint" && -d "$hint" ]]; then echo "$hint"; return; fi
  for c in \
    "/usr/lib/jvm/temurin-21" \
    "/usr/lib/jvm/java-21-openjdk" \
    "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home" \
    "/usr/lib/jvm/temurin-17" \
    "/usr/lib/jvm/java-17-openjdk" \
    "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"; do
    [[ -d "$c" ]] && { echo "$c"; return; }
  done
  echo ""; return 0
}

JH="$(resolve_java_home "$JAVA_HOME_HINT")"
if [[ -z "$JH" ]]; then
  echo "Could not find a JDK 17+ installation. Pass path as first arg." >&2
  exit 1
fi

export GRADLE_OPTS="-Dorg.gradle.java.home=$JH ${GRADLE_OPTS:-}"
echo "Using JDK: $JH"

"$(dirname "$0")/../gradlew" "$@"

