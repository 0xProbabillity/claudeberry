import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * The application icon: the block-character creature Claude Code greets you
 * with, read off its quadrant characters into pixels and scaled up.
 *
 * Host-only build tool. Not part of the MIDlet.
 *
 *   java tools/MakeIcon.java <size> <out.png>
 */
public final class MakeIcon {

    static final String[] ART = {
        " ▐▛███▜▌ ",
        "▝▜█████▛▘",
        "  ▘▘ ▝▝  "
    };

    static final int ORANGE = 0xffd97757;

    /** Quadrants lit by a character: upper left, upper right, lower left, lower right. */
    static int quadrants(char c) {
        switch (c) {
            case '█': return 15;
            case '▐': return 2 | 8;
            case '▌': return 1 | 4;
            case '▛': return 1 | 2 | 4;
            case '▜': return 1 | 2 | 8;
            case '▝': return 2;
            case '▘': return 1;
            default: return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]);
        int gridWidth = ART[0].length() * 2;
        int gridHeight = ART.length * 2;
        // A quadrant of a terminal cell is taller than it is wide (4x7 in an
        // 8x14 cell), and the creature is drawn for that shape.
        int scale = (size - 4) / gridWidth;
        int tall = scale * 7 / 4;
        int left = (size - gridWidth * scale) / 2;
        int top = (size - gridHeight * tall) / 2;
        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < ART.length; row++) {
            for (int column = 0; column < ART[row].length(); column++) {
                int q = quadrants(ART[row].charAt(column));
                for (int part = 0; part < 4; part++) {
                    if ((q & (1 << part)) == 0) {
                        continue;
                    }
                    int px = column * 2 + (part & 1);
                    int py = row * 2 + (part >> 1);
                    for (int y = 0; y < tall; y++) {
                        for (int x = 0; x < scale; x++) {
                            icon.setRGB(left + px * scale + x, top + py * tall + y, ORANGE);
                        }
                    }
                }
            }
        }
        ImageIO.write(icon, "png", new File(args[1]));
    }
}
