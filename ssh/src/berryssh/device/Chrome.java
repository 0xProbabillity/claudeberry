package berryssh.device;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * The palette and the furniture the drawn screens share.
 *
 * MIDP's own Form and List are drawn by the device, which means a white 2003
 * feature-phone menu in front of a terminal that looks nothing like it. A
 * Canvas gives every pixel, which is how the terminal is already drawn.
 *
 * Text is left to MIDP's Font rather than the terminal's bitmap atlas. The
 * atlas is monospace at eight pixels wide, which is right for a terminal grid
 * and wrong for a label; the system font is proportional and is rendered by
 * the device at whatever quality it manages natively.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Chrome {

    /** Claude Code's dark look, so the lists match the terminal behind them. */
    public static final int BACKGROUND = 0x141413;
    public static final int BAR = 0x1f1e1d;
    public static final int ROW = 0x1a1918;
    public static final int SELECTED = 0x3b2a22;
    public static final int ACCENT = 0xd97757;
    public static final int TEXT = 0xece9e4;
    public static final int MUTED = 0x8f8a83;
    public static final int RULE = 0x2c2a28;

    public static final int PADDING = 6;

    private Chrome() {
    }

    public static Font title() {
        return Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
    }

    public static Font label() {
        return Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
    }

    public static Font detail() {
        return Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /** Height of a two-line row: a name and what it reaches. */
    public static int rowHeight() {
        return label().getHeight() + detail().getHeight() + PADDING;
    }

    public static int barHeight() {
        return title().getHeight() + PADDING * 2;
    }

    /**
     * The home screen's header: the creature, the name, a line under it, and
     * a badge on the right. Returns the y the content starts at.
     */
    public static int drawBanner(Graphics g, int width, String name, String subtitle,
                                 String badge) {
        int qw = 3;
        int qh = 5;
        int height = Mascot.height(qh) + PADDING * 4;
        g.setColor(BAR);
        g.fillRect(0, 0, width, height);
        g.setColor(ACCENT);
        g.fillRect(0, height - 2, width, 2);
        int mascotTop = (height - 2 - Mascot.height(qh)) / 2;
        Mascot.draw(g, PADDING * 2, mascotTop, qw, qh);

        int x = PADDING * 4 + Mascot.width(qw);
        Font big = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
        int textTop = (height - 2 - big.getHeight() - detail().getHeight()) / 2;
        g.setColor(TEXT);
        g.setFont(big);
        g.drawString(name, x, textTop, Graphics.TOP | Graphics.LEFT);
        g.setColor(MUTED);
        g.setFont(detail());
        g.drawString(subtitle, x, textTop + big.getHeight(), Graphics.TOP | Graphics.LEFT);
        if (badge != null && badge.length() > 0) {
            g.setColor(ACCENT);
            g.setFont(label());
            g.drawString(badge, width - PADDING * 2, textTop + 2, Graphics.TOP | Graphics.RIGHT);
        }
        return height;
    }

    /** Draws the top bar and returns the y the content starts at. */
    public static int drawTitleBar(Graphics g, int width, String text) {
        int height = barHeight();
        g.setColor(BAR);
        g.fillRect(0, 0, width, height);
        g.setColor(ACCENT);
        g.fillRect(0, height - 2, width, 2);
        g.setColor(TEXT);
        g.setFont(title());
        g.drawString(text, PADDING, PADDING, Graphics.TOP | Graphics.LEFT);
        return height;
    }

    /** Draws the hint line at the foot and returns the y it starts at. */
    public static int drawFooter(Graphics g, int width, int height, String hint) {
        Font font = detail();
        int top = height - font.getHeight() - PADDING;
        g.setColor(BAR);
        g.fillRect(0, top, width, height - top);
        g.setColor(RULE);
        g.drawLine(0, top, width, top);
        g.setColor(MUTED);
        g.setFont(font);
        g.drawString(hint, PADDING, top + PADDING / 2, Graphics.TOP | Graphics.LEFT);
        return top;
    }

    /**
     * Draws one row.
     *
     * The detail line is clipped rather than wrapped: a hostname long enough to
     * wrap would push every row below it out of place, and a list whose rows
     * move as the contents change is harder to use than one that truncates.
     */
    public static void drawRow(Graphics g, int width, int y, String name, String detail,
                               boolean selected) {
        int height = rowHeight();
        g.setColor(selected ? SELECTED : ROW);
        g.fillRect(0, y, width, height - 1);
        if (selected) {
            g.setColor(ACCENT);
            g.fillRect(0, y, 3, height - 1);
        }

        g.setColor(TEXT);
        g.setFont(label());
        g.drawString(clip(name, label(), width - PADDING * 2),
            PADDING + 4, y + PADDING / 2, Graphics.TOP | Graphics.LEFT);

        if (detail != null && detail.length() > 0) {
            g.setColor(selected ? TEXT : MUTED);
            g.setFont(detail());
            g.drawString(clip(detail, detail(), width - PADDING * 2),
                PADDING + 4, y + PADDING / 2 + label().getHeight(),
                Graphics.TOP | Graphics.LEFT);
        }
    }

    /** Trims a string to fit, with an ellipsis, measuring as it goes. */
    public static String clip(String text, Font font, int width) {
        if (font.stringWidth(text) <= width) {
            return text;
        }
        for (int i = text.length() - 1; i > 0; i--) {
            String candidate = text.substring(0, i) + "...";
            if (font.stringWidth(candidate) <= width) {
                return candidate;
            }
        }
        return "";
    }
}
