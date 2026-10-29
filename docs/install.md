# Building and installing

ClaudeBerry is distributed as source: you build it yourself, usually with your own server baked in, and copy it
to your phone. It takes about five minutes once the server is [set up](server-setup.md).

- [Building](#building)
  - [1. Tools](#1-tools)
  - [2. Bake in your server: the preset](#2-bake-in-your-server-the-preset)
  - [3. Build](#3-build)
- [Install on the phone](#install-on-the-phone)
  - [Over Bluetooth](#over-bluetooth)
  - [Over USB](#over-usb)
  - [Over Wi-Fi, without a preset](#over-wi-fi-without-a-preset)
  - [Permissions](#permissions)
- [First launch](#first-launch)
- [Adding servers on the phone](#adding-servers-on-the-phone)
- [Updating](#updating)

## Building

### 1. Tools

| | |
| --- | --- |
| **JDK 8** | CLDC 1.1 wants class file version 47, which no newer `javac` can emit. On Apple Silicon use [Azul Zulu 8](https://www.azul.com/downloads/?version=java-8-lts&os=macos&architecture=arm-64-bit&package=jdk) (unpack it anywhere; it does not have to be your default Java). On Linux, `sudo apt install openjdk-8-jdk-headless` or Zulu 8. Or skip it and have Docker installed: the build then compiles in an `eclipse-temurin:8-jdk` container. |
| **Java 8 or newer on PATH** | Runs ProGuard, which preverifies the classes. Any JDK or JRE will do, including the JDK 8 above. |
| **curl, unzip, zip** | `lib/fetch.sh` downloads the build dependencies. |
| **ssh-keyscan, ssh-keygen** | Used by `tools/make_preset.sh`; part of OpenSSH. |

Clone the repository and fetch the dependencies (the MIDP/CLDC and MMAPI API stubs from microemu and ProGuard,
all from Maven Central; none of them are committed):

```sh
git clone https://github.com/0xProbabillity/claudeberry.git
cd claudeberry
lib/fetch.sh
```

### 2. Bake in your server: the preset

A *preset* is one connection built into the app: host, port, user, the phone's private key and the server's
pinned host key. A build with a preset connects on launch with nothing to type, which on a phone keyboard is most
of the point.

```sh
tools/make_preset.sh you@your-server ~/.ssh/id_ed25519_phone 22 @your_handle
```

| Argument | |
| --- | --- |
| `you@your-server` | The login: user and host name or IP address |
| `~/.ssh/id_ed25519_phone` | The phone's private key from [server setup](server-setup.md#1-a-key-for-the-phone): Ed25519, no passphrase |
| `22` | SSH port (optional, default 22) |
| `@your_handle` | Optional badge shown on the home screen and the About screen: your name or handle |

The script fetches the server's Ed25519 host key and prints its fingerprint. **Compare it** with
`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` run on the server: that check is what makes pinning it safe.

It writes `ssh/res/preset/preset.txt` and `ssh/res/preset/key`. The folder is in `.gitignore`. You can edit
`preset.txt` by hand:

```ini
name=VPS            # the name in the connection list
host=203.0.113.7
port=22
user=you
font=0              # 0 = 8x14, the size for 80 columns on a 640x480 screen
autoconnect=yes     # connect on launch
hostkey=AAAAC3Nz…   # the server's Ed25519 public key, base64
badge=@your_handle
```

> [!WARNING]
> **A jar built with a preset contains your private key.** Anyone who has the file can log in to your server as
> that user. Keep it to your own phone: copy it over Bluetooth or USB, never upload it, never attach it to an
> issue, never serve it over Wi-Fi. If a copy gets out, remove the key's line from `~/.ssh/authorized_keys` on
> the server.
>
> To build a plain jar again: `rm -rf ssh/res/preset`.

Skip this step entirely for a plain build, and add servers [on the phone](#adding-servers-on-the-phone) instead.

### 3. Build

```sh
JAVA8_HOME=/path/to/zulu8/Contents/Home ./build.sh     # macOS, Zulu
JAVA8_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./build.sh # Linux
./build.sh                                             # no JAVA8_HOME: compiles in Docker
```

```
==> compiling (source/target 1.3)
==> class file version
  major version: 47
==> preverifying (CLDC StackMap)
==> packaging jar
==> built out/claudeberry.jar (373142 bytes) + out/claudeberry.jad
```

You get two files in `out/`: `claudeberry.jad`, the descriptor the phone reads first, and `claudeberry.jar`, the
app. Keep them together.

To check your build before it goes to the phone, run the test vectors (crypto, SSH transport, terminal) on your
computer:

```sh
JAVA8_HOME=/path/to/jdk8 spike2/run.sh      # 332 checks, no network needed
```

## Install on the phone

Copy **both** files to the same folder on the phone, then open the `.jad`.

### Over Bluetooth

1. On the phone, turn Bluetooth on (**Manage Connections → Bluetooth**) and pair it with your computer once
   (**Options → Networks and Connections → Bluetooth Connections → Add New Device**).
2. Send `out/claudeberry.jad` and `out/claudeberry.jar` from the computer:
   - **macOS**: open *Bluetooth File Exchange* (in `/Applications/Utilities`), or from the repository:
     ```sh
     open -a "Bluetooth File Exchange" out/claudeberry.jad out/claudeberry.jar
     ```
     and pick the phone.
   - **Windows**: right-click each file → *Send to* → *Bluetooth device*.
   - **Linux**: `bluetooth-sendto out/claudeberry.jad out/claudeberry.jar`, or the Bluetooth applet.
3. Accept both files on the phone. They land in the received-files folder (usually *Device Memory → home → user →
   documents*, or *Media Card → BlackBerry → documents*).
4. Open the **Files** app (on some builds **Media → Menu → Explore**), go to that folder and click
   **claudeberry.jad**, then **Download**. The phone installs the jar next to it.

### Over USB

1. Connect the phone with a USB cable and choose **Mass Storage** (or turn it on in **Options → Device → Storage →
   Mass Storage Mode Support**). The media card appears on the computer as a drive.
2. Copy `claudeberry.jad` and `claudeberry.jar` to `BlackBerry/documents/` on the card.
3. Eject the drive, then on the phone open **Files → Media Card → BlackBerry → documents → claudeberry.jad** and
   click **Download**.

### Over Wi-Fi, without a preset

A plain build (no preset) can be installed over the air from your computer. Never do this with a preset build:
anyone on the network could download your key.

```sh
rm -rf ssh/res/preset && ./build.sh
python3 tools/ota_server.py out
```

The script prints a URL like `http://192.168.1.20:8080/claudeberry.jad`. Open it in the phone's own browser, on
the same Wi-Fi, and click **Download**. Plain HTTP is deliberate: the OS 7 browser cannot validate any certificate
issued today. The server sends the `.jad` as `text/vnd.sun.j2me.app-descriptor` and the jar as
`application/java-archive`; a generic file server would make the browser show the descriptor as text instead.

### Permissions

On the first connection the phone asks whether ClaudeBerry may use the network, and on the first voice prompt
whether it may record. Answer **Allow** (and tick *Don't ask again*). To set them in advance, or fix a "Deny"
answer:

**Options → Device → Application Management → ClaudeBerry → Menu → Edit Permissions**

| Permission | Set to | Needed for |
| --- | --- | --- |
| Connections → **Internet** | Allow | Every connection |
| Connections → **Wi-Fi** | Allow | Connections over Wi-Fi |
| Interactions → **Recording** | Allow | Voice input |

## First launch

Open **ClaudeBerry** from the home screen (it may be in the **Downloads** folder).

- **With a preset**, it connects straight away. You see the server's login messages, then an empty `tmux` session.
  Type `claude` and press Enter.
- **Without a preset**, you see the home screen with **New connection…**; see the next section.

The first launch after installing can take a few seconds while the phone verifies the jar.

If it does not connect, see [troubleshooting](troubleshooting.md#no-transport-reached).

## Adding servers on the phone

On the home screen, click **New connection…** (or **Menu → New**) and fill in:

| Field | |
| --- | --- |
| Name | What the list shows |
| Host, Port | Server address and SSH port |
| User | The login |
| Password | For password login. Kept in memory for the session only, unless *Save password* is yes |
| Private key | An OpenSSH Ed25519 private key without a passphrase, pasted from the clipboard |
| Terminal size | Font size for this connection: 6×9 up to 12×21 |
| WebSocket path, Bridge key, Bridge target | Only for reaching a server through berryssh's [WebSocket bridge](../tools/wsbridge-install.md); leave empty |

**Menu → Save**, then click the connection. The first time, the app shows the server's host key fingerprint:
compare it with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the server and **Accept**.

Typing or pasting a key on the phone is fiddly; the [preset](#2-bake-in-your-server-the-preset) exists so you do
not have to.

## Updating

Build the new version the same way (the preset stays in `ssh/res/preset/` between builds), copy both files to the
phone and open the new `.jad`. The phone offers to replace the installed version and keeps your settings and saved
connections.

If the update fails with `907 Invalid JAR` or similar, delete the old version first
(**Options → Device → Application Management → ClaudeBerry → Menu → Delete**) and install again. Settings and
connections other than the preset are lost then.

> [!NOTE]
> ClaudeBerry is a separate app from berryssh: the name on the phone is different, so installing it does not
> replace berryssh and does not see berryssh's saved connections.
