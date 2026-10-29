import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * The application icon and the logo in docs/: the berry from
 * ssh/src/berryssh/device/Berry.java, scaled up by whole pixels so it stays
 * sharp. Keep the art and palette in step with that file.
 *
 * Host-only build tool. Not part of the MIDlet.
 *
 *   java tools/MakeIcon.java <size> <out.png> [background, e.g. 141413]
 *
 * Without a background the icon is transparent, and the eyes with it.
 */
public final class MakeIcon {

    static final String[] ART = {
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

    static int colour(char c, int background) {
        switch (c) {
            case 'H': return 0xfff2a485;
            case 'O': return 0xffd97757;
            case 'D': return 0xffa84f34;
            case 'K': return background;
            case 'G': return 0xff8fa86a;
            case 'g': return 0xff5e7245;
            default: return background;
        }
    }

    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]);
        int background = args.length > 2 ? 0xff000000 | Integer.parseInt(args[2], 16) : 0;
        int scale = Math.max(1, (size - 4) / Math.max(ART[0].length(), ART.length));
        int left = (size - ART[0].length() * scale) / 2;
        int top = (size - ART.length * scale) / 2;
        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                icon.setRGB(x, y, background);
            }
        }
        for (int row = 0; row < ART.length; row++) {
            for (int column = 0; column < ART[row].length(); column++) {
                int c = colour(ART[row].charAt(column), background);
                for (int y = 0; y < scale; y++) {
                    for (int x = 0; x < scale; x++) {
                        icon.setRGB(left + column * scale + x, top + row * scale + y, c);
                    }
                }
            }
        }
        ImageIO.write(icon, "png", new File(args[1]));
    }
}
