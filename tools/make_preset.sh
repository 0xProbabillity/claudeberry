#!/usr/bin/env bash
# Bake one connection into the next build, so the handset needs no typing.
#
#   tools/make_preset.sh root@203.0.113.7 ~/.ssh/id_ed25519_phone [port]
#   ./build.sh
#
# Writes ssh/res/preset/, which build.sh packs into the jar. The host key is
# fetched with ssh-keyscan and its fingerprint printed: compare it against
# `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the server.
#
# The jar this produces holds a private key. Copy it onto the phone over USB;
# never serve it. `rm -rf ssh/res/preset` to build a plain jar again.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ $# -lt 2 ]; then
    echo "usage: $0 user@host private-key [port]" >&2
    exit 1
fi

TARGET=$1
KEY=$2
PORT=${3:-22}
USER_NAME=${TARGET%@*}
HOST=${TARGET#*@}

if ! grep -q 'BEGIN OPENSSH PRIVATE KEY' "$KEY"; then
    echo "$KEY is not an OpenSSH private key" >&2
    exit 1
fi
if ! ssh-keygen -y -P '' -f "$KEY" 2>/dev/null | grep -q '^ssh-ed25519 '; then
    echo "$KEY must be Ed25519 and have no passphrase" >&2
    exit 1
fi

HOSTKEY=$(ssh-keyscan -t ed25519 -p "$PORT" "$HOST" 2>/dev/null | awk '$2 == "ssh-ed25519" { print $3; exit }')
if [ -z "$HOSTKEY" ]; then
    echo "no ed25519 host key from $HOST:$PORT" >&2
    exit 1
fi

mkdir -p ssh/res/preset
cp "$KEY" ssh/res/preset/key
cat > ssh/res/preset/preset.txt <<EOF
name=VPS
host=$HOST
port=$PORT
user=$USER_NAME
font=0
autoconnect=yes
hostkey=$HOSTKEY
EOF

echo "preset: $USER_NAME@$HOST:$PORT"
echo "ssh-ed25519 $HOSTKEY" | ssh-keygen -lf -
