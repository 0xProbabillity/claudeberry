package berryssh.device;

import java.io.IOException;

import berryssh.protocol.SshException;
import berryssh.protocol.WireReader;
import berryssh.protocol.WireWriter;

/**
 * How sessions look and start, for every connection at once.
 *
 * Each field is an index into one of the tables below or a switch, so the
 * settings screen can step through choices with the trackpad rather than ask
 * for a hex value on a phone keyboard. Free of MIDP, like {@link Profile}.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Settings {

    /** Where settings are kept. Implemented over RMS on the device. */
    public interface Store {
        Settings load() throws IOException;

        void save(Settings settings) throws IOException;
    }

    /**
     * Format 1 had no smooth-scroll field, format 2 no key bar or tmux; the
     * missing ones read as on.
     */
    private static final int FORMAT = 3;
    private static final int FORMAT_WITHOUT_SMOOTH = 1;
    private static final int FORMAT_WITHOUT_SESSION = 2;

    public static final String[] TEXT_NAMES = {
        "From theme", "White", "Light grey", "Grey", "Claude orange",
        "Amber", "Green", "Cyan", "Black" };
    public static final int[] TEXT_COLOURS = {
        -1, 0xffffff, 0xe5e5e5, 0xaaaaaa, 0xd97757,
        0xffb000, 0x33ff66, 0x66d9ef, 0x000000 };

    public static final String[] BACKGROUND_NAMES = {
        "From theme", "Black", "Claude dark", "Graphite", "Navy",
        "Dark green", "Dark brown", "White" };
    public static final int[] BACKGROUND_COLOURS = {
        -1, 0x000000, 0x171717, 0x282828, 0x0d1b2a,
        0x001a08, 0x1e1408, 0xffffff };

    /** Profile font indices, smallest cell first, for stepping up and down. */
    public static final int[] SIZE_ORDER = { 2, 1, 0, 3, 4 };

    /**
     * What the server is told the terminal is. xterm-256color is what gets
     * colour out of Claude Code and most modern tools; vt320 is what berryssh
     * always said, kept for anything that misbehaves.
     */
    public static final String[] TERMINAL_TYPES = { "xterm-256color", "vt320" };

    /**
     * What a tmux session start runs instead of a bare shell.
     *
     * Every connection lands in the same session, "bb", so a dropped mobile
     * link costs nothing: reconnecting is back where you were, and nothing is
     * started twice. A server without tmux gets its login shell.
     */
    public static final String TMUX_COMMAND =
        "command -v tmux >/dev/null 2>&1 && exec tmux start-server"
        // 256 colours inside, where tmux would otherwise say "screen" and
        // Claude Code would drop to sixteen.
        + " \\; set -g default-terminal screen-256color"
        // berryssh draws its own status line; tmux's would cost a row.
        + " \\; set -g status off"
        // tmux waits half a second after Escape by default, to see whether a
        // sequence follows. Claude Code's Escape would lag by that much.
        + " \\; set -s escape-time 10"
        + " \\; new-session -A -s bb"
        + " || exec \"$SHELL\" -l";

    private final int theme;
    private final int text;
    private final int background;
    private final int fontSize;
    private final int terminalType;
    private final boolean smoothScroll;
    private final boolean keyBar;
    private final boolean tmux;

    /**
     * @param fontSize a Profile font index, or -1 to use each connection's own
     */
    public Settings(int theme, int text, int background, int fontSize, int terminalType,
                    boolean smoothScroll, boolean keyBar, boolean tmux) {
        this.theme = clamp(theme, Theme.ALL.length, 0);
        this.text = clamp(text, TEXT_COLOURS.length, 0);
        this.background = clamp(background, BACKGROUND_COLOURS.length, 0);
        this.fontSize = (fontSize < -1 || fontSize >= Profile.CELL_WIDTHS.length) ? -1 : fontSize;
        this.terminalType = clamp(terminalType, TERMINAL_TYPES.length, 0);
        this.smoothScroll = smoothScroll;
        this.keyBar = keyBar;
        this.tmux = tmux;
    }

    public static Settings defaults() {
        return new Settings(0, 0, 0, -1, 0, true, true, true);
    }

    public int themeIndex() {
        return theme;
    }

    public int textIndex() {
        return text;
    }

    public int backgroundIndex() {
        return background;
    }

    /** A Profile font index, or -1 when each connection keeps its own. */
    public int fontSize() {
        return fontSize;
    }

    public int terminalTypeIndex() {
        return terminalType;
    }

    public String terminalType() {
        return TERMINAL_TYPES[terminalType];
    }

    /** Whether scrolling slides rather than jumps. */
    public boolean smoothScroll() {
        return smoothScroll;
    }

    /** Whether the row of keys shows along the bottom of a session. */
    public boolean keyBar() {
        return keyBar;
    }

    /** Whether a session starts in tmux rather than a bare shell. */
    public boolean tmux() {
        return tmux;
    }

    /** The theme with any text or background override applied. */
    public Theme theme() {
        Theme base = Theme.ALL[theme];
        int fg = TEXT_COLOURS[text] < 0 ? base.foreground() : TEXT_COLOURS[text];
        int bg = BACKGROUND_COLOURS[background] < 0 ? base.background() : BACKGROUND_COLOURS[background];
        if (fg == base.foreground() && bg == base.background()) {
            return base;
        }
        return base.withDefaults(fg, bg);
    }

    /** The font a connection should open with. */
    public int fontFor(Profile profile) {
        return fontSize >= 0 ? fontSize : profile.fontSize();
    }

    public Settings withTheme(int value) {
        return new Settings(value, text, background, fontSize, terminalType, smoothScroll, keyBar, tmux);
    }

    public Settings withText(int value) {
        return new Settings(theme, value, background, fontSize, terminalType, smoothScroll, keyBar, tmux);
    }

    public Settings withBackground(int value) {
        return new Settings(theme, text, value, fontSize, terminalType, smoothScroll, keyBar, tmux);
    }

    public Settings withFontSize(int value) {
        return new Settings(theme, text, background, value, terminalType, smoothScroll, keyBar, tmux);
    }

    public Settings withTerminalType(int value) {
        return new Settings(theme, text, background, fontSize, value, smoothScroll, keyBar, tmux);
    }

    public Settings withSmoothScroll(boolean value) {
        return new Settings(theme, text, background, fontSize, terminalType, value, keyBar, tmux);
    }

    public Settings withKeyBar(boolean value) {
        return new Settings(theme, text, background, fontSize, terminalType, smoothScroll, value, tmux);
    }

    public Settings withTmux(boolean value) {
        return new Settings(theme, text, background, fontSize, terminalType, smoothScroll, keyBar, value);
    }

    /**
     * The next font size up or down from a Profile font index, or the same
     * index at either end.
     */
    public static int stepFont(int index, int direction) {
        for (int i = 0; i < SIZE_ORDER.length; i++) {
            if (SIZE_ORDER[i] == index) {
                int next = i + direction;
                if (next < 0 || next >= SIZE_ORDER.length) {
                    return index;
                }
                return SIZE_ORDER[next];
            }
        }
        return index;
    }

    public byte[] encode() {
        WireWriter w = new WireWriter(40);
        w.writeUint32(FORMAT);
        w.writeUint32(theme);
        w.writeUint32(text);
        w.writeUint32(background);
        w.writeUint32(fontSize + 1);
        w.writeUint32(terminalType);
        w.writeBoolean(smoothScroll);
        w.writeBoolean(keyBar);
        w.writeBoolean(tmux);
        return w.toByteArray();
    }

    public static Settings decode(byte[] record) throws IOException {
        WireReader r = new WireReader(record);
        long format = r.readUint32();
        if (format < FORMAT_WITHOUT_SMOOTH || format > FORMAT) {
            throw new SshException("settings are in format " + format + ", not "
                + FORMAT_WITHOUT_SMOOTH + " to " + FORMAT);
        }
        int theme = (int) r.readUint32();
        int text = (int) r.readUint32();
        int background = (int) r.readUint32();
        int fontSize = (int) r.readUint32() - 1;
        int terminalType = (int) r.readUint32();
        boolean smoothScroll = format < FORMAT_WITHOUT_SESSION || r.readBoolean();
        boolean keyBar = format < FORMAT || r.readBoolean();
        boolean tmux = format < FORMAT || r.readBoolean();
        return new Settings(theme, text, background, fontSize, terminalType,
            smoothScroll, keyBar, tmux);
    }

    private static int clamp(int value, int length, int fallback) {
        return (value < 0 || value >= length) ? fallback : value;
    }
}
