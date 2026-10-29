package berryssh.device;

import java.io.IOException;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import berryssh.terminal.BitmapFont;
import berryssh.terminal.VT320;

/**
 * The terminal on screen.
 *
 * Drawing is per character cell out of the font atlas rather than through
 * MIDP's text API, for the reason in {@link BitmapFont}: the smallest system
 * font this device offers gives a 21x13 terminal, which is not one.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class TerminalCanvas extends Canvas {

    /** What the canvas cannot do itself: the actions behind some keys. */
    public interface Listener {
        void action(int code);
    }

    // VT320 packs these into the high half of each cell.
    private static final int BOLD = VT320.BOLD;
    private static final int UNDERLINE = VT320.UNDERLINE;
    private static final int INVERT = VT320.INVERT;
    private static final int LOW = VT320.LOW;

    private BitmapFont font;
    private Theme theme = Theme.CLASSIC;
    private VT320 terminal;
    private Keyboard keyboard;
    private String status = "";

    /** Rows above the live screen currently being looked at. Zero is live. */
    private int scrollOffset;

    private boolean shown;
    private boolean showKeyCodes;
    private String lastKey = "";

    /**
     * Each screen row, already drawn, and what it was drawn from.
     *
     * Drawing a cell means recolouring a glyph and copying its pixels, and a
     * full screen is some two and a half thousand of them; Claude Code changes
     * one row for every frame of its spinner. Redrawing only rows whose
     * content changed, and moving the pictures of rows that only moved, is
     * what keeps the screen from lagging behind the session.
     */
    private Image[] rowImages = new Image[0];
    private long[] rowHashes = new long[0];
    private boolean[] rowBlank = new boolean[0];
    /** Pictures no row is using, kept for the next one that needs drawing. */
    private Image[] spare = new Image[0];
    private int spareCount;
    private int cacheWidth;
    private int cacheCellHeight;

    /**
     * Scrolling, drawn as a slide rather than a jump: the rows that left the
     * top, and how far through the slide the screen is.
     */
    private boolean smoothScroll = true;

    /** The row of keys along the bottom, and which one a finger is on. */
    private boolean keyBar = true;
    private int pressedKey = -1;
    private int pressedPanel = -1;
    private int dragY = -1;
    private boolean dragged;
    private Listener listener;

    /**
     * The quick panel over the top of the session: the page showing, or null
     * when it is closed, and the cell the trackpad is on.
     */
    private Keys.Entry[] panel;
    private int panelColumns;
    private int panelFocus;
    private Image[] outgoing = new Image[0];
    private int slideRows;
    private long slideStart;
    private int slideDuration;

    /**
     * Starts windowed rather than full screen, deliberately.
     *
     * Full screen buys about two more rows and costs the menu: MIDP does not
     * promise that commands stay reachable without one, and on this device they
     * do not. That trade is only ever worth making by choice — a terminal you
     * cannot get out of is worse than a slightly shorter one, and there is no
     * other way off this screen, since every command lives in that menu.
     */
    public TerminalCanvas(BitmapFont font, Theme theme) {
        this.font = font;
        this.theme = theme;
        setFullScreenMode(false);
    }

    /** A different cell size; the caller resizes the terminal to match. */
    public void setFont(BitmapFont font) {
        this.font = font;
        invalidate();
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
        invalidate();
    }

    public void setSmoothScroll(boolean on) {
        smoothScroll = on;
    }

    /** Before the session starts: it changes how many rows there are. */
    public void setKeyBar(boolean on) {
        keyBar = on;
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean panelOpen() {
        return panel != null;
    }

    /** Opens the quick panel, or closes it when it is open. */
    public void togglePanel() {
        if (panel == null) {
            showPage(Keys.PANEL, Keys.PANEL_COLUMNS);
        } else {
            closePanel();
        }
    }

    private void showPage(Keys.Entry[] page, int columns) {
        panel = page;
        panelColumns = columns;
        panelFocus = 0;
        repaint();
    }

    private void closePanel() {
        panel = null;
        repaint();
    }

    /** Does what a panel cell says, and decides whether the panel stays. */
    private void activate(Keys.Entry entry) {
        if (entry.kind == Keys.ACTION) {
            switch (entry.code) {
                case Keys.ACTION_SYMBOLS:
                    showPage(Keys.symbols(), Keys.SYMBOL_COLUMNS);
                    return;
                case Keys.ACTION_BACK:
                    showPage(Keys.PANEL, Keys.PANEL_COLUMNS);
                    return;
                case Keys.ACTION_CLOSE:
                case Keys.ACTION_PANEL:
                    closePanel();
                    return;
                default:
                    if (!entry.keepsPanelOpen()) {
                        closePanel();
                    }
                    if (listener != null) {
                        listener.action(entry.code);
                    }
                    repaint();
                    return;
            }
        }
        if (keyboard != null) {
            toLive();
            keyboard.perform(entry);
        }
        if (!entry.keepsPanelOpen()) {
            closePanel();
        }
        repaint();
    }

    private int panelRows() {
        return (panel.length + panelColumns - 1) / panelColumns;
    }

    private int panelCellHeight() {
        int h = font.cellHeight() * 2 + 12;
        return h < 40 ? 40 : h;
    }

    /** The panel cell at a point, -1 for the panel but no cell, -2 for outside it. */
    private int panelCellAt(int x, int y) {
        int top = font.cellHeight();
        int bottom = top + panelRows() * panelCellHeight();
        if (y < top || y >= bottom) {
            return -2;
        }
        int index = ((y - top) / panelCellHeight()) * panelColumns + x * panelColumns / getWidth();
        return index < panel.length ? index : -1;
    }

    private void drawPanel(Graphics g, int width) {
        int top = font.cellHeight();
        int cellHeight = panelCellHeight();
        int rows = panelRows();
        int face = theme.statusBar();
        int accent = theme.colour(13);
        g.setColor(face);
        g.fillRect(0, top, width, rows * cellHeight);
        for (int i = 0; i < panel.length; i++) {
            Keys.Entry e = panel[i];
            int column = i % panelColumns;
            int row = i / panelColumns;
            int x0 = column * width / panelColumns;
            int x1 = (column + 1) * width / panelColumns;
            int y0 = top + row * cellHeight;
            boolean lit = i == panelFocus || i == pressedPanel
                || (e.kind == Keys.CONTROL && keyboard != null && keyboard.controlPending());
            int cell = lit ? accent : theme.background();
            g.setColor(cell);
            g.fillRect(x0 + 2, y0 + 2, x1 - x0 - 4, cellHeight - 4);
            String label = e.label;
            int max = (x1 - x0 - 4) / font.cellWidth();
            if (label.length() > max) {
                label = label.substring(0, max);
            }
            int x = x0 + (x1 - x0 - label.length() * font.cellWidth()) / 2;
            int y = y0 + (cellHeight - font.cellHeight()) / 2 + 2;
            font.setColours(lit ? theme.background() : theme.foreground(), cell);
            for (int c = 0; c < label.length(); c++) {
                font.drawChar(g, label.charAt(c), x + c * font.cellWidth(), y);
            }
            String hint = hintFor(e.hotkey);
            if (hint.length() > 0) {
                font.setColours(lit ? theme.background() : accent, cell);
                for (int c = 0; c < hint.length(); c++) {
                    font.drawChar(g, hint.charAt(c), x0 + 6 + c * font.cellWidth(), y0 + 4);
                }
            }
        }
        g.setColor(accent);
        g.drawLine(0, top + rows * cellHeight, width, top + rows * cellHeight);
    }

    /** How a cell's letter is shown in its corner. */
    private static String hintFor(char hotkey) {
        if (hotkey == 0) {
            return "";
        }
        if (hotkey == '\r') {
            return "\u21b5";
        }
        if (hotkey == ' ') {
            return "spc";
        }
        return String.valueOf((char) (hotkey - 'a' + 'A'));
    }

    /**
     * A key while the panel is open: its letter does that cell, the trackpad
     * moves between cells and clicks one, and Backspace or Escape leaves.
     */
    private void panelKey(int keyCode, int action) {
        boolean symbols = panel != Keys.PANEL;
        if (keyCode == 8 || keyCode == 127 || keyCode == -8) {
            if (symbols) {
                showPage(Keys.PANEL, Keys.PANEL_COLUMNS);
            } else {
                closePanel();
            }
            return;
        }
        if (keyCode > 0 && keyCode != ' ' && symbols && Keyboard.isPrintable((char) keyCode)) {
            // On the symbols page the keyboard still types, Alt symbols included.
            if (keyboard != null) {
                keyboard.keyPressed(keyCode, 0);
            }
            return;
        }
        if (keyCode > 0) {
            Keys.Entry entry = Keys.hotkey(panel, (char) keyCode);
            if (entry != null) {
                activate(entry);
                return;
            }
        }
        switch (action) {
            case Canvas.UP:
                moveFocus(-panelColumns);
                return;
            case Canvas.DOWN:
                moveFocus(panelColumns);
                return;
            case Canvas.LEFT:
                moveFocus(-1);
                return;
            case Canvas.RIGHT:
                moveFocus(1);
                return;
            case Canvas.FIRE:
                activate(panel[panelFocus]);
                return;
            default:
                break;
        }
    }

    private void moveFocus(int by) {
        int next = panelFocus + by;
        if (next >= 0 && next < panel.length) {
            panelFocus = next;
            repaint();
        }
    }

    /** Tall enough for a thumb, whatever the font size. */
    private int barHeight() {
        if (!keyBar) {
            return 0;
        }
        int h = font.cellHeight() + 14;
        return h < 32 ? 32 : h;
    }

    /** Forgets every drawn row, so the next paint draws them all. */
    private synchronized void invalidate() {
        rowImages = new Image[0];
        rowHashes = new long[0];
        rowBlank = new boolean[0];
        spare = new Image[0];
        spareCount = 0;
        outgoing = new Image[0];
        slideRows = 0;
        repaint();
    }

    /** The size changes, so the caller has to tell the session to resize too. */
    public void setFullScreen(boolean on) {
        setFullScreenMode(on);
        repaint();
    }

    public void attach(VT320 terminal, Keyboard keyboard) {
        this.terminal = terminal;
        this.keyboard = keyboard;
        repaint();
    }

    /**
     * Blocks until the canvas is actually on screen.
     *
     * setCurrent is asynchronous and full-screen mode is not applied until the
     * canvas is shown, so getWidth and getHeight are not to be trusted before
     * this returns. Reading them early gave a zero height, hence a negative row
     * count, hence a paint loop that never ran — a blank terminal under a
     * status line that drew perfectly well.
     */
    public synchronized void awaitShown(long milliseconds) {
        long deadline = System.currentTimeMillis() + milliseconds;
        while (!shown) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return;
            }
            try {
                wait(left);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    protected synchronized void showNotify() {
        shown = true;
        notifyAll();
    }

    protected void sizeChanged(int width, int height) {
        synchronized (this) {
            shown = true;
            notifyAll();
        }
        repaint();
    }

    /**
     * How many columns of this font fit the canvas.
     *
     * Never below one. A zero or negative grid is not a smaller terminal, it is
     * a terminal that draws nothing, and it should not be reachable by
     * arithmetic on a size that has not arrived yet.
     */
    public int columns() {
        return atLeastOne(font.columnsIn(getWidth()));
    }

    /** Rows, less one kept for the status line. */
    public int rows() {
        // The status line on top and the key bar below are not terminal.
        return atLeastOne((getHeight() - font.cellHeight() - barHeight()) / font.cellHeight());
    }

    private static int atLeastOne(int n) {
        return n < 1 ? 1 : n;
    }

    /**
     * Shows the raw code of each key pressed instead of the status message.
     *
     * This is the measurement issue #11 has been waiting for. MIDP guarantees
     * nothing about what a BlackBerry sends for its own Escape, Menu, Symbol
     * and Alt keys, and putting the readout in the app itself means the numbers
     * can be read off the hardware in the place they matter rather than from a
     * separate probe.
     */
    /**
     * Moves the view back through the scrollback, a screen at a time.
     *
     * Bounded by what the buffer actually holds rather than by a guess: asking
     * for a line that was never kept would draw whatever the array happens to
     * contain there.
     */
    public void pageUp() {
        int available = terminal == null ? 0 : terminal.screenBase;
        scrollOffset += rows() - 1;
        if (scrollOffset > available) {
            scrollOffset = available;
        }
        repaint();
    }

    public void pageDown() {
        scrollOffset -= rows() - 1;
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        repaint();
    }

    /**
     * Returns to the live screen.
     *
     * Called whenever a key is typed. Reading history and then typing blind
     * into a screen that is not the one being shown is worse than not being
     * able to scroll at all.
     */
    public void toLive() {
        if (scrollOffset != 0) {
            scrollOffset = 0;
            repaint();
        }
    }

    public boolean isScrolledBack() {
        return scrollOffset > 0;
    }

    public void toggleKeyCodes() {
        showKeyCodes = !showKeyCodes;
        repaint();
    }

    public void setStatus(String message) {
        status = message == null ? "" : message;
        repaint();
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        int cellHeight = font.cellHeight();
        int top = cellHeight;

        int background = theme.background();
        g.setColor(background);
        g.fillRect(0, 0, width, height);

        boolean sliding = false;
        if (terminal != null) {
            int columns = columns();
            int rows = rows();
            synchronized (terminal.getTermBufferMutex()) {
                synchronized (this) {
                    update(rows, columns, width);
                }
            }
            int offset = 0;
            if (slideRows > 0) {
                long elapsed = System.currentTimeMillis() - slideStart;
                if (elapsed >= slideDuration) {
                    finishSlide();
                } else {
                    offset = RowDiff.slideOffset(elapsed, slideDuration, slideRows * cellHeight);
                    sliding = true;
                }
            }
            g.setClip(0, top, width, rows * cellHeight);
            for (int row = 0; row < rows && row < rowImages.length; row++) {
                if (rowImages[row] != null) {
                    g.drawImage(rowImages[row], 0, top + row * cellHeight + offset,
                        Graphics.TOP | Graphics.LEFT);
                }
            }
            if (sliding) {
                for (int i = 0; i < outgoing.length; i++) {
                    if (outgoing[i] != null) {
                        g.drawImage(outgoing[i], 0,
                            top + (i - slideRows) * cellHeight + offset,
                            Graphics.TOP | Graphics.LEFT);
                    }
                }
            }
            g.setClip(0, 0, width, height);
        }

        // The status line sits on top, as tmux's does in the picture this
        // look is taken from.
        String line = showKeyCodes ? lastKey : status;
        if (keyboard != null && keyboard.controlPending()) {
            line = "^ " + line;
        }
        if (scrollOffset > 0) {
            line = "-" + scrollOffset + " " + line;
        }
        line = "[blackberry] " + line;
        g.setColor(theme.statusBar());
        g.fillRect(0, 0, width, cellHeight);
        font.setColours(theme.statusText(), theme.statusBar());
        for (int i = 0; i < line.length() && i < columns(); i++) {
            font.drawChar(g, line.charAt(i), i * font.cellWidth(), 0);
        }

        if (panel != null) {
            drawPanel(g, width);
        }
        if (keyBar) {
            drawKeyBar(g, width, height);
        }

        if (sliding) {
            repaint();
        }
    }

    private void drawKeyBar(Graphics g, int width, int height) {
        int bar = barHeight();
        int top = height - bar;
        int n = Keys.BAR.length;
        g.setColor(theme.statusBar());
        g.fillRect(0, top, width, bar);
        for (int i = 0; i < n; i++) {
            int x0 = i * width / n;
            int x1 = (i + 1) * width / n;
            boolean lit = i == pressedKey
                || (Keys.BAR[i].kind == Keys.CONTROL && keyboard != null && keyboard.controlPending())
                || (Keys.BAR[i].kind == Keys.ACTION && panel != null);
            int face = lit ? theme.colour(13) : theme.statusBar();
            int text = lit ? theme.background() : theme.foreground();
            if (lit) {
                g.setColor(face);
                g.fillRect(x0 + 1, top + 1, x1 - x0 - 2, bar - 2);
            }
            if (i > 0) {
                g.setColor(theme.dim(theme.statusText()));
                g.drawLine(x0, top + 6, x0, top + bar - 7);
            }
            String label = Keys.BAR[i].label;
            int labelWidth = label.length() * font.cellWidth();
            int x = x0 + (x1 - x0 - labelWidth) / 2;
            int y = top + (bar - font.cellHeight()) / 2;
            font.setColours(text, face);
            for (int c = 0; c < label.length(); c++) {
                font.drawChar(g, label.charAt(c), x + c * font.cellWidth(), y);
            }
        }
        g.setColor(theme.dim(theme.statusText()));
        g.drawLine(0, top, width, top);
    }

    /** Which bar key is under this point, or -1. */
    private int keyAt(int x, int y) {
        if (!keyBar || y < getHeight() - barHeight()) {
            return -1;
        }
        int index = x * Keys.BAR.length / getWidth();
        return index < 0 || index >= Keys.BAR.length ? -1 : index;
    }

    protected void pointerPressed(int x, int y) {
        if (panel != null) {
            int cell = panelCellAt(x, y);
            if (cell != -2) {
                pressedPanel = cell;
                dragY = -1;
                repaint();
                return;
            }
        }
        pressedKey = keyAt(x, y);
        dragY = pressedKey < 0 ? y : -1;
        dragged = false;
        repaint();
    }

    /** A drag on the text scrolls back through what went past, a line at a time. */
    protected void pointerDragged(int x, int y) {
        if (pressedPanel >= 0) {
            if (panelCellAt(x, y) != pressedPanel) {
                pressedPanel = -1;
                repaint();
            }
            return;
        }
        if (pressedKey >= 0) {
            if (keyAt(x, y) != pressedKey) {
                pressedKey = -1;
                repaint();
            }
            return;
        }
        if (dragY < 0 || terminal == null) {
            return;
        }
        int lines = (y - dragY) / font.cellHeight();
        if (lines != 0) {
            dragged = true;
            dragY += lines * font.cellHeight();
            scrollOffset += lines;
            if (scrollOffset > terminal.screenBase) {
                scrollOffset = terminal.screenBase;
            }
            if (scrollOffset < 0) {
                scrollOffset = 0;
            }
            repaint();
        }
    }

    protected void pointerReleased(int x, int y) {
        if (pressedPanel >= 0 || (panel != null && panelCellAt(x, y) != -2)) {
            int cell = pressedPanel;
            pressedPanel = -1;
            if (panel != null && cell >= 0 && panelCellAt(x, y) == cell) {
                panelFocus = cell;
                activate(panel[cell]);
            }
            repaint();
            return;
        }
        int key = pressedKey;
        pressedKey = -1;
        if (key >= 0 && keyAt(x, y) == key && Keys.BAR[key].kind == Keys.ACTION) {
            activate(Keys.BAR[key]);
        } else if (key >= 0 && keyAt(x, y) == key) {
            if (keyboard != null) {
                toLive();
                int action = keyboard.perform(Keys.BAR[key]);
                if (action != 0 && listener != null) {
                    listener.action(action);
                }
            } else if (Keys.BAR[key].kind == Keys.ACTION && listener != null) {
                listener.action(Keys.BAR[key].code);
            }
        } else if (dragY >= 0 && !dragged) {
            // A tap on the text brings a scrolled-back view to the present.
            toLive();
        }
        dragY = -1;
        repaint();
    }

    /**
     * Brings the row pictures up to date with the terminal.
     *
     * Every row is hashed; a row whose hash some picture already has gets that
     * picture, and only the rest are drawn. When most rows turn out to be the
     * rows below them a moment ago, the screen scrolled, and the move is shown
     * as a slide.
     */
    private void update(int rows, int columns, int width) {
        int cellHeight = font.cellHeight();
        if (rowImages.length != rows || cacheWidth != width || cacheCellHeight != cellHeight) {
            rowImages = new Image[rows];
            rowHashes = new long[rows];
            rowBlank = new boolean[rows];
            spare = new Image[rows * 2];
            spareCount = 0;
            outgoing = new Image[0];
            slideRows = 0;
            cacheWidth = width;
            cacheCellHeight = cellHeight;
        }

        long[][] data = terminal.terminalData;
        int base = terminal.screenBase - scrollOffset;
        if (base < 0) {
            base = 0;
        }
        boolean live = scrollOffset == 0;
        long[] hashes = new long[rows];
        boolean[] blank = new boolean[rows];
        for (int row = 0; row < rows; row++) {
            int line = base + row;
            long[] cells = (data != null && line >= 0 && line < data.length) ? data[line] : null;
            boolean cursor = live && row == terminal.ROW;
            hashes[row] = RowDiff.hash(cells, columns, cursor ? terminal.COLUMN : -1);
            blank[row] = !cursor && RowDiff.blank(cells, columns);
        }

        boolean changed = false;
        for (int row = 0; row < rows; row++) {
            if (rowImages[row] == null || hashes[row] != rowHashes[row]) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        // New output during a slide: land the slide and start from here.
        if (slideRows > 0) {
            finishSlide();
        }

        int shift = 0;
        if (smoothScroll && live) {
            boolean[] drawn = new boolean[rows];
            for (int row = 0; row < rows; row++) {
                drawn[row] = rowImages[row] != null;
            }
            shift = RowDiff.scrollDistance(hashes, blank, rowHashes, rowBlank, drawn);
        }

        Image[] next = new Image[rows];
        boolean[] taken = new boolean[rows];
        for (int row = 0; row < rows; row++) {
            for (int old = 0; old < rows; old++) {
                if (!taken[old] && rowImages[old] != null && rowHashes[old] == hashes[row]) {
                    next[row] = rowImages[old];
                    taken[old] = true;
                    break;
                }
            }
        }
        if (shift > 0) {
            outgoing = new Image[shift];
            for (int i = 0; i < shift; i++) {
                outgoing[i] = taken[i] ? null : rowImages[i];
                taken[i] = true;
            }
            slideRows = shift;
            slideStart = System.currentTimeMillis();
            slideDuration = RowDiff.slideDuration(shift);
        }
        for (int old = 0; old < rows; old++) {
            if (!taken[old] && rowImages[old] != null) {
                keepSpare(rowImages[old]);
            }
        }
        for (int row = 0; row < rows; row++) {
            if (next[row] == null) {
                next[row] = takeSpare(width, cellHeight);
                drawRow(next[row].getGraphics(), data, base + row, row, columns, live, width);
            }
        }
        rowImages = next;
        rowHashes = hashes;
        rowBlank = blank;
    }

    private void finishSlide() {
        for (int i = 0; i < outgoing.length; i++) {
            if (outgoing[i] != null) {
                keepSpare(outgoing[i]);
            }
        }
        outgoing = new Image[0];
        slideRows = 0;
    }

    private void keepSpare(Image image) {
        if (spareCount < spare.length) {
            spare[spareCount++] = image;
        }
    }

    private Image takeSpare(int width, int height) {
        if (spareCount > 0) {
            Image image = spare[--spareCount];
            spare[spareCount] = null;
            return image;
        }
        return Image.createImage(width, height);
    }

    private void drawRow(Graphics g, long[][] data, int line, int row, int columns,
                         boolean live, int width) {
        int background = theme.background();
        g.setColor(background);
        g.fillRect(0, 0, width, font.cellHeight());
        if (data == null || line < 0 || line >= data.length) {
            return;
        }
        long[] cells = data[line];
        for (int column = 0; column < columns && column < cells.length; column++) {
            long cell = cells[column];
            drawCell(g, (char) cell, (int) (cell >>> 32), column * font.cellWidth(), 0);
        }
        if (live && row == terminal.ROW) {
            int column = terminal.COLUMN;
            if (column >= 0 && column < columns) {
                int x = column * font.cellWidth();
                char c = column < cells.length ? (char) cells[column] : ' ';
                g.setColor(theme.cursor());
                g.fillRect(x, 0, font.cellWidth(), font.cellHeight());
                if (c > 32) {
                    font.setColours(background, theme.cursor());
                    font.drawChar(g, c, x, 0);
                }
            }
        }
    }

    private void drawCell(Graphics g, char c, int attributes, int x, int y) {
        int foregroundIndex = ((attributes & VT320.COLOR_FG) >>> VT320.FG_SHIFT) - 1;
        int backgroundIndex = ((attributes & VT320.COLOR_BG) >>> VT320.BG_SHIFT) - 1;
        // Bold brightens the eight base colours, as xterm does by default.
        if ((attributes & BOLD) != 0 && foregroundIndex >= 0 && foregroundIndex < 8) {
            foregroundIndex += 8;
        }
        int foreground = foregroundIndex < 0 ? theme.foreground() : theme.colour(foregroundIndex);
        int background = backgroundIndex < 0 ? theme.background() : theme.colour(backgroundIndex);
        if ((attributes & LOW) != 0) {
            foreground = theme.dim(foreground);
        }
        if ((attributes & INVERT) != 0) {
            int swap = foreground;
            foreground = background;
            background = swap;
        }
        if (background != theme.background()) {
            g.setColor(background);
            g.fillRect(x, y, font.cellWidth(), font.cellHeight());
        }
        if (c > 32) {
            font.setColours(foreground, background);
            font.drawChar(g, c, x, y);
        }
        if ((attributes & UNDERLINE) != 0) {
            g.setColor(foreground);
            g.drawLine(x, y + font.cellHeight() - 1, x + font.cellWidth() - 1, y + font.cellHeight() - 1);
        }
    }

    protected void keyPressed(int keyCode) {
        deliver(keyCode);
    }

    /**
     * Key repeat is available on this device and is wanted: holding a cursor
     * key should scroll rather than move once.
     */
    protected void keyRepeated(int keyCode) {
        deliver(keyCode);
    }

    private void deliver(int keyCode) {
        int action = 0;
        try {
            action = getGameAction(keyCode);
        } catch (IllegalArgumentException e) {
            // MIDP allows this for a code it does not recognise, and a QWERTY
            // handset produces plenty it does not. The key is still a key.
            action = 0;
        }

        // Recorded before anything can go wrong with it, and whether or not
        // there is a session yet: a key that produces no output is exactly the
        // case this readout exists to explain.
        lastKey = "key " + keyCode + " action " + action
            + " grid " + columns() + "x" + rows();

        if (panel != null) {
            panelKey(keyCode, action);
            repaint();
            return;
        }
        if (keyboard != null) {
            toLive();
            keyboard.keyPressed(keyCode, action);
        }
        repaint();
    }
}
