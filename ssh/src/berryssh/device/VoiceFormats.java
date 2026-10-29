package berryssh.device;

/**
 * Which recording formats to ask the phone for, best first.
 *
 * MMAPI lets a device name its capture formats in the "audio.encodings"
 * system property, as space-separated locator parameters such as
 * "encoding=pcm&rate=8000 encoding=amr". What a BlackBerry actually lists, and
 * which of those an unsigned MIDlet may use, is not documented anywhere that
 * survives, so this takes what the device says, orders it for Whisper, and adds
 * the common spellings after it in case it said nothing useful.
 *
 * Free of MMAPI, so the ordering can be tested on the host.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class VoiceFormats {

    /** Whisper's own rate. Anything recorded at it needs no resampling. */
    private static final String[] FALLBACKS = {
        "encoding=pcm&rate=16000&bits=16&channels=1",
        "encoding=audio/wav&rate=16000",
        "encoding=pcm",
        "encoding=audio/amr",
        "encoding=amr",
        ""
    };

    private VoiceFormats() {
    }

    /** Capture locators to try in order, without duplicates. */
    public static String[] locators(String property) {
        String[] listed = split(property == null ? "" : property.trim());
        String[] all = new String[listed.length + FALLBACKS.length];
        int n = 0;
        // The device's own list, best first; then the guesses.
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < listed.length; i++) {
                if (rank(listed[i]) == pass) {
                    n = add(all, n, listed[i]);
                }
            }
        }
        for (int i = 0; i < FALLBACKS.length; i++) {
            n = add(all, n, FALLBACKS[i]);
        }
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = all[i].length() == 0 ? "capture://audio" : "capture://audio?" + all[i];
        }
        return out;
    }

    /** The sample rate a locator asks for, or 0 when it does not say. */
    public static int rate(String locator) {
        int at = locator.indexOf("rate=");
        if (at < 0) {
            return 0;
        }
        int end = at + 5;
        while (end < locator.length() && Character.isDigit(locator.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(locator.substring(at + 5, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 0: uncompressed at 16 kHz; 1: other uncompressed; 2: everything else. */
    private static int rank(String encoding) {
        String e = encoding.toLowerCase();
        boolean uncompressed = e.indexOf("pcm") >= 0 || e.indexOf("wav") >= 0;
        if (uncompressed && e.indexOf("rate=16000") >= 0) {
            return 0;
        }
        return uncompressed ? 1 : 2;
    }

    private static int add(String[] all, int n, String encoding) {
        for (int i = 0; i < n; i++) {
            if (all[i].equals(encoding)) {
                return n;
            }
        }
        all[n] = encoding;
        return n + 1;
    }

    private static String[] split(String text) {
        int count = 0;
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') {
                i++;
            }
            if (i < text.length()) {
                count++;
            }
            while (i < text.length() && text.charAt(i) != ' ') {
                i++;
            }
        }
        String[] out = new String[count];
        int n = 0;
        i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') {
                i++;
            }
            int start = i;
            while (i < text.length() && text.charAt(i) != ' ') {
                i++;
            }
            if (i > start) {
                out[n++] = text.substring(start, i);
            }
        }
        return out;
    }
}
