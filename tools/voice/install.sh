#!/usr/bin/env bash
# Install the server half of berryssh's voice input.
#
#   scp tools/voice/transcribe.py tools/voice/install.sh server:/tmp/
#   ssh server 'bash /tmp/install.sh'
#
# Puts faster-whisper in its own virtualenv under /opt/berryssh-voice, so
# nothing system-wide changes, and a `berryssh-transcribe` command on PATH,
# which is what the handset runs. Needs ffmpeg and uv. The first run downloads
# the Whisper model (small: about 500 MB) into ~/.cache/huggingface.
set -euo pipefail

DIR=/opt/berryssh-voice
HERE="$(cd "$(dirname "$0")" && pwd)"

command -v ffmpeg >/dev/null || { echo "ffmpeg is needed" >&2; exit 1; }
command -v uv >/dev/null || { echo "uv is needed: https://docs.astral.sh/uv/" >&2; exit 1; }

mkdir -p "$DIR"
[ -x "$DIR/venv/bin/python" ] || uv venv -q "$DIR/venv" --python 3.10
uv pip install -q --python "$DIR/venv/bin/python" faster-whisper
install -m 644 "$HERE/transcribe.py" "$DIR/transcribe.py"

cat > /usr/local/bin/berryssh-transcribe <<EOF
#!/bin/sh
exec $DIR/venv/bin/python $DIR/transcribe.py "\$@"
EOF
chmod 755 /usr/local/bin/berryssh-transcribe

echo "installed: berryssh-transcribe"
