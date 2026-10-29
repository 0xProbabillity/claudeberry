package berryssh.device;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Who made this and what it is made of. Any key or a tap goes back.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class AboutScreen extends Canvas {

    /** What closing the screen does. */
    public interface Listener {
        void closed();
    }

    private final String version;
    private final String badge;
    private final String server;
    private final Listener listener;

    private static final String[] CREDITS = {
        "Built on berryssh by cobanov, GPL-2.0",
        "Terminal: VT320 from JTA, via BBSSH",
        "Font: DejaVu Sans Mono",
        "Voice: Whisper, on your own server"
    };

    public AboutScreen(String version, String badge, String server, Listener listener) {
        this.version = version;
        this.badge = badge == null ? "" : badge;
        this.server = server == null ? "" : server;
        this.listener = listener;
        setFullScreenMode(false);
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        g.setColor(Chrome.BACKGROUND);
        g.fillRect(0, 0, width, height);

        int scale = 4;
        int y = Chrome.PADDING * 3;
        Berry.draw(g, (width - Berry.width(scale)) / 2, y, scale, Chrome.BACKGROUND);
        y += Berry.height(scale) + Chrome.PADDING * 3;

        Font big = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
        Chrome.drawName(g, big, "ClaudeBerry", width / 2, y, Graphics.TOP | Graphics.HCENTER);
        y += big.getHeight();
        y = centred(g, Chrome.detail(), Chrome.MUTED, version, width, y);
        y = centred(g, Chrome.detail(), Chrome.MUTED, "SSH and Claude Code on a BlackBerry", width, y);
        y += Chrome.PADDING;
        if (badge.length() > 0) {
            y = centred(g, Chrome.label(), Chrome.ACCENT, badge, width, y);
            String handle = badge.startsWith("@") ? badge.substring(1) : badge;
            y = centred(g, Chrome.detail(), Chrome.MUTED, "x.com/" + handle, width, y);
            y += Chrome.PADDING;
        }
        if (server.length() > 0) {
            y = centred(g, Chrome.detail(), Chrome.TEXT, server, width, y);
            y += Chrome.PADDING;
        }

        g.setColor(Chrome.RULE);
        g.drawLine(width / 4, y, width * 3 / 4, y);
        y += Chrome.PADDING;
        for (int i = 0; i < CREDITS.length; i++) {
            y = centred(g, Chrome.detail(), Chrome.MUTED, CREDITS[i], width, y);
        }

        Chrome.drawFooter(g, width, height, "Any key or tap to go back");
    }

    private static int centred(Graphics g, Font font, int colour, String text, int width, int y) {
        g.setFont(font);
        g.setColor(colour);
        g.drawString(Chrome.clip(text, font, width - Chrome.PADDING * 2), width / 2, y,
            Graphics.TOP | Graphics.HCENTER);
        return y + font.getHeight();
    }

    protected void keyPressed(int keyCode) {
        listener.closed();
    }

    protected void pointerReleased(int x, int y) {
        listener.closed();
    }
}
