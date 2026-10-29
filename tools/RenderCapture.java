import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import berryssh.device.Keys;
import berryssh.device.Theme;
import berryssh.protocol.Utf8;
import berryssh.terminal.VT320;

/**
 * Renders a captured terminal byte stream the way the handset would.
 *
 * The bytes go through the real VT320 in the same 4096-byte reads the session
 * loop uses, and each cell is drawn from the real atlas with the colour rules
 * of TerminalCanvas, which needs MIDP and so cannot run here. Keep the two in
 * step when either changes.
 *
 * Host-only tool. Compile against the client classes:
 *
 *   java -cp <classes> RenderCapture capture.bin out.png 80 31 [theme] [font] [bar|panel|symbols]
 *   java -Dstatus="[blackberry] me@box" -cp <classes> RenderCapture ...
 *
 * theme is an index into Theme.ALL, font a Profile font index.
 */
public final class RenderCapture {

    static final int[] WIDTHS = { 8, 6, 6, 10, 12 };
    static final int[] HEIGHTS = { 14, 11, 9, 18, 21 };

    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(new File(args[0]).toPath());
        int columns = Integer.parseInt(args[2]);
        int rows = Integer.parseInt(args[3]);
        Theme theme = Theme.ALL[args.length > 4 ? Integer.parseInt(args[4]) : 0];
        int font = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        int cw = WIDTHS[font];
        int ch = HEIGHTS[font];
        BufferedImage atlas = ImageIO.read(new File("ssh/res/fonts/mono" + cw + "x" + ch + ".png"));
        int atlasColumns = atlas.getWidth() / cw;

        VT320 t = new VT320(columns, rows) {
            public void sendData(byte[] b, int offset, int length) {
            }

            public void beep() {
            }

            public void resize() {
            }
        };
        for (int at = 0; at < data.length; ) {
            int n = Math.min(4096, data.length - at);
            int complete = Utf8.completeLength(data, at, n);
            if (complete == 0) {
                complete = n;
            }
            t.putString(Utf8.decode(data, at, complete));
            at += complete;
        }

        String overlay = args.length > 6 ? args[6] : "";
        int bar = overlay.length() == 0 ? 0 : Math.max(32, ch + 14);
        BufferedImage out = new BufferedImage(columns * cw, (rows + 1) * ch + bar, BufferedImage.TYPE_INT_RGB);
        fill(out, 0, 0, out.getWidth(), out.getHeight(), theme.background());
        for (int row = 0; row < rows; row++) {
            long[] cells = t.terminalData[t.screenBase + row];
            for (int column = 0; column < columns; column++) {
                long cell = cells[column];
                char c = (char) cell;
                int a = (int) (cell >>> 32);
                int fi = ((a & VT320.COLOR_FG) >>> VT320.FG_SHIFT) - 1;
                int bi = ((a & VT320.COLOR_BG) >>> VT320.BG_SHIFT) - 1;
                if ((a & VT320.BOLD) != 0 && fi >= 0 && fi < 8) {
                    fi += 8;
                }
                int fg = fi < 0 ? theme.foreground() : theme.colour(fi);
                int bg = bi < 0 ? theme.background() : theme.colour(bi);
                if ((a & VT320.LOW) != 0) {
                    fg = theme.dim(fg);
                }
                if ((a & VT320.INVERT) != 0) {
                    int s = fg;
                    fg = bg;
                    bg = s;
                }
                if (row == t.ROW && column == t.COLUMN) {
                    bg = theme.cursor();
                    fg = theme.background();
                }
                glyph(out, atlas, atlasColumns, cw, ch, c, column * cw, (row + 1) * ch, fg, bg);
                if ((a & VT320.UNDERLINE) != 0) {
                    fill(out, column * cw, (row + 1) * ch + ch - 1, cw, 1, fg);
                }
            }
        }
        // On top, as on the device.
        // -Dstatus="..." sets what the status line names; the device shows the profile.
        String status = System.getProperty("status", "[blackberry] you@your-server")
            + "  " + columns + "x" + rows;
        int y = 0;
        fill(out, 0, y, out.getWidth(), ch, theme.statusBar());
        for (int i = 0; i < status.length() && i < columns; i++) {
            glyph(out, atlas, atlasColumns, cw, ch, status.charAt(i), i * cw, y,
                theme.statusText(), theme.statusBar());
        }
        if (overlay.equals("panel")) {
            panel(out, atlas, atlasColumns, cw, ch, theme, Keys.PANEL, Keys.PANEL_COLUMNS);
        } else if (overlay.equals("symbols")) {
            panel(out, atlas, atlasColumns, cw, ch, theme, Keys.symbols(), Keys.SYMBOL_COLUMNS);
        }
        if (bar > 0) {
            int top = out.getHeight() - bar;
            int width = out.getWidth();
            fill(out, 0, top, width, bar, theme.statusBar());
            fill(out, 0, top, width, 1, theme.dim(theme.statusText()));
            for (int i = 0; i < Keys.BAR.length; i++) {
                int x0 = i * width / Keys.BAR.length;
                int x1 = (i + 1) * width / Keys.BAR.length;
                boolean lit = overlay.equals("panel") && Keys.BAR[i].kind == Keys.ACTION;
                int face = lit ? theme.colour(13) : theme.statusBar();
                if (lit) {
                    fill(out, x0 + 1, top + 1, x1 - x0 - 2, bar - 2, face);
                }
                if (i > 0) {
                    fill(out, x0, top + 6, 1, bar - 13, theme.dim(theme.statusText()));
                }
                String label = Keys.BAR[i].label;
                int x = x0 + (x1 - x0 - label.length() * cw) / 2;
                for (int c = 0; c < label.length(); c++) {
                    glyph(out, atlas, atlasColumns, cw, ch, label.charAt(c), x + c * cw,
                        top + (bar - ch) / 2, lit ? theme.background() : theme.foreground(), face);
                }
            }
        }
        ImageIO.write(out, "png", new File(args[1]));
    }

    /** The quick panel as TerminalCanvas draws it, focus on the first cell. */
    static void panel(BufferedImage out, BufferedImage atlas, int atlasColumns, int cw, int ch,
                      Theme theme, Keys.Entry[] page, int columns) {
        int width = out.getWidth();
        int top = ch;
        int cellHeight = Math.max(40, ch * 2 + 12);
        int rows = (page.length + columns - 1) / columns;
        int accent = theme.colour(13);
        fill(out, 0, top, width, rows * cellHeight, theme.statusBar());
        for (int i = 0; i < page.length; i++) {
            int x0 = (i % columns) * width / columns;
            int x1 = (i % columns + 1) * width / columns;
            int y0 = top + (i / columns) * cellHeight;
            boolean lit = i == 0;
            int cell = lit ? accent : theme.background();
            fill(out, x0 + 2, y0 + 2, x1 - x0 - 4, cellHeight - 4, cell);
            String label = page[i].label;
            int max = (x1 - x0 - 4) / cw;
            if (label.length() > max) {
                label = label.substring(0, max);
            }
            int x = x0 + (x1 - x0 - label.length() * cw) / 2;
            int y = y0 + (cellHeight - ch) / 2 + 2;
            for (int c = 0; c < label.length(); c++) {
                glyph(out, atlas, atlasColumns, cw, ch, label.charAt(c), x + c * cw, y,
                    lit ? theme.background() : theme.foreground(), cell);
            }
            char h = page[i].hotkey;
            String hint = h == 0 ? "" : h == '\r' ? "\u21b5" : h == ' ' ? "spc" : String.valueOf((char) (h - 'a' + 'A'));
            for (int c = 0; c < hint.length(); c++) {
                glyph(out, atlas, atlasColumns, cw, ch, hint.charAt(c), x0 + 6 + c * cw, y0 + 4,
                    lit ? theme.background() : accent, cell);
            }
        }
        fill(out, 0, top + rows * cellHeight, width, 1, accent);
    }

    static void glyph(BufferedImage out, BufferedImage atlas, int atlasColumns, int cw, int ch,
                      char c, int x, int y, int fg, int bg) {
        fill(out, x, y, cw, ch, bg);
        int index = berryssh.terminal.BitmapFont.indexOf(c);
        if (c <= 32 || index < 0) {
            return;
        }
        int ax = (index % atlasColumns) * cw;
        int ay = (index / atlasColumns) * ch;
        for (int dy = 0; dy < ch; dy++) {
            for (int dx = 0; dx < cw; dx++) {
                int p = atlas.getRGB(ax + dx, ay + dy);
                int r = blend(fg >> 16 & 0xff, bg >> 16 & 0xff, p >> 16 & 0xff);
                int g = blend(fg >> 8 & 0xff, bg >> 8 & 0xff, p >> 8 & 0xff);
                int b = blend(fg & 0xff, bg & 0xff, p & 0xff);
                out.setRGB(x + dx, y + dy, (r << 16) | (g << 8) | b);
            }
        }
    }

    static int blend(int f, int b, int coverage) {
        return (f * coverage + b * (255 - coverage)) / 255;
    }

    static void fill(BufferedImage out, int x, int y, int w, int h, int colour) {
        for (int j = y; j < y + h && j < out.getHeight(); j++) {
            for (int i = x; i < x + w && i < out.getWidth(); i++) {
                out.setRGB(i, j, colour);
            }
        }
    }
}
