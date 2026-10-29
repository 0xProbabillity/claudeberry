package berryssh.device;

import javax.microedition.lcdui.Graphics;

/**
 * The block-character creature Claude Code greets you with, drawn as pixels:
 * the same quadrant characters as tools/MakeIcon.java, for the screens that are
 * not a terminal.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Mascot {

    public static final int ORANGE = 0xd97757;

    /** Upper left, upper right, lower left, lower right, per character cell. */
    private static final int[][] ART = {
        { 0, 10, 7, 15, 15, 15, 11, 5, 0 },
        { 2, 11, 15, 15, 15, 15, 15, 7, 1 },
        { 0, 0, 1, 1, 0, 2, 2, 0, 0 }
    };

    private Mascot() {
    }

    /** Width for a quadrant this many pixels wide. */
    public static int width(int quadrant) {
        return ART[0].length * 2 * quadrant;
    }

    /** Height for a quadrant this many pixels tall. */
    public static int height(int quadrant) {
        return ART.length * 2 * quadrant;
    }

    /**
     * Draws with its top left at (x, y). A terminal quadrant is taller than it
     * is wide, so the two sizes are separate; 4 by 7 looks as it does in an
     * 8x14 cell.
     */
    public static void draw(Graphics g, int x, int y, int qw, int qh) {
        g.setColor(ORANGE);
        for (int row = 0; row < ART.length; row++) {
            for (int column = 0; column < ART[row].length; column++) {
                int q = ART[row][column];
                for (int part = 0; part < 4; part++) {
                    if ((q & (1 << part)) != 0) {
                        g.fillRect(x + (column * 2 + (part & 1)) * qw,
                            y + (row * 2 + (part >> 1)) * qh, qw, qh);
                    }
                }
            }
        }
    }
}
