package berryssh.device;

import berryssh.terminal.VT320;

/**
 * The keys a terminal needs and a BlackBerry keyboard does not have, and the
 * session actions that sit beside them.
 *
 * Two layouts: the bar along the bottom of the session, and the quick panel it
 * opens — a grid sized for a thumb, where every cell also answers to one
 * letter on the hardware keyboard. Free of MIDP, like {@link Profile}, so what
 * each one sends can be tested on the host.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Keys {

    /** Sent through VT320's key handling, so cursor-key mode is honoured. */
    public static final int KEY = 0;
    /** Sent as these exact bytes, in one write. */
    public static final int SEQUENCE = 1;
    /** Arms the sticky Ctrl for the next key typed. */
    public static final int CONTROL = 2;
    /** Something the application does rather than the terminal. */
    public static final int ACTION = 3;

    public static final int ACTION_PANEL = 1;
    public static final int ACTION_SYMBOLS = 2;
    public static final int ACTION_FONT_UP = 3;
    public static final int ACTION_FONT_DOWN = 4;
    public static final int ACTION_DISCONNECT = 5;
    public static final int ACTION_RECONNECT = 6;
    public static final int ACTION_CLOSE = 7;
    public static final int ACTION_BACK = 8;

    /** One key or action. */
    public static final class Entry {
        public final String label;
        /** The letter that does this while the panel is open, or 0. */
        public final char hotkey;
        public final int kind;
        public final int code;
        public final String sequence;

        Entry(String label, char hotkey, int kind, int code, String sequence) {
            this.label = label;
            this.hotkey = hotkey;
            this.kind = kind;
            this.code = code;
            this.sequence = sequence;
        }

        /** Whether the panel should stay open after this, for the next press. */
        public boolean keepsPanelOpen() {
            if (kind == CONTROL) {
                // The next letter has to reach the terminal, not the panel.
                return false;
            }
            if (kind == ACTION) {
                return code == ACTION_FONT_UP || code == ACTION_FONT_DOWN
                    || code == ACTION_SYMBOLS || code == ACTION_BACK;
            }
            // Detaching ends the session on screen; nothing left to press.
            return !"\u0002d".equals(sequence);
        }
    }

    private static Entry key(String label, char hotkey, int code) {
        return new Entry(label, hotkey, KEY, code, null);
    }

    private static Entry seq(String label, char hotkey, String sequence) {
        return new Entry(label, hotkey, SEQUENCE, 0, sequence);
    }

    private static Entry action(String label, char hotkey, int code) {
        return new Entry(label, hotkey, ACTION, code, null);
    }

    /** Along the bottom of the session, for the thumb. */
    public static final Entry[] BAR = {
        seq("Esc", (char) 0, "\u001b"),
        seq("Tab", (char) 0, "\t"),
        new Entry("Ctrl", (char) 0, CONTROL, 0, null),
        key("\u2191", (char) 0, VT320.VK_UP),
        key("\u2193", (char) 0, VT320.VK_DOWN),
        key("\u2190", (char) 0, VT320.VK_LEFT),
        key("\u2192", (char) 0, VT320.VK_RIGHT),
        seq("\u21b5", (char) 0, "\r"),
        action("\u22ef", (char) 0, ACTION_PANEL)
    };

    public static final int PANEL_COLUMNS = 5;

    /**
     * The quick panel, row by row. The arrows sit where I, J, K and L sit on
     * the keyboard, so the letters point the way they move.
     */
    public static final Entry[] PANEL = {
        seq("Esc", 'e', "\u001b"),
        seq("Tab", 't', "\t"),
        seq("Shift+Tab", 'a', "\u001b[Z"),
        seq("Ctrl+C", 'c', "\u0003"),
        seq("Ctrl+D", 'd', "\u0004"),

        key("PgUp", 'p', VT320.VK_PAGE_UP),
        key("\u2191 Up", 'i', VT320.VK_UP),
        new Entry("Ctrl+..", 'x', CONTROL, 0, null),
        seq("Ctrl+R", 'r', "\u0012"),
        seq("Ctrl+L", 'w', "\u000c"),

        key("\u2190 Left", 'j', VT320.VK_LEFT),
        key("\u2193 Down", 'k', VT320.VK_DOWN),
        key("\u2192 Right", 'l', VT320.VK_RIGHT),
        seq("\u21b5 Enter", '\r', "\r"),
        seq("Ctrl+Z", 'z', "\u001a"),

        action("Symbols", 's', ACTION_SYMBOLS),
        key("PgDn", 'n', VT320.VK_PAGE_DOWN),
        seq("Detach", 'b', "\u0002d"),
        action("Font +", 'f', ACTION_FONT_UP),
        action("Font -", 'v', ACTION_FONT_DOWN),

        key("Home", 'h', VT320.VK_HOME),
        key("End", 'g', VT320.VK_END),
        action("Reconnect", 'o', ACTION_RECONNECT),
        action("Disconnect", 'q', ACTION_DISCONNECT),
        action("Close", ' ', ACTION_CLOSE)
    };

    /** Everything on a US keyboard that is not a letter or a digit. */
    public static final String SYMBOLS = "./-_|~`'\":;!?@#$%^&*()[]{}<>=+\\,";

    public static final int SYMBOL_COLUMNS = 8;

    /** The panel's second page: every symbol, and a way back. */
    public static Entry[] symbols() {
        Entry[] entries = new Entry[SYMBOLS.length() + 1];
        for (int i = 0; i < SYMBOLS.length(); i++) {
            String c = SYMBOLS.substring(i, i + 1);
            entries[i] = seq(c, (char) 0, c);
        }
        entries[SYMBOLS.length()] = action("Back", ' ', ACTION_BACK);
        return entries;
    }

    /** The panel cell for a letter typed while it is open, or null. */
    public static Entry hotkey(Entry[] page, char typed) {
        char c = typed;
        if (c >= 'A' && c <= 'Z') {
            c = (char) (c - 'A' + 'a');
        }
        if (c == '\n') {
            c = '\r';
        }
        for (int i = 0; i < page.length; i++) {
            if (page[i].hotkey != 0 && page[i].hotkey == c) {
                return page[i];
            }
        }
        return null;
    }

    private Keys() {
    }
}
