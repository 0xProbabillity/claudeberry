#!/usr/bin/env bash
# Compile the client sources under CLDC 1.1 constraints, then run the test
# vectors on the host JVM.
#
# The two halves are the point: compiling at -source 1.3 against the CLDC
# bootclasspath proves the code will run on the device, while executing on the
# host proves it is correct — without a single device round-trip.
set -euo pipefail

cd "$(dirname "$0")"

# As in build.sh: a local JDK 8 when JAVA8_HOME is set, a container otherwise.
if [ -n "${JAVA8_HOME:-}" ]; then
    cldc_javac() { (cd .. && "$JAVA8_HOME/bin/javac" "$@"); }
    ROOT=.
else
    # OrbStack's socket when it is there and nothing else was asked for.
    if [ -z "${DOCKER_HOST:-}" ] && [ -S "$HOME/.orbstack/run/docker.sock" ]; then
        export DOCKER_HOST="unix://$HOME/.orbstack/run/docker.sock"
    fi
    DOCKER_CONFIG="$(mktemp -d)"; export DOCKER_CONFIG
    printf '{}' > "$DOCKER_CONFIG/config.json"
    trap 'rm -rf "$DOCKER_CONFIG"' EXIT
    cldc_javac() { docker run --rm -v "$PWD/..:/client" -w /client eclipse-temurin:8-jdk javac "$@"; }
    ROOT=/client
fi

rm -rf classes
mkdir -p classes

echo "==> compiling the client under CLDC 1.1 / -source 1.3"
# javac's exit status is what decides this, not the presence of some class file:
# a partial failure still leaves the earlier packages on disk, so counting
# classes would let a package that does not build on the device pass unnoticed.
# The filtering only applies to a successful run, where it is dropping the
# -source 1.3 obsolescence notices.
if ! cldc_output=$(cldc_javac -source 1.3 -target 1.3 -nowarn \
          -bootclasspath $ROOT/lib/cldcapi11.jar:$ROOT/lib/midpapi20.jar:$ROOT/lib/mmapi.jar \
          -d $ROOT/spike2/classes \
          $(cd .. && find ssh/src -name '*.java' | sed "s|^|$ROOT/|") \
    2>&1); then
    echo "$cldc_output" >&2
    echo "the client did not compile under CLDC constraints" >&2
    exit 1
fi
echo "$cldc_output" | grep -v 'obsolete\|deprecat\|warning' || true

echo "==> compiling tests (host JDK, sees the CLDC-built classes)"
javac -nowarn -cp classes -d classes src/*.java

echo "==> running crypto vectors on host JVM"
java -cp classes CryptoTests

echo "==> running transport vectors on host JVM"
java -cp classes TransportTests

echo "==> running terminal vectors on host JVM"
java -cp classes TerminalTests
