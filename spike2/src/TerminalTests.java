import berryssh.device.Keyboard;
import berryssh.device.Keys;
import berryssh.device.RowDiff;
import berryssh.terminal.BitmapFont;
import berryssh.terminal.VT320;

import java.io.IOException;

/**
 * The terminal emulator, driven directly.
 *
 * VT320 came across from BBSSH nearly unchanged, so these are not vectors for
 * new code — they check that the port kept working, and pin the behaviours the
 * renderer and the SSH layer depend on. Escape sequence handling in the round
 * is checked against a real shell in ServerTests, since that produces sequences
 * nobody thought to write down here.
 *
 * Escapes are built with {@link #esc} rather than written literally: a source
 * file with invisible control bytes in it cannot be reviewed, and a stray one
 * would be impossible to see.
 *
 * Host-only, like the rest of the suite.
 */
public class TerminalTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        writesAndReads();
        wraps();
        handlesEscapeSequences();
        scrolls();
        sendsKeys();
        dispatchesKeys();
        keepsScrollback();
        mapsCharactersToGlyphs();
        parsesModernColours();
        mapsClaudeCodeGlyphs();
        findsWhatScrolled();
        slidesSmoothly();
        sendsTheMissingKeys();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** A terminal that records what it would have sent, and draws nowhere. */
    private static final class Headless extends VT320 {
        final StringBuffer sent = new StringBuffer();

        Headless(int columns, int rows) {
            super(columns, rows);
        }

        public void sendData(byte[] b, int offset, int length) throws IOException {
            for (int i = 0; i < length; i++) {
                sent.append((char) (b[offset + i] & 0xff));
            }
        }

        public void beep() {
        }

        public void resize() {
        }
    }

    private static void writesAndReads() {
        Headless t = new Headless(60, 25);
        t.putString("hello");
        checkTrue("text written appears on the screen", "hello".equals(row(t, 0, 5)));

        t.putString("\r\nsecond");
        checkTrue("carriage return and newline move to the next row",
            "second".equals(row(t, 1, 6)));
    }

    private static void wraps() {
        Headless t = new Headless(10, 4);
        t.putString("0123456789abc");
        checkTrue("text past the last column wraps to the next row",
            "0123456789".equals(row(t, 0, 10)) && "abc".equals(row(t, 1, 3)));
    }

    private static void handlesEscapeSequences() {
        Headless t = new Headless(20, 5);

        // CUP is one-based, so row 3 column 5 is index (4, 2).
        t.putString(esc("[3;5H") + "x");
        checkTrue("cursor addressing puts the character where it was asked",
            t.getChar(4, 2) == 'x');

        // Erase-in-display clears to NUL and leaves the cursor alone, which is
        // what a real terminal does — a program that wanted the cursor moved
        // would have said so, and clearing usually precedes a redraw.
        t.putString(esc("[2J"));
        checkTrue("erase-in-display clears the screen", t.getChar(4, 2) < 32);

        // The cursor really did stay put: this lands just past where the 'x'
        // was, because writing it advanced the cursor one column.
        t.putString(esc("[31m") + "red" + esc("[0m"));
        checkTrue("colour sequences are consumed rather than printed",
            "red".equals(rowFrom(t, 2, 5, 3)));

        // An unrecognised sequence has to be swallowed whole. A terminal that
        // printed its own confusion would corrupt the screen at the first
        // surprise, and a real session is full of surprises.
        Headless unknown = new Headless(20, 5);
        unknown.putString(esc("[?9999h") + "after");
        checkTrue("an unrecognised sequence is swallowed",
            "after".equals(row(unknown, 0, 5)));
    }

    private static void scrolls() {
        Headless t = new Headless(10, 3);
        t.putString("one\r\ntwo\r\nthree\r\nfour");
        checkTrue("output past the last row scrolls the screen",
            "two".equals(row(t, 0, 3))
                && "three".equals(row(t, 1, 5))
                && "four".equals(row(t, 2, 4)));
    }

    private static void sendsKeys() {
        Headless t = new Headless(20, 5);
        t.keyTyped(VT320.VK_ENTER, (char) 0, 0);
        checkTrue("Enter sends a carriage return", t.sent.toString().indexOf('\r') >= 0);

        // The cursor keys are why a terminal needs a keyboard mapping at all:
        // they are escape sequences rather than characters, so there is nothing
        // for a plain key-to-character table to produce.
        Headless arrows = new Headless(20, 5);
        arrows.keyPressed(VT320.VK_UP, 0);
        checkTrue("the up arrow sends an escape sequence",
            arrows.sent.length() >= 3 && arrows.sent.charAt(0) == 27);
    }

    /**
     * Key dispatch, and specifically the ordering that got it wrong on the
     * device.
     *
     * MIDP lets a device map keys to game actions, and BlackBerry maps the
     * letters so that games written for a numeric keypad work on a QWERTY: 'w'
     * reports UP, 'a' reports LEFT. Consulting the game action before the
     * character therefore turns typing into cursor movement on exactly those
     * letters and leaves every other key working — which is why it presented as
     * "some keys do nothing and some produce something else" rather than as a
     * keyboard that was plainly broken.
     *
     * The MIDP game action values are written out here rather than imported, so
     * the test does not depend on the stub jar being on the host classpath.
     */
    private static void dispatchesKeys() {
        final int UP = 1, LEFT = 2, RIGHT = 5, DOWN = 6;

        // A letter that the device also calls a direction is still a letter.
        checkTrue("'w' types 'w' even though the device reports UP",
            "w".equals(sendKey('w', UP)));
        checkTrue("'a' types 'a' even though the device reports LEFT",
            "a".equals(sendKey('a', LEFT)));
        checkTrue("'s' types 's' even though the device reports DOWN",
            "s".equals(sendKey('s', DOWN)));
        checkTrue("'d' types 'd' even though the device reports RIGHT",
            "d".equals(sendKey('d', RIGHT)));

        checkTrue("a letter with no game action still types itself",
            "q".equals(sendKey('q', 0)));
        checkTrue("space is a character, not a fire button",
            " ".equals(sendKey(' ', 8)));

        // The trackpad reports a direction and no character, so it still moves.
        String up = sendKey(-1, UP);
        checkTrue("a key with no character and a direction sends a cursor sequence",
            up.length() >= 3 && up.charAt(0) == 27);

        checkTrue("Enter sends a carriage return", sendKey(13, 0).indexOf('\r') >= 0);
        // Backspace has to reach VT320 through keyPressed: keyTyped drops it,
        // which sent nothing at all and left no way to correct a typo.
        checkTrue("Backspace sends something", sendKey(8, 0).length() > 0);
        checkTrue("Delete deletes too, on a handset with one such key",
            sendKey(127, 0).equals(sendKey(8, 0)) && sendKey(127, 0).length() > 0);
        checkTrue("Tab sends one", "\t".equals(sendKey(9, 0)));

        // A key we know nothing about must send nothing rather than a guess.
        checkTrue("an unmapped key sends nothing", "".equals(sendKey(-999, 0)));

        // Sticky Ctrl, on the character path.
        Headless t = new Headless(20, 5);
        Keyboard k = new Keyboard(t);
        k.toggleControl();
        checkTrue("Ctrl is armed", k.controlPending());
        k.keyPressed('c', 0);
        checkTrue("Ctrl-C sends 0x03 and disarms",
            t.sent.length() == 1 && t.sent.charAt(0) == 3 && !k.controlPending());

        // Ctrl-I must be Tab. Under a Turkish locale a case fold would make
        // this the one letter of the alphabet that stopped working.
        checkTrue("Ctrl-I is Tab under any locale", Keyboard.controlOf('i') == 9);
        checkTrue("Ctrl-I is Tab from the capital too", Keyboard.controlOf('I') == 9);
    }

    /**
     * The character-to-cell mapping, which is what was wrong.
     *
     * The arithmetic this replaced assumed a contiguous Latin-1 layout, so it
     * could not distinguish a character it had a glyph for from one it did not:
     * Turkish letters above U+00FF resolved to some empty cell and drew
     * nothing, with no way to tell that from a space.
     */
    private static int foregroundAt(VT320 t, int column, int row) {
        int a = (int) (t.terminalData[t.screenBase + row][column] >>> 32);
        return ((a & VT320.COLOR_FG) >>> VT320.FG_SHIFT) - 1;
    }

    private static int backgroundAt(VT320 t, int column, int row) {
        int a = (int) (t.terminalData[t.screenBase + row][column] >>> 32);
        return ((a & VT320.COLOR_BG) >>> VT320.BG_SHIFT) - 1;
    }

    private static int attributesAt(VT320 t, int column, int row) {
        return (int) (t.terminalData[t.screenBase + row][column] >>> 32);
    }

    /** What xterm-256color programs send, Claude Code among them. */
    private static void parsesModernColours() {
        Headless t = new Headless(20, 5);
        t.putString(esc("[38;5;208m") + "a" + esc("[48;5;236m") + "b" + esc("[0m") + "c");
        checkTrue("38;5;n sets a 256-colour foreground", foregroundAt(t, 0, 0) == 208);
        checkTrue("48;5;n sets a 256-colour background",
            backgroundAt(t, 1, 0) == 236 && foregroundAt(t, 1, 0) == 208);
        checkTrue("SGR 0 clears both", foregroundAt(t, 2, 0) == -1 && backgroundAt(t, 2, 0) == -1);

        // Claude's orange, rgb(215,119,87), lands on the cube's 173.
        t.putString(esc("[38;2;215;119;87m") + "d" + esc("[39m") + "e");
        checkTrue("truecolour lands on the nearest palette entry", foregroundAt(t, 3, 0) == 173);
        checkTrue("SGR 39 restores the default foreground", foregroundAt(t, 4, 0) == -1);
        checkTrue("a grey lands on the grey ramp", VT320.nearestColour(128, 128, 128) == 244);
        checkTrue("pure white is the cube's corner", VT320.nearestColour(255, 255, 255) == 231);

        t.putString(esc("[91m") + "f" + esc("[104m") + "g" + esc("[0m"));
        checkTrue("90-97 are the bright foregrounds", foregroundAt(t, 5, 0) == 9);
        checkTrue("100-107 are the bright backgrounds", backgroundAt(t, 6, 0) == 12);

        // Parameters after a 38;5;n still apply: the colour consumed two more.
        t.putString(esc("[1;38;5;12;4m") + "h" + esc("[0m"));
        int a = attributesAt(t, 7, 0);
        checkTrue("an extended colour does not swallow what follows it",
            foregroundAt(t, 7, 0) == 12 && (a & VT320.BOLD) != 0 && (a & VT320.UNDERLINE) != 0);

        t.putString(esc("[2m") + "i" + esc("[22m") + "j");
        checkTrue("SGR 22 ends dim as well as bold",
            (attributesAt(t, 8, 0) & VT320.LOW) != 0 && (attributesAt(t, 9, 0) & VT320.LOW) == 0);
    }

    private static void mapsClaudeCodeGlyphs() {
        String glyphs = "\u25cf\u23fa\u23bf\u276f\u2026\u273b\u2736\u2733\u2722\u273d"
            + "\u2713\u2717\u2190\u2192\u21b5\u23f5\u23f8\u2022\u2014\u25b6\u2588\u2590\u259b\u259c";
        boolean all = true;
        for (int i = 0; i < glyphs.length(); i++) {
            if (BitmapFont.indexOf(glyphs.charAt(i)) < 0) {
                all = false;
                System.out.println("        no cell for U+" + Integer.toHexString(glyphs.charAt(i)));
            }
        }
        checkTrue("Claude Code's symbols and mascot all have cells", all);
        checkTrue("the old cells kept their places",
            BitmapFont.indexOf('\u2580') == 357 - 32 && BitmapFont.indexOf('\u259f') == 356);
    }

    private static long[] hashes(VT320 t, int columns, int rows, boolean[] blank) {
        long[] h = new long[rows];
        for (int row = 0; row < rows; row++) {
            long[] cells = t.terminalData[t.screenBase + row];
            boolean cursor = row == t.ROW;
            h[row] = RowDiff.hash(cells, columns, cursor ? t.COLUMN : -1);
            blank[row] = !cursor && RowDiff.blank(cells, columns);
        }
        return h;
    }

    /** The comparison the canvas uses to tell a scroll from a redraw. */
    private static void findsWhatScrolled() {
        int columns = 30;
        int rows = 10;
        Headless t = new Headless(columns, rows);
        t.setScrollbackBufferSize(100);
        for (int i = 1; i <= 12; i++) {
            t.putString("line " + i + "\r\n");
        }
        boolean[] beforeBlank = new boolean[rows];
        long[] before = hashes(t, columns, rows, beforeBlank);
        boolean[] drawn = new boolean[rows];
        for (int i = 0; i < rows; i++) {
            drawn[i] = true;
        }

        t.putString("line 13\r\nline 14\r\nline 15\r\n");
        boolean[] nowBlank = new boolean[rows];
        long[] now = hashes(t, columns, rows, nowBlank);
        checkTrue("three new lines read as a scroll of three",
            RowDiff.scrollDistance(now, nowBlank, before, beforeBlank, drawn) == 3);

        // What Claude Code does between scrolls: the cursor goes up and a row
        // is rewritten in place. That is a change, not a scroll.
        before = now;
        beforeBlank = nowBlank;
        t.putString(esc("[3A") + esc("[2K") + "spinner" + esc("[3B") + "\r");
        now = hashes(t, columns, rows, nowBlank = new boolean[rows]);
        checkTrue("a row rewritten in place is not a scroll",
            RowDiff.scrollDistance(now, nowBlank, before, beforeBlank, drawn) == 0);
        int changed = 0;
        for (int i = 0; i < rows; i++) {
            if (now[i] != before[i]) {
                changed++;
            }
        }
        checkTrue("and only that row needs drawing again", changed == 1);

        Headless empty = new Headless(columns, rows);
        boolean[] eb = new boolean[rows];
        long[] e1 = hashes(empty, columns, rows, eb);
        empty.putString("\r\n\r\n");
        boolean[] eb2 = new boolean[rows];
        long[] e2 = hashes(empty, columns, rows, eb2);
        checkTrue("an empty screen never looks like a scroll",
            RowDiff.scrollDistance(e2, eb2, e1, eb, drawn) == 0);
    }

    private static void slidesSmoothly() {
        checkTrue("a slide starts at the full distance", RowDiff.slideOffset(0, 200, 42) == 42);
        checkTrue("and ends at none", RowDiff.slideOffset(200, 200, 42) == 0
            && RowDiff.slideOffset(500, 200, 42) == 0);
        checkTrue("halfway through it is halfway there", RowDiff.slideOffset(100, 200, 42) == 21);
        boolean falling = true;
        int last = 43;
        for (int ms = 0; ms <= 200; ms += 5) {
            int offset = RowDiff.slideOffset(ms, 200, 42);
            if (offset > last) {
                falling = false;
            }
            last = offset;
        }
        checkTrue("it never moves backwards", falling);
        checkTrue("it eases in: the first tenth covers less than a tenth",
            42 - RowDiff.slideOffset(20, 200, 42) < 5);
        checkTrue("long scrolls are capped so typing never waits",
            RowDiff.slideDuration(1) == 170 && RowDiff.slideDuration(30) == 320);
    }

    private static String sent(Keys.Entry entry) {
        Headless t = new Headless(20, 5);
        Keyboard k = new Keyboard(t);
        k.perform(entry);
        return t.sent.toString();
    }

    /** What the key bar and quick panel put on the wire. */
    private static void sendsTheMissingKeys() {
        checkTrue("Down sends the cursor-down sequence",
            sent(Keys.hotkey(Keys.PANEL, 'k')).equals("\u001b[B"));
        checkTrue("Shift+Tab goes as one sequence", sent(Keys.hotkey(Keys.PANEL, 'a')).equals("\u001b[Z"));
        checkTrue("Esc is a lone escape", sent(Keys.hotkey(Keys.PANEL, 'e')).equals("\u001b"));
        checkTrue("Ctrl+C is ETX", sent(Keys.hotkey(Keys.PANEL, 'c')).equals("\u0003"));
        checkTrue("Enter on the panel is a carriage return", sent(Keys.hotkey(Keys.PANEL, '\r')).equals("\r"));
        checkTrue("Detach is the tmux prefix and d", sent(Keys.hotkey(Keys.PANEL, 'b')).equals("\u0002d"));
        checkTrue("capital letters work as hotkeys too", Keys.hotkey(Keys.PANEL, 'K') == Keys.hotkey(Keys.PANEL, 'k'));
        checkTrue("the arrows sit on I J K L",
            Keys.hotkey(Keys.PANEL, 'i').code == VT320.VK_UP && Keys.hotkey(Keys.PANEL, 'j').code == VT320.VK_LEFT
                && Keys.hotkey(Keys.PANEL, 'k').code == VT320.VK_DOWN && Keys.hotkey(Keys.PANEL, 'l').code == VT320.VK_RIGHT);

        boolean unique = true;
        for (int i = 0; i < Keys.PANEL.length; i++) {
            for (int j = i + 1; j < Keys.PANEL.length; j++) {
                if (Keys.PANEL[i].hotkey != 0 && Keys.PANEL[i].hotkey == Keys.PANEL[j].hotkey) {
                    unique = false;
                    System.out.println("        two cells on " + Keys.PANEL[i].hotkey);
                }
            }
        }
        checkTrue("no two panel cells share a letter", unique);
        checkTrue("the panel is a full grid",
            Keys.PANEL.length % Keys.PANEL_COLUMNS == 0 && Keys.PANEL.length == 25);

        // Ctrl armed from the panel, then a letter typed, is that control.
        Headless t = new Headless(20, 5);
        Keyboard k = new Keyboard(t);
        k.perform(Keys.hotkey(Keys.PANEL, 'x'));
        k.keyPressed('a', 0);
        checkTrue("Ctrl from the panel applies to the next letter", t.sent.toString().equals("\u0001"));
        checkTrue("arming Ctrl closes the panel so the letter reaches the terminal",
            !Keys.hotkey(Keys.PANEL, 'x').keepsPanelOpen());
        checkTrue("arrows keep the panel open for the next press",
            Keys.hotkey(Keys.PANEL, 'k').keepsPanelOpen() && Keys.hotkey(Keys.PANEL, '\r').keepsPanelOpen());

        Keys.Entry[] symbols = Keys.symbols();
        Headless st = new Headless(20, 5);
        Keyboard sk = new Keyboard(st);
        for (int i = 0; i < symbols.length - 1; i++) {
            sk.perform(symbols[i]);
        }
        checkTrue("every symbol types itself", st.sent.toString().equals(Keys.SYMBOLS));
        checkTrue("every bar and panel label can be drawn", drawable(Keys.BAR) && drawable(Keys.PANEL));
    }

    private static boolean drawable(Keys.Entry[] entries) {
        for (int i = 0; i < entries.length; i++) {
            String label = entries[i].label;
            for (int c = 0; c < label.length(); c++) {
                if (BitmapFont.indexOf(label.charAt(c)) < 0) {
                    System.out.println("        no glyph for " + label);
                    return false;
                }
            }
        }
        return true;
    }

    private static void mapsCharactersToGlyphs() {
        checkTrue("space is the first cell", BitmapFont.indexOf(' ') == 0);
        checkTrue("ASCII is contiguous from there",
            BitmapFont.indexOf('A') == 'A' - ' ' && BitmapFont.indexOf('~') == '~' - ' ');

        // The letters that were missing. Each must now have a cell, and each
        // must have its own.
        char[] turkish = { 'ç', 'Ç', 'ö', 'Ö', 'ü', 'Ü', 'ğ', 'Ğ', 'ı', 'İ', 'ş', 'Ş' };
        boolean allMapped = true;
        for (int i = 0; i < turkish.length; i++) {
            if (BitmapFont.indexOf(turkish[i]) < 0) {
                allMapped = false;
            }
            for (int j = i + 1; j < turkish.length; j++) {
                if (BitmapFont.indexOf(turkish[i]) == BitmapFont.indexOf(turkish[j])) {
                    allMapped = false;
                }
            }
        }
        checkTrue("every Turkish letter has a cell of its own", allMapped);

        // Box drawing, which is most of what a terminal UI puts on screen.
        checkTrue("box drawing is mapped",
            BitmapFont.indexOf('─') >= 0 && BitmapFont.indexOf('│') >= 0
                && BitmapFont.indexOf('┌') >= 0 && BitmapFont.indexOf('┼') >= 0);
        checkTrue("block elements are mapped", BitmapFont.indexOf('█') >= 0);

        // Control codes and anything outside the ranges must be reported as
        // absent rather than resolved to a cell that happens to be in bounds.
        checkTrue("control codes have no cell",
            BitmapFont.indexOf((char) 7) < 0 && BitmapFont.indexOf((char) 27) < 0);
        checkTrue("C1 controls have no cell", BitmapFont.indexOf((char) 0x85) < 0);
        checkTrue("a character outside the ranges has no cell",
            BitmapFont.indexOf('\u4e00') < 0 && BitmapFont.indexOf('\u0400') < 0);

        // Every mapped character has to land inside the atlas the layout claims.
        boolean inBounds = true;
        for (int c = 0; c < 0x3000; c++) {
            int index = BitmapFont.indexOf((char) c);
            if (index >= BitmapFont.layoutSize()) {
                inBounds = false;
            }
        }
        checkTrue("no character maps past the end of the layout", inBounds);
    }

    /**
     * Scrollback, which the emulator does not have until it is asked for.
     *
     * The constructor sizes the buffer to the screen, so a terminal built and
     * left alone keeps nothing: paging up would show the screen already on
     * display. This is the mechanism the canvas reads through, checked here
     * because the canvas itself needs MIDP and cannot run on the host.
     */
    private static void keepsScrollback() {
        Headless plain = new Headless(20, 4);
        plain.putString("one\r\ntwo\r\nthree\r\nfour\r\nfive\r\n");
        checkTrue("without asking, nothing is kept above the screen",
            plain.screenBase == 0);

        Headless t = new Headless(20, 4);
        t.setScrollbackBufferSize(100);
        for (int i = 1; i <= 40; i++) {
            t.putString("line" + i + "\r\n");
        }

        checkTrue("history accumulates above the live screen", t.screenBase > 0);

        // The live screen still shows the end.
        checkTrue("the live screen is the most recent output",
            "line40".equals(row(t, 2, 6)));

        // And the buffer holds what scrolled past, which is what the canvas
        // reads when it pages up.
        boolean foundEarlier = false;
        for (int line = 0; line < t.screenBase; line++) {
            StringBuffer sb = new StringBuffer();
            for (int c = 0; c < 6; c++) {
                sb.append((char) t.terminalData[line][c]);
            }
            if ("line10".equals(sb.toString())) {
                foundEarlier = true;
            }
        }
        checkTrue("a line that scrolled off is still in the buffer", foundEarlier);
    }

    /** Feeds one key through the dispatcher and returns what reached the wire. */
    private static String sendKey(int keyCode, int gameAction) {
        Headless t = new Headless(20, 5);
        new Keyboard(t).keyPressed(keyCode, gameAction);
        return t.sent.toString();
    }

    /** Builds an escape sequence without putting a control byte in the source. */
    private static String esc(String rest) {
        return ((char) 27) + rest;
    }

    private static String row(VT320 t, int line, int length) {
        return rowFrom(t, line, 0, length);
    }

    private static String rowFrom(VT320 t, int line, int column, int length) {
        StringBuffer sb = new StringBuffer(length);
        for (int i = 0; i < length; i++) {
            sb.append(t.getChar(column + i, line));
        }
        return sb.toString();
    }

    private static void checkTrue(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }
}
