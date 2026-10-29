package berryssh.device;

import javax.microedition.lcdui.Graphics;

/**
 * ClaudeBerry's logo: a blackberry in Claude's colours that is also a
 * character, with the eye slots, stubby arms and four legs of the creature
 * Claude Code greets you with. Pixel art, so it stays sharp at
 * any whole-number scale on a screen that cannot smooth anything.
 *
 * tools/MakeIcon.java draws the same picture for the application icon and
 * docs/logo; keep the three in step.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Berry {

    /** One character per pixel; the letters are the palette below. */
    private static final String[] ART = {
        "...........g........",
        "..........g.........",
        ".....GG..gG..GG.....",
        "......GGGGGGGG......",
        "....GGGgGGGGgGGG....",
        ".....HOGGHOGGHO.....",
        "....HOODHOODHOOD....",
        "....OODDOODDOODD....",
        "...HODDHODDHODDHO...",
        "..HOODHOODHOODHOOD..",
        "..OODDOODDOODDOODD..",
        "...DDHOKKHOKKHODD...",
        ".OOOHOOKKOOKKOODOOO.",
        ".DDDOODKKODKKODDDDD.",
        "...HODDHODDHODDHO...",
        "..HOODHOODHOODHOOD..",
        "..OODDOODDOODDOODD..",
        "...DDHODDHODDHODD...",
        "....HOODHOODHOOD....",
        "....OODDOODDOODD....",
        ".....DDHODDHODD.....",
        "......HOODHOOD......",
        "......OODDOODD......",
        "......O.O..O.O......",
        "......D.D..D.D......"
    };

    private Berry() {
    }

    /** Width at this many screen pixels per art pixel. */
    public static int width(int scale) {
        return ART[0].length() * scale;
    }

    /** Height at this many screen pixels per art pixel. */
    public static int height(int scale) {
        return ART.length * scale;
    }

    /**
     * The colour of a pixel, or -1 where the background shows through. The
     * eyes are drawn in the background colour, as the creature's are gaps.
     */
    private static int colour(char c, int background) {
        switch (c) {
            case 'H': return 0xf2a485;   // highlight
            case 'O': return Chrome.ACCENT;
            case 'D': return 0xa84f34;   // shadow between drupelets
            case 'K': return background;
            case 'G': return 0x8fa86a;   // leaves
            case 'g': return 0x5e7245;   // leaf shade and stem
            default: return -1;
        }
    }

    /** Draws with its top left at (x, y), eyes in the given background. */
    public static void draw(Graphics g, int x, int y, int scale, int background) {
        for (int row = 0; row < ART.length; row++) {
            String line = ART[row];
            for (int column = 0; column < line.length(); column++) {
                int colour = colour(line.charAt(column), background);
                if (colour >= 0) {
                    g.setColor(colour);
                    g.fillRect(x + column * scale, y + row * scale, scale, scale);
                }
            }
        }
    }
}
