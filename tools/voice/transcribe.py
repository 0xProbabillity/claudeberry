"""Speech to text for berryssh's voice input.

The handset records a phrase and runs, over a short SSH connection of its own:

    berryssh-transcribe <content-type> <language> [<rate>]

with the recording on stdin. The answer is one line on stdout, "OK <text>" or
"ERR <what went wrong>": the phone reads stdout and stderr as one stream, and
the marker is how it tells the answer from anything a library printed.

content-type is what the phone's recorder reported ("audio/amr",
"audio/x-wav", "audio/basic", ...); it only matters for raw PCM, which has no
header for ffmpeg to recognise, and rate is the sample rate it was recorded at,
for the same reason. language is a Whisper language code such as "en", or
"auto", which picks whichever of the languages in BERRYSSH_LANGUAGES (English
alone by default; "en,de" for two) the audio is more likely to be: Whisper left
to choose among all of its languages sometimes hears the wrong one in a short
phrase.

Loading the model takes longer than transcribing a phrase, so the first request
starts a small server that keeps it in memory, and later requests go to it. The
server leaves after IDLE_SECONDS without a request, and the memory with it.
"""

import json
import os
import socket
import socketserver
import struct
import subprocess
import sys
import tempfile
import time

MODEL = os.environ.get("BERRYSSH_WHISPER_MODEL", "small")
LANGUAGES = tuple(code.strip() for code in
                  os.environ.get("BERRYSSH_LANGUAGES", "en").split(",") if code.strip())
SOCKET = os.environ.get("BERRYSSH_VOICE_SOCKET", "/tmp/berryssh-voice.sock")
IDLE_SECONDS = 15 * 60


def to_wav(data, content_type, path, rate=8000):
    """Decodes whatever arrived into 16 kHz mono 16-bit WAV for Whisper."""
    source = path + ".in"
    with open(source, "wb") as f:
        f.write(data)
    raw = content_type in ("audio/basic", "audio/pcm", "audio/x-pcm", "audio/l16")
    before = ["-f", "s16le", "-ar", str(rate or 8000), "-ac", "1"] if raw else []
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *before,
               "-i", source, "-ar", "16000", "-ac", "1", "-f", "wav", path]
    result = subprocess.run(command, capture_output=True)
    os.unlink(source)
    if result.returncode != 0:
        raise RuntimeError("could not decode the recording: "
                           + result.stderr.decode(errors="replace").strip())


def load_model():
    from faster_whisper import WhisperModel
    return WhisperModel(MODEL, device="cpu", compute_type="int8",
                        cpu_threads=os.cpu_count() or 4)


def transcribe(model, data, content_type, language, rate=8000):
    from faster_whisper import decode_audio

    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "speech.wav")
        to_wav(data, content_type, wav, rate)
        audio = decode_audio(wav, sampling_rate=16000)
    fixed = language if language in LANGUAGES else None
    if fixed is None and len(LANGUAGES) == 1:
        fixed = LANGUAGES[0]   # nothing to choose between
    # Left to itself, transcribe detects the language from the encoder pass it
    # makes anyway; asking detect_language first would cost a second one.
    segments, info = model.transcribe(audio, language=fixed, beam_size=5,
                                      vad_filter=True)
    if fixed is None and info.language not in LANGUAGES:
        # It heard something else in a short phrase: take the likeliest of
        # the allowed languages and go again.
        scores = dict(info.all_language_probs or [])
        fixed = max(LANGUAGES, key=lambda code: scores.get(code, 0.0))
        segments, _ = model.transcribe(audio, language=fixed, beam_size=5,
                                       vad_filter=True)
    text = " ".join(segment.text.strip() for segment in segments)
    # One line: the phone types it into the terminal, where a newline is Enter.
    return " ".join(text.split())


# The server: one request per connection, a JSON header line, then the audio.

def send_frame(sock, payload):
    sock.sendall(struct.pack(">I", len(payload)) + payload)


def read_frame(sock):
    header = read_exactly(sock, 4)
    return read_exactly(sock, struct.unpack(">I", header)[0])


def read_exactly(sock, n):
    chunks = []
    while n > 0:
        chunk = sock.recv(min(n, 65536))
        if not chunk:
            raise EOFError("connection closed early")
        chunks.append(chunk)
        n -= len(chunk)
    return b"".join(chunks)


def serve():
    model = load_model()
    last = [time.time()]

    class Handler(socketserver.BaseRequestHandler):
        def handle(self):
            last[0] = time.time()
            try:
                header = json.loads(read_frame(self.request))
                data = read_frame(self.request)
                text = transcribe(model, data, header["type"], header["lang"],
                                  header.get("rate", 8000))
                reply = {"text": text}
            except Exception as error:
                reply = {"error": str(error)}
            send_frame(self.request, json.dumps(reply).encode())
            last[0] = time.time()

    if os.path.exists(SOCKET):
        os.unlink(SOCKET)
    old = os.umask(0o077)
    server = socketserver.UnixStreamServer(SOCKET, Handler)
    os.umask(old)
    server.timeout = 30
    try:
        while time.time() - last[0] < IDLE_SECONDS:
            server.handle_request()
    finally:
        server.server_close()
        if os.path.exists(SOCKET):
            os.unlink(SOCKET)


def ask_server(data, content_type, language, rate):
    """The text, or None when no server is listening."""
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    try:
        sock.connect(SOCKET)
    except OSError:
        sock.close()
        return None
    with sock:
        send_frame(sock, json.dumps({"type": content_type, "lang": language,
                                     "rate": rate}).encode())
        send_frame(sock, data)
        reply = json.loads(read_frame(sock))
    if "error" in reply:
        raise RuntimeError(reply["error"])
    return reply["text"]


def start_server():
    subprocess.Popen([sys.executable, os.path.abspath(__file__), "--serve"],
                     stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                     stderr=subprocess.DEVNULL, start_new_session=True)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--serve":
        serve()
        return 0

    content_type = sys.argv[1] if len(sys.argv) > 1 else "audio/amr"
    language = sys.argv[2] if len(sys.argv) > 2 else "auto"
    rate = int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3].isdigit() else 8000

    data = sys.stdin.buffer.read()
    if len(data) < 100:
        print("ERR the recording is empty", flush=True)
        return 2

    text = ask_server(data, content_type, language, rate)
    if text is None:
        # Nobody has the model loaded: load it here for this one, and leave a
        # server behind for the next.
        start_server()
        text = transcribe(load_model(), data, content_type, language, rate)
    print("OK " + text, flush=True)
    return 0


if __name__ == "__main__":
    os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
    try:
        sys.exit(main())
    except Exception as error:  # the phone shows this, so say what it was
        print("ERR " + " ".join(str(error).split()), flush=True)
        sys.exit(1)
