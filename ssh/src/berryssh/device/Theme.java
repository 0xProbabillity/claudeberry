package berryssh.device;

/**
 * The colours a session is drawn in.
 *
 * Free of MIDP, like {@link Profile}, so the palette arithmetic can be tested
 * on the host.
 *
 * The first sixteen palette entries belong to the theme; 16 to 255 are the
 * xterm cube and grey ramp, which every program that asks for one of them
 * expects to be exactly those colours.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Theme {

    /** Claude Code's dark look: warm greys, and its orange for the accent. */
    public static final Theme CLAUDE = new Theme("Claude",
        0xe5e5e5, 0x171717, 0xd4d4d4, 0xaaaaaa, 0x282828, new int[] {
            0x171717, 0xe06c75, 0xa6bf8e, 0xd7ba7d,
            0x91aec6, 0xc49bd6, 0x88c0d0, 0xd6d6d6,
            0x5c5c5c, 0xff7b72, 0xb0c897, 0xe5c07b,
            0xa5c4dc, 0xd99b7d, 0x9ad7e3, 0xffffff },
        // Claude Code's orange is rgb(215,119,87). Rounded into 256 colours
        // it arrives as 174 (its own conversion) or 173 (the nearest entry),
        // both pinker than the real thing, so both get the real thing back.
        new int[] { 173, 0xd97757, 174, 0xd97757 });

    /** What berryssh drew before there were themes. */
    public static final Theme CLASSIC = new Theme("Classic",
        0xffffff, 0x000000, 0xffffff, 0x808080, 0x000000, new int[] {
            0x000000, 0xcd0000, 0x00cd00, 0xcdcd00,
            0x0000ee, 0xcd00cd, 0x00cdcd, 0xe5e5e5,
            0x7f7f7f, 0xff0000, 0x00ff00, 0xffff00,
            0x5c5cff, 0xff00ff, 0x00ffff, 0xffffff });

    public static final Theme AMBER = new Theme("Amber",
        0xffb000, 0x120c00, 0xffb000, 0x9a6a00, 0x241800, new int[] {
            0x120c00, 0xff6a3d, 0xd9a400, 0xffc640,
            0xcc8f00, 0xff9a52, 0xffcf70, 0xffb000,
            0x6b4a00, 0xff8a5c, 0xffc21a, 0xffd970,
            0xe6a600, 0xffb27a, 0xffe0a0, 0xffe6b3 });

    public static final Theme GREEN = new Theme("Green",
        0x33ff66, 0x001a08, 0x33ff66, 0x1f9a3d, 0x002a0d, new int[] {
            0x001a08, 0x7aff5c, 0x33ff66, 0xb3ff66,
            0x1fcc52, 0x66ff99, 0x99ffbb, 0x33ff66,
            0x0f5c24, 0xa3ff8c, 0x66ff8c, 0xccff99,
            0x4de67a, 0x8cffb3, 0xbfffd4, 0xd9ffe6 });

    public static final Theme LIGHT = new Theme("Light",
        0x1f1f1f, 0xf7f5f2, 0x1f1f1f, 0x6b6b6b, 0xe6e2dc, new int[] {
            0x1f1f1f, 0xc0392b, 0x2e7d32, 0x9a6700,
            0x1f5fa8, 0x8e44ad, 0x00838f, 0x6b6b6b,
            0x4a4a4a, 0xe74c3c, 0x43a047, 0xb7791f,
            0x3b82c4, 0xc65d3b, 0x0097a7, 0x9e9e9e });

    public static final Theme[] ALL = { CLAUDE, CLASSIC, AMBER, GREEN, LIGHT };

    private final String name;
    private final int foreground;
    private final int background;
    private final int cursor;
    private final int statusText;
    private final int statusBar;
    private final int[] ansi;
    /** Pairs of palette index and colour, replacing the xterm value. */
    private final int[] overrides;

    private Theme(String name, int foreground, int background, int cursor,
                  int statusText, int statusBar, int[] ansi) {
        this(name, foreground, background, cursor, statusText, statusBar, ansi, new int[0]);
    }

    private Theme(String name, int foreground, int background, int cursor,
                  int statusText, int statusBar, int[] ansi, int[] overrides) {
        this.name = name;
        this.foreground = foreground;
        this.background = background;
        this.cursor = cursor;
        this.statusText = statusText;
        this.statusBar = statusBar;
        this.ansi = ansi;
        this.overrides = overrides;
    }

    /** This theme with the default text and background replaced. */
    public Theme withDefaults(int foreground, int background) {
        int[] copy = new int[ansi.length];
        System.arraycopy(ansi, 0, copy, 0, ansi.length);
        if (background != this.background) {
            // Black is the background on every palette above; following the
            // override keeps "black on default" from showing as a slab.
            copy[0] = background;
        }
        int cursor = foreground == this.foreground ? this.cursor : foreground;
        return new Theme(name, foreground, background, cursor, statusText, statusBar, copy, overrides);
    }

    public String name() {
        return name;
    }

    public int foreground() {
        return foreground;
    }

    public int background() {
        return background;
    }

    public int cursor() {
        return cursor;
    }

    public int statusText() {
        return statusText;
    }

    public int statusBar() {
        return statusBar;
    }

    /** An xterm-256 palette entry, 0 to 255. */
    public int colour(int index) {
        if (index < 16) {
            return ansi[index < 0 ? 0 : index];
        }
        for (int i = 0; i < overrides.length; i += 2) {
            if (overrides[i] == index) {
                return overrides[i + 1];
            }
        }
        if (index < 232) {
            int i = index - 16;
            return (level(i / 36) << 16) | (level((i / 6) % 6) << 8) | level(i % 6);
        }
        int grey = 8 + 10 * (index - 232);
        return (grey << 16) | (grey << 8) | grey;
    }

    /** Halfway to the background, for SGR 2. */
    public int dim(int colour) {
        return mix(colour, background);
    }

    private static int level(int step) {
        return step == 0 ? 0 : 55 + step * 40;
    }

    private static int mix(int a, int b) {
        int r = (((a >> 16) & 0xff) + ((b >> 16) & 0xff)) / 2;
        int g = (((a >> 8) & 0xff) + ((b >> 8) & 0xff)) / 2;
        int bl = ((a & 0xff) + (b & 0xff)) / 2;
        return (r << 16) | (g << 8) | bl;
    }
}
