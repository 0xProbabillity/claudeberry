# Notice

ClaudeBerry is a modified version of **berryssh**, an SSH client for BlackBerry OS 7
by Mert Cobanov, <https://github.com/cobanov/berryssh>, imported at commit
`137e2cf` (release 0.8.1). berryssh in turn reuses code from **BBSSH** by
Marc Paradise, <https://github.com/marcparadise/bbssh>: the `VT320` terminal
emulator (originally from JTA by Matthias L. Jugel and Marcus Meissner) and the
`BitmapFont` renderer (derived from Roar Lauritzsen's LCDFont). Copyright in that
code remains with its authors; their notices are kept in the files.

The whole work is distributed under the GNU General Public License, version 2
or (at your option) any later version. See [LICENSE](LICENSE).

## Changes made in ClaudeBerry

October 2026, by 0xProbability. The first commit of this repository is berryssh
0.8.1 as published, without the Cloudflare Worker and deploy notes for the
upstream author's download host; every later commit is a change made here.

- **Preset** (`device/Preset.java`, `tools/make_preset.sh`): one connection,
  with its Ed25519 key and pinned host key, built into the jar; connects on
  launch.
- **Terminal** (`terminal/VT320.java`, `device/TerminalCanvas.java`,
  `device/RowDiff.java`, `terminal/BitmapFont.java`, `tools/MakeAtlas.java`):
  256-colour and true-colour SGR, colon colours, dim and underline, REP; unknown
  control sequences (DECRQM, DA2, XTVERSION, cursor shape, kitty queries,
  APC/PM/SOS) consumed instead of printed; SGR and X10 mouse reporting for taps
  and swipes; Claude Code's glyphs, box drawing, block elements and Cyrillic in
  the font atlases; per-row picture cache and smooth scrolling; UTF-8 split
  across reads kept intact.
- **Keys and touch** (`device/Keys.java`, `device/Keyboard.java`,
  `device/TerminalCanvas.java`): key bar, quick panel with one-letter shortcuts,
  symbols page, touch on the terminal, lists and keys, live font size.
- **Sessions** (`device/BerrysshMIDlet.java`, `device/Settings.java`): start in
  the tmux session `bb`; SSH keep-alive every 30 s; sockets and their streams
  closed on disconnect.
- **Voice input** (`device/VoiceRecorder.java`, `device/VoiceFormats.java`,
  `device/VoiceReply.java`, `tools/voice/`): recording through MMAPI and
  transcription by faster-whisper on the user's server over a second SSH
  connection.
- **Look** (`device/Theme.java`, `device/Chrome.java`, `device/ListScreen.java`,
  `device/AboutScreen.java`, `device/Berry.java`, `tools/MakeIcon.java`):
  themes, a Settings screen (`device/RecordStoreSettings.java`), the home
  banner, an About screen, the ClaudeBerry name, logo and icon.
- **Build and tools** (`build.sh`, `spike2/run.sh`, `lib/fetch.sh`,
  `tools/RenderCapture.java`): a local JDK 8 instead of Docker when
  `JAVA8_HOME` is set, MMAPI stubs, jar named `claudeberry`, a host-side
  renderer for screenshots; tests for all of the above in `spike2/src`.
- **Documentation**: this README and `docs/`.
