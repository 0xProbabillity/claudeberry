#!/usr/bin/env bash
# Build the ClaudeBerry MIDlet: javac (JDK 8, targeting 1.3) -> preverify -> jar -> jad.
#
# Compiles with JDK 8 (local, or in a container) because CLDC 1.1 wants class
# file version 47 and a newer JDK cannot emit anything that old. The API stubs are microemu's
# repackaged cldcapi11/midpapi20, so no BlackBerry JDE is involved anywhere —
# and since nothing here touches a RIM API, the result needs no code signature.
#
#   ./build.sh            -> out/claudeberry.jar + out/claudeberry.jad
set -euo pipefail

cd "$(dirname "$0")"

# The name the handset shows; the jar and descriptor are named in lower case.
# The code underneath is berryssh's, so its package keeps that name.
NAME=ClaudeBerry
FILE=claudeberry
VENDOR="ClaudeBerry"
VERSION=0.12.2
MIDLET_CLASS=berryssh.device.BerrysshMIDlet

OUT=out
JAR="$OUT/$FILE.jar"
JAD="$OUT/$FILE.jad"

# JDK 8 is the newest javac that still emits class file version 47. Set
# JAVA8_HOME to use a local one; otherwise it runs in a container, with a
# throwaway docker config so a Docker Desktop credential helper is not
# consulted.
if [ -n "${JAVA8_HOME:-}" ]; then
    jdk8() { "$JAVA8_HOME/bin/$1" "${@:2}"; }
else
    # OrbStack's socket when it is there and nothing else was asked for.
    if [ -z "${DOCKER_HOST:-}" ] && [ -S "$HOME/.orbstack/run/docker.sock" ]; then
        export DOCKER_HOST="unix://$HOME/.orbstack/run/docker.sock"
    fi
    DOCKER_CONFIG="$(mktemp -d)"; export DOCKER_CONFIG
    printf '{}' > "$DOCKER_CONFIG/config.json"
    trap 'rm -rf "$DOCKER_CONFIG"' EXIT
    jdk8() { docker run --rm -v "$PWD:/work" -w /work eclipse-temurin:8-jdk "$@"; }
fi

rm -rf "$OUT" classes classes-pv
mkdir -p "$OUT" classes

echo "==> compiling (source/target 1.3)"
# javac's exit status decides this, not the presence of some class file: a
# partial failure still leaves earlier packages on disk.
if ! output=$(jdk8 javac -source 1.3 -target 1.3 -nowarn \
          -bootclasspath lib/cldcapi11.jar:lib/midpapi20.jar:lib/mmapi.jar \
          -d classes $(find ssh/src -name '*.java' | tr '\n' ' ') 2>&1); then
    echo "$output" >&2
    echo "the client did not compile under CLDC constraints" >&2
    exit 1
fi
echo "$output" | grep -v 'obsolete\|deprecat\|warning' || true

echo "==> class file version"
jdk8 javap -verbose -cp classes "$MIDLET_CLASS" 2>/dev/null | grep -m1 'major version' || true

# CLDC's verifier needs StackMap attributes baked in ahead of time; without them
# the device rejects the suite with "907 Invalid JAR / missing stack map".
# ProGuard's -microedition mode emits them, so no native preverifier is needed.
echo "==> preverifying (CLDC StackMap)"
rm -rf classes-pv && mkdir -p classes-pv
java -cp lib/proguard.jar proguard.ProGuard \
    -injars classes -outjars classes-pv \
    -libraryjars lib/cldcapi11.jar -libraryjars lib/midpapi20.jar -libraryjars lib/mmapi.jar \
    -dontshrink -dontobfuscate -dontoptimize -microedition \
    -keep 'public class * extends javax.microedition.midlet.MIDlet' \
    > "$OUT/proguard.log" 2>&1

if [ -z "$(find classes-pv -name '*.class' -print -quit)" ]; then
    echo "preverification produced no classes; see $OUT/proguard.log" >&2
    exit 1
fi

echo "==> packaging jar"
cat > "$OUT/manifest.mf" <<EOF
MIDlet-Name: $NAME
MIDlet-Version: $VERSION
MIDlet-Vendor: $VENDOR
MIDlet-1: $NAME,/icon.png,$MIDLET_CLASS
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
EOF

# The font atlases ride in the jar and are loaded with getResourceAsStream.
jdk8 jar cfm "$JAR" "$OUT/manifest.mf" -C classes-pv . -C ssh/res .

JAR_SIZE=$(stat -f%z "$JAR" 2>/dev/null || stat -c%s "$JAR")

# The descriptor must repeat the MIDlet-* attributes and state the jar's exact
# size; a mismatch is one of the ways an OTA install fails with no useful error.
cat > "$JAD" <<EOF
MIDlet-Name: $NAME
MIDlet-Version: $VERSION
MIDlet-Vendor: $VENDOR
MIDlet-1: $NAME,/icon.png,$MIDLET_CLASS
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
MIDlet-Jar-URL: $FILE.jar
MIDlet-Jar-Size: $JAR_SIZE
EOF

rm -f "$OUT/manifest.mf"
echo "==> built $JAR ($JAR_SIZE bytes) + $JAD"
