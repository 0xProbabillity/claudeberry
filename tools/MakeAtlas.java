import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Generates a terminal font atlas.
 *
 * The atlas that shipped first came from BBSSH and covered U+0020 to U+00FF and
 * nothing else — which left the four Turkish letters from Latin Extended-A and
 * the whole box-drawing range with no glyph. Generating it means the coverage
 * is a decision rather than an inheritance.
 *
 * The character set is defined by {@link #RANGES} and must stay identical to
 * the table in berryssh.terminal.BitmapFont. Cells are laid out row-major in
 * the order the ranges appear, and the index of a character is its position in
 * that sequence — so adding a range in the middle renumbers everything after
 * it, and both sides have to move together.
 *
 * Rendered with LCD subpixel antialiasing, because that is what the renderer
 * expects: it blends the red, green and blue channels separately, treating each
 * as coverage for one subpixel. A greyscale atlas still works — the three
 * channels simply carry the same value — but it throws away the horizontal
 * resolution that makes an eight-pixel glyph legible.
 *
 * Host-only build tool. Not part of the MIDlet.
 *
 *   java tools/MakeAtlas.java <font.ttf> <cellWidth> <cellHeight> <out.png>
 */
public final class MakeAtlas {

    /**
     * Start and end of each range, inclusive. Kept deliberately small: every
     * cell costs atlas space, and the atlas is loaded whole into memory on a
     * device where that used to matter.
     */
    static final int[] RANGES = {
        0x0020, 0x007E,   // ASCII printable
        0x00A0, 0x00FF,   // Latin-1 supplement: covers ç Ç ö Ö ü Ü
        0x011E, 0x011F,   // Ğ ğ
        0x0130, 0x0131,   // İ ı
        0x015E, 0x015F,   // Ş ş
        0x2500, 0x257F,   // box drawing
        0x2580, 0x259F,   // block elements
        // What Claude Code draws, appended so the cells above keep their index
        0x2010, 0x2027,   // dashes, curly quotes, bullet, ellipsis
        0x2030, 0x203B,   // single angle quotes, reference mark
        0x2190, 0x21BB,   // arrows, return symbol
        0x2212, 0x221E,   // minus, bullet operator, root, infinity
        0x22EF, 0x22EF,   // midline ellipsis
        0x23AF, 0x23BF,   // line extension, the result connector
        0x23F4, 0x23FA,   // play, pause, record
        0x25A0, 0x25FF,   // geometric shapes: the tool dot, triangles, squares
        0x2605, 0x2606,   // stars
        0x2610, 0x2612,   // ballot boxes
        0x26A0, 0x26A1,   // warning, high voltage
        0x2713, 0x2718,   // check marks and crosses
        0x2722, 0x273D,   // the spinner's asterisks and stars
        0x2766, 0x2775,   // heavy angle brackets: the prompt chevron
        0x29C9, 0x29C9    // two joined squares
    };

    /**
     * Stand-ins for what DejaVu Sans Mono has no glyph for, drawn from a
     * character it does have that reads the same at this size. Claude Code's
     * result connector is the one that matters: without it every tool result
     * starts with an empty box.
     */
    static final int[] SUBSTITUTES = {
        0x23AF, 0x2500,   // line extension -> light horizontal
        0x23BF, 0x2514,   // the result connector -> light up and right
        0x23F4, 0x25C2,   // play left -> small left triangle
        0x23F5, 0x25B8,   // play -> small right triangle
        0x23F6, 0x25B4,   // play up -> small up triangle
        0x23F7, 0x25BE,   // play down -> small down triangle
        0x23F8, 0x2016,   // pause -> double vertical line
        0x23F9, 0x25A0,   // stop -> black square
        0x23FA, 0x25CF,   // record -> black circle
        0x2728, 0x2726,   // sparkles -> four pointed star
        0x29C9, 0x25A3    // two joined squares -> square in a square
    };

    static int substitute(int c) {
        for (int i = 0; i < SUBSTITUTES.length; i += 2) {
            if (SUBSTITUTES[i] == c) {
                return SUBSTITUTES[i + 1];
            }
        }
        return c;
    }

    /**
     * Block elements and the common box-drawing lines, as exact pixels.
     *
     * A font draws these inside its own ascent and descent, which on a cell
     * taller than the font leaves a gap of a pixel or two between rows. Text
     * does not notice; a picture built from blocks — Claude Code's mascot —
     * comes out striped, and a vertical rule comes out dashed.
     */
    static boolean drawShape(Graphics2D g, int c, int x, int y, int w, int h) {
        int hw = w / 2;
        int hh = h / 2;
        g.setColor(Color.WHITE);
        if (c >= 0x2580 && c <= 0x259F && (c < 0x2591 || c > 0x2593)) {
            if (c == 0x2580) {
                g.fillRect(x, y, w, hh);
            } else if (c >= 0x2581 && c <= 0x2588) {
                int part = (h * (c - 0x2580) + 4) / 8;
                g.fillRect(x, y + h - part, w, part);
            } else if (c >= 0x2589 && c <= 0x258F) {
                int part = (w * (0x2590 - c) + 4) / 8;
                g.fillRect(x, y, part, h);
            } else if (c == 0x2590) {
                g.fillRect(x + hw, y, w - hw, h);
            } else if (c == 0x2594) {
                g.fillRect(x, y, w, Math.max(1, h / 8));
            } else if (c == 0x2595) {
                g.fillRect(x + w - Math.max(1, w / 8), y, Math.max(1, w / 8), h);
            } else {
                // Quadrants: upper left, upper right, lower left, lower right.
                int[] masks = { 4, 8, 1, 13, 9, 7, 11, 2, 6, 14 };
                int mask = masks[c - 0x2596];
                if ((mask & 1) != 0) g.fillRect(x, y, hw, hh);
                if ((mask & 2) != 0) g.fillRect(x + hw, y, w - hw, hh);
                if ((mask & 4) != 0) g.fillRect(x, y + hh, hw, h - hh);
                if ((mask & 8) != 0) g.fillRect(x + hw, y + hh, w - hw, h - hh);
            }
            return true;
        }
        // The four arrows, as large as the cell allows: the font's are a few
        // pixels high, too small to read on a key.
        if (c >= 0x2190 && c <= 0x2193) {
            int cx = x + hw;
            int cy = y + hh;
            int head = Math.max(2, Math.min(hw, hh) - 1);
            if (c == 0x2190 || c == 0x2192) {
                int dir = c == 0x2190 ? -1 : 1;
                int tip = dir < 0 ? x + 1 : x + w - 2;
                g.fillRect(x + 1, cy, w - 2, 1);
                for (int i = 0; i <= head; i++) {
                    g.fillRect(tip - dir * i, cy - i, 1, 2 * i + 1);
                }
            } else {
                int dir = c == 0x2191 ? -1 : 1;
                int tip = dir < 0 ? y + 2 : y + h - 3;
                g.fillRect(cx, y + 2, 1, h - 4);
                for (int i = 0; i <= head; i++) {
                    g.fillRect(cx - i, tip - dir * i, 2 * i + 1, 1);
                }
            }
            return true;
        }
        // Box drawing: which arms reach the edge (up, down, left, right), and
        // whether they are heavy. Rounded corners are drawn square; at this
        // size the curve is a single pixel either way.
        int arms;
        boolean heavy = false;
        switch (c) {
            case 0x2500: arms = 12; break;
            case 0x2501: arms = 12; heavy = true; break;
            case 0x2502: arms = 3; break;
            case 0x2503: arms = 3; heavy = true; break;
            case 0x250C: case 0x256D: arms = 2 | 8; break;
            case 0x2510: case 0x256E: arms = 2 | 4; break;
            case 0x2514: case 0x2570: arms = 1 | 8; break;
            case 0x2518: case 0x256F: arms = 1 | 4; break;
            case 0x251C: arms = 1 | 2 | 8; break;
            case 0x2524: arms = 1 | 2 | 4; break;
            case 0x252C: arms = 2 | 4 | 8; break;
            case 0x2534: arms = 1 | 4 | 8; break;
            case 0x253C: arms = 15; break;
            default: return false;
        }
        int t = heavy ? 2 : 1;
        int cx = x + hw - (heavy ? 1 : 0);
        int cy = y + hh - (heavy ? 1 : 0);
        if ((arms & 1) != 0) g.fillRect(cx, y, t, cy - y + t);
        if ((arms & 2) != 0) g.fillRect(cx, cy, t, y + h - cy);
        if ((arms & 4) != 0) g.fillRect(x, cy, cx - x + t, t);
        if ((arms & 8) != 0) g.fillRect(cx, cy, x + w - cx, t);
        return true;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: MakeAtlas <font.ttf> <cellWidth> <cellHeight> <out.png> [fallback.ttf]");
            System.exit(2);
        }
        File source = new File(args[0]);
        int cellWidth = Integer.parseInt(args[1]);
        int cellHeight = Integer.parseInt(args[2]);
        File out = new File(args[3]);

        int count = 0;
        for (int i = 0; i < RANGES.length; i += 2) {
            count += RANGES[i + 1] - RANGES[i] + 1;
        }

        // 256 wide keeps the atlas the size the renderer already handles; the
        // height grows to whatever the character set needs.
        int columns = 256 / cellWidth;
        int rows = (count + columns - 1) / columns;
        int width = 256;
        int height = rows * cellHeight;

        Font base = Font.createFont(Font.TRUETYPE_FONT, source);
        Font font = fit(base, cellWidth, cellHeight);
        // Same family, proportional, for the few symbols the monospace face
        // lacks. Sized to the monospace font, so it sits on the same baseline.
        Font fallback = args.length == 5
            ? Font.createFont(Font.TRUETYPE_FONT, new File(args[4])).deriveFont(font.getSize2D())
            : null;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setFont(font);
        g.setColor(Color.WHITE);

        FontMetrics metrics = g.getFontMetrics();
        int baseline = (cellHeight - metrics.getHeight()) / 2 + metrics.getAscent();
        if (baseline < metrics.getAscent()) {
            baseline = metrics.getAscent();
        }
        if (baseline > cellHeight) {
            baseline = cellHeight;
        }

        int index = 0;
        int missing = 0;
        for (int r = 0; r < RANGES.length; r += 2) {
            for (int c = RANGES[r]; c <= RANGES[r + 1]; c++) {
                int x = (index % columns) * cellWidth;
                int y = (index / columns) * cellHeight;
                int drawn = font.canDisplay((char) c) ? c : substitute(c);
                Font face = font;
                if (!font.canDisplay((char) drawn) && fallback != null
                        && fallback.canDisplay((char) c)) {
                    face = fallback;
                    drawn = c;
                }
                if (drawShape(g, drawn, x, y, cellWidth, cellHeight)) {
                    // Drawn as pixels: see drawShape.
                } else if (!face.canDisplay((char) drawn)) {
                    missing++;
                } else {
                    g.setFont(face);
                    metrics = g.getFontMetrics();
                    String s = String.valueOf((char) drawn);
                    // Centred on the cell rather than left-aligned: the box
                    // drawing characters have to meet across a cell boundary,
                    // and a half-pixel offset is a visible break in a table.
                    int advance = metrics.charWidth((char) drawn);
                    // Clipped to the cell. Without it a tall glyph's
                    // antialiasing spills into the neighbour above or below,
                    // and since the atlas is read a cell at a time that spill
                    // appears as a smudge attached to an unrelated character.
                    g.setClip(x, y, cellWidth, cellHeight);
                    g.drawString(s, x + (cellWidth - advance) / 2, y + baseline);
                    g.setClip(null);
                }
                index++;
            }
        }
        g.dispose();

        ImageIO.write(image, "png", out);
        System.out.println(out.getName() + ": " + width + "x" + height
            + ", " + columns + "x" + rows + " cells, "
            + index + " characters at " + font.getSize() + "pt"
            + (missing > 0 ? ", " + missing + " the font cannot draw" : ""));
    }

    /**
     * The largest size whose widest glyph still fits the cell.
     *
     * Measured rather than calculated from the em size: a monospace font's
     * advance is not its ink extent, and a glyph one pixel too wide bleeds into
     * the next cell, which in a terminal means every column after it looks
     * wrong.
     */
    private static Font fit(Font base, int cellWidth, int cellHeight) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = probe.createGraphics();
        Font best = base.deriveFont(6f);
        for (float size = 6f; size <= 40f; size += 0.5f) {
            Font candidate = base.deriveFont(size);
            FontMetrics m = g.getFontMetrics(candidate);
            if (m.charWidth('M') <= cellWidth && m.getHeight() <= cellHeight) {
                best = candidate;
            }
        }
        g.dispose();
        return best;
    }
}
