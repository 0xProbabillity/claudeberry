# Server setup

ClaudeBerry needs one thing from your server: an SSH login with a key. Everything else on this page makes that
login comfortable from a phone: a `tmux` session to come back to, Claude Code to talk to, and a firewall that
does not get in the way.

Any Linux machine you can reach over the network works: a VPS, a home server, a Raspberry Pi. The examples use
Debian or Ubuntu; other distributions need only a different package manager.

- [1. A key for the phone](#1-a-key-for-the-phone)
- [2. Check sshd](#2-check-sshd)
- [3. tmux](#3-tmux)
- [4. Claude Code](#4-claude-code)
- [5. Firewall](#5-firewall)
- [6. Note the host key](#6-note-the-host-key)
- [What ClaudeBerry runs on your server](#what-claudeberry-runs-on-your-server)

## 1. A key for the phone

Give the phone a key of its own, so you can revoke it without touching your other machines. It must be
**Ed25519** and have **no passphrase**: the phone has nowhere to type one in, and the key travels inside your
own build of the app (see [the preset](install.md#2-bake-in-your-server-the-preset)).

On your computer:

```sh
ssh-keygen -t ed25519 -N "" -C "blackberry" -f ~/.ssh/id_ed25519_phone
ssh-copy-id -i ~/.ssh/id_ed25519_phone.pub you@your-server
```

Check that it works before going further:

```sh
ssh -i ~/.ssh/id_ed25519_phone -o IdentitiesOnly=yes you@your-server 'echo ok'
```

> [!TIP]
> Log in as a normal user rather than `root`. Claude Code runs whatever it is allowed to run, and a user account
> keeps a mistake from reaching the whole system. Give that user `sudo` if you need it.

To revoke the phone later, delete its line (the one ending in `blackberry`) from `~/.ssh/authorized_keys` on the
server.

## 2. Check sshd

ClaudeBerry speaks the algorithms OpenSSH has offered by default since 6.5 (2014): `curve25519-sha256`,
`ssh-ed25519` and `chacha20-poly1305@openssh.com`. A stock server needs no change.

```sh
ssh -V                                   # OpenSSH_6.5 or newer
sudo sshd -T | grep -E '^(kexalgorithms|hostkeyalgorithms|ciphers)'
```

The three lists must contain `curve25519-sha256` (or `curve25519-sha256@libssh.org`), `ssh-ed25519` and
`chacha20-poly1305@openssh.com`. If a hardening guide removed one of them, put it back in
`/etc/ssh/sshd_config` and `sudo systemctl reload ssh`.

The server also needs an Ed25519 host key, which every current distribution generates:

```sh
ls /etc/ssh/ssh_host_ed25519_key.pub
```

Once the key login works, you can turn passwords off for good:

```sh
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo systemctl reload ssh
```

## 3. tmux

```sh
sudo apt install tmux
```

That is all. ClaudeBerry starts or reattaches the session itself (see
[below](#what-claudeberry-runs-on-your-server)), so you need no `~/.tmux.conf`. Without `tmux` the app still works
and opens your login shell, but a dropped connection then takes the shell, and anything running in it, with it.

A session started from the phone is an ordinary `tmux` session. From your computer you can join the same one and
watch the phone type:

```sh
ssh you@your-server -t tmux attach -t bb
```

## 4. Claude Code

Install Claude Code on the server, as the user the phone logs in as:

```sh
curl -fsSL https://claude.ai/install.sh | bash
```

(or `npm install -g @anthropic-ai/claude-code` if you prefer npm; see
[the Claude Code docs](https://code.claude.com/docs/en/setup) for other options).

**Log in from a computer, not from the phone.** The first `claude` run prints a sign-in link to open in a browser
and asks for the code it gives back, which is easy from a laptop and painful on a 2011 browser:

```sh
ssh you@your-server
claude            # follow the sign-in link, paste the code, then /exit
```

The login is stored on the server, so the phone is signed in from then on. An API key works too: put
`export ANTHROPIC_API_KEY=...` in the server's `~/.bashrc` (or `~/.profile`). While you are on the computer,
pick Claude Code's dark theme in `/config`: it matches the app's Claude theme.

Then, from the phone, open your project and start it:

```sh
cd ~/projects/my-app
claude
```

The same works for any other terminal agent or tool (OpenCode, Aider, Codex CLI, `vim`, `htop`…).

## 5. Firewall

If the server runs `ufw`, allow SSH:

```sh
sudo ufw allow OpenSSH        # or: sudo ufw limit OpenSSH
```

`ufw limit` refuses an address that opens more than 6 connections in 30 seconds. ClaudeBerry opens one
connection per session and one short one per voice prompt, so normal use stays well under that, but a burst of
reconnects plus voice prompts can hit it. If you see the connection refused right after a few quick retries, wait
half a minute, or use `allow` instead of `limit`.

If your hosting provider has its own firewall (security groups, cloud firewall), allow TCP 22 (or your SSH port)
there too. Mobile networks hand out changing addresses, so allowing only "your" IP will lock the phone out.

## 6. Note the host key

ClaudeBerry pins the server's host key. With a preset, `tools/make_preset.sh` fetches it for you and prints its
fingerprint; compare it with the one the server reports for itself, over a session you already trust:

```sh
ssh you@your-server ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

Without a preset, the app shows the fingerprint on the first connection and asks you to accept it. Compare it
with the same command.

You are ready to [build and install the app](install.md). For voice input, continue with
[voice.md](voice.md) once the app works.

## What ClaudeBerry runs on your server

Nothing is installed on the server by the app, and nothing runs there except what a normal SSH login runs.
When **Settings → Session** is *tmux* (the default), each connection asks the server to execute:

```sh
command -v tmux >/dev/null 2>&1 && exec tmux start-server \
  \; set -g default-terminal screen-256color \
  \; set -g status off \
  \; set -s escape-time 10 \
  \; set -g mouse on \
  \; new-session -A -s bb \
  || exec "$SHELL" -l
```

| Option | Why |
| --- | --- |
| `default-terminal screen-256color` | 256 colours inside tmux; otherwise Claude Code drops to 16 |
| `status off` | The app draws its own status line; tmux's would cost a row |
| `escape-time 10` | tmux waits 500 ms after Esc by default, which makes Esc in Claude Code feel stuck |
| `mouse on` | Taps reach programs as clicks, swipes scroll |
| `new-session -A -s bb` | Attach to the session `bb`, creating it the first time |

These are `-g` (global) options of the tmux server, so if you also use tmux on that account from elsewhere, you
will see the status line off and the mouse on there too. Put your own preferences back in `~/.tmux.conf` if that
matters; the app sets these only when it starts the tmux server.

With **Settings → Session** set to *Plain shell* the app asks for an ordinary interactive shell instead.

For voice input the app also runs `berryssh-transcribe <type> <language> <rate>` over a separate, short
connection; see [voice.md](voice.md).
