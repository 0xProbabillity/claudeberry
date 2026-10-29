# Troubleshooting

BlackBerry OS 7 is good at failing without saying why. This page lists what goes wrong, what you see, and what
to do about it.

- [Installing](#installing)
- [Connecting](#connecting)
  - [no transport reached](#no-transport-reached)
- [In a session](#in-a-session)
- [Voice input](#voice-input)
- [Building](#building)
- [Reporting a bug](#reporting-a-bug)

## Installing

| You see | Do |
| --- | --- |
| `907 Invalid JAR` when updating | Delete the installed ClaudeBerry (**Options → Device → Application Management → ClaudeBerry → Menu → Delete**) and install again |
| `907 Invalid JAR … missing stack map` | The jar was not preverified. Build with `./build.sh`, which runs ProGuard's preverifier; do not repackage the jar by hand |
| `Invalid JAD` or a size mismatch | The `.jad` and `.jar` come from different builds. Copy both files from the same `out/` again |
| The browser shows the `.jad` as text | It was served with the wrong content type. Use `tools/ota_server.py`, or install over Bluetooth or USB |
| Clicking the `.jad` does nothing | Make sure the `.jar` is in the same folder, then open the `.jad` from the **Files** app rather than from a message or the browser's history |
| The app is not on the home screen | Look in the **Downloads** folder |

## Connecting

### no transport reached

`no transport reached host:22 (last error: …)` means the phone could not open a TCP connection at all. The app
tries direct TCP over Wi-Fi, then direct TCP over any interface, then the phone's default route.

- **On Wi-Fi**: check that Wi-Fi is on and connected (the Wi-Fi icon at the top shows the network name, not only
  bars), and that ClaudeBerry is allowed to use it: **Options → Device → Application Management → ClaudeBerry →
  Menu → Edit Permissions → Connections → Wi-Fi: Allow**.
- **On mobile data**: BlackBerry's own data service (BIS) was shut down, so the phone must use the carrier's
  internet directly, which needs the **APN**. Set it in **Options → Device → Advanced System Settings → TCP IP**:
  tick *APN Settings Enabled* and enter your carrier's APN, user name and password (the same ones as for any
  phone on that network; your carrier's website lists them). Then **Menu → Save** and restart the phone.
- **Internet permission**: **Edit Permissions → Connections → Internet: Allow**.
- **The server**: can your computer reach it on the same port right now? Is a cloud firewall or `ufw` in the way
  (see [server setup](server-setup.md#5-firewall))?

### Other connection errors

| You see | Do |
| --- | --- |
| `Connection refused` right after several quick reconnects | `ufw limit` allows 6 connections per 30 s per address. Wait 30 s, or `sudo ufw allow OpenSSH` |
| Host key prompt on a server you have used before, or a host key mismatch | The server's host key changed. If you reinstalled the server, rebuild with a fresh preset (`tools/make_preset.sh`), or delete and re-add the connection. If you did not, **do not accept**: something is in the way |
| `Permission denied (publickey)` / authentication failed | The phone's public key is not in `~/.ssh/authorized_keys` of that user, or the key in the preset is not the one you copied there. Check with `ssh -i ~/.ssh/id_ed25519_phone you@your-server` from the computer |
| `… must be Ed25519 and have no passphrase` (from `make_preset.sh`) | Make a new key: `ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519_phone` |
| No common algorithm / key exchange failed | The server's `sshd_config` removed `curve25519-sha256`, `ssh-ed25519` or `chacha20-poly1305@openssh.com`; see [server setup](server-setup.md#2-check-sshd) |
| It connects but stops responding after the phone slept or changed networks | **Menu → Reconnect** (or panel → `O`). tmux kept everything |

## In a session

| You see | Do |
| --- | --- |
| Garbled characters, a half-drawn screen | Ctrl+L: panel → `W`. In Claude Code you can also resize with `Font +` / `Font −`, which forces a redraw |
| Only 16 colours in Claude Code | **Settings → Terminal type** must be `xterm-256color`. Inside tmux the app sets `screen-256color`; if you have your own `~/.tmux.conf`, do not override `default-terminal` with a plain `screen` |
| Esc takes half a second | Your `~/.tmux.conf` sets a long `escape-time`; set `set -s escape-time 10` |
| A plain shell instead of tmux, and the session is gone after a reconnect | tmux is not installed on the server, so the app fell back to your login shell. `sudo apt install tmux` |
| A tmux status line appears | Your `~/.tmux.conf` turns it on; the app turns it off only when it starts the tmux server |
| Swiping scrolls the shell into `[0/123]` | That is tmux copy mode, the scrollback. `q` or `Esc` leaves it |
| A key types the wrong thing | **Menu → Key codes** shows each key's code at the bottom; include it in an issue |
| Claude Code asks you to log in | Log in once from a computer over SSH; see [server setup](server-setup.md#4-claude-code) |
| Text is too small or too big | `Font +` / `Font −` in the menu, or panel → `F` / `V`. For all connections: **Settings → Font size** |

## Voice input

| You see | Do |
| --- | --- |
| `berryssh-transcribe is not installed on the server` | Install the server half: [voice.md](voice.md#install-the-server-half). Check with `ssh you@your-server which berryssh-transcribe` |
| `voice: microphone permission was refused` or `voice: could not record: …` | Allow recording: **Edit Permissions → Interactions → Recording: Allow**, and make sure no call is using the microphone |
| `voice: nothing heard` | Whisper found no speech. Hold the phone closer and speak after the key turns red |
| `voice needs a direct connection, not a bridge` | Voice works only for connections made directly over SSH, not through the WebSocket bridge |
| `could not log in for voice` | The second, short SSH login was refused. Usually `ufw limit` after many quick connections: wait 30 s |
| `ERR could not decode the recording` | `ffmpeg` is missing on the server, or too old to read AMR. `sudo apt install ffmpeg` |
| `ERR the recording is empty` | The recording was shorter than a moment |
| The first prompt after a break takes 10–20 s | Normal: the model is loading. It stays warm for 15 minutes after each prompt |
| It mishears words | Speak after the key turns red, close to the phone; on a server with RAM to spare, try the `medium` model ([voice.md](voice.md#choosing-a-model)) |
| `ERR … No module named faster_whisper` | The virtualenv is broken: run `install.sh` again |
| Out of memory on a 512 MB server | Use the `base` model ([voice.md](voice.md#choosing-a-model)) or add swap |

To see exactly what the phone sees, run the same command from your computer:

```sh
ssh you@your-server 'berryssh-transcribe audio/x-wav auto' < some-recording.wav
```

## Building

| You see | Do |
| --- | --- |
| `the client did not compile under CLDC constraints` with `javac: No such file` | `JAVA8_HOME` points at the wrong folder. It must contain `bin/javac`; for Zulu on macOS that is `…/zulu-8.jdk/Contents/Home` or the unpacked folder's `Contents/Home` |
| `class file has wrong version` or `major version: 52` | A newer JDK compiled it. Use a JDK 8 through `JAVA8_HOME`, or Docker |
| `Cannot connect to the Docker daemon` | Start Docker, or set `JAVA8_HOME` to build without it |
| `curl: (22)` in `lib/fetch.sh` | Maven Central was unreachable; run it again |
| `no ed25519 host key from host:22` | `ssh-keyscan` could not reach the server from your computer, or the server has no Ed25519 host key |

## Reporting a bug

Open an issue with the phone model and OS version (**Options → Device → About Device Versions**), the ClaudeBerry
version (**Menu → About**), what you did and what you saw. **Never attach a `.jar` built with a preset**, a
private key, or a screenshot showing your server's address.
