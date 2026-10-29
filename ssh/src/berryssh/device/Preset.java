package berryssh.device;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import berryssh.protocol.Base64;
import berryssh.protocol.KnownHosts;
import berryssh.protocol.SshException;
import berryssh.protocol.Utf8;

/**
 * A connection baked into the jar, so the handset needs no typing at all.
 *
 * tools/make_preset.sh writes two resources: /preset/preset.txt, a few
 * key=value lines, and /preset/key, an OpenSSH Ed25519 private key. Neither is
 * in the repository, and a jar built with them is a credential — it is meant
 * to be copied onto one phone over USB, never served.
 *
 * The host key comes with it. Pinning it here is the same comparison the
 * first-contact prompt asks the user to make, done on the computer where the
 * server's real key could be read over an authenticated session.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class Preset {

    private static final String SETTINGS = "/preset/preset.txt";
    private static final String KEY = "/preset/key";

    private final Profile profile;
    private final byte[] hostKey;
    private final boolean autoConnect;

    private Preset(Profile profile, byte[] hostKey, boolean autoConnect) {
        this.profile = profile;
        this.hostKey = hostKey;
        this.autoConnect = autoConnect;
    }

    /** The preset in this jar, or null for a jar built without one. */
    public static Preset load() throws IOException {
        byte[] settings = read(SETTINGS);
        if (settings == null) {
            return null;
        }
        return parse(Utf8.decode(settings), read(KEY));
    }

    /** Separate from {@link #load} so the parsing can be tested on the host. */
    public static Preset parse(String settings, byte[] key) throws IOException {
        String name = "VPS";
        String host = null;
        int port = 22;
        String user = null;
        int fontSize = 0;
        String hostKey = "";
        boolean autoConnect = false;

        int start = 0;
        while (start < settings.length()) {
            int end = settings.indexOf('\n', start);
            if (end < 0) {
                end = settings.length();
            }
            String line = settings.substring(start, end).trim();
            start = end + 1;
            int eq = line.indexOf('=');
            if (line.length() == 0 || line.charAt(0) == '#' || eq < 0) {
                continue;
            }
            String field = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (field.equals("name")) {
                name = value;
            } else if (field.equals("host")) {
                host = value;
            } else if (field.equals("port")) {
                port = number(field, value);
            } else if (field.equals("user")) {
                user = value;
            } else if (field.equals("font")) {
                fontSize = number(field, value);
            } else if (field.equals("hostkey")) {
                hostKey = value;
            } else if (field.equals("autoconnect")) {
                autoConnect = value.equals("yes") || value.equals("true");
            }
        }
        if (host == null || user == null) {
            throw new SshException("the preset needs a host and a user");
        }

        String privateKey = key == null ? "" : Utf8.decode(key);
        Profile profile = new Profile(name, host, port, user, "", false,
            privateKey, fontSize, "", "", "");
        byte[] blob = hostKey.length() == 0 ? null : Base64.decode(hostKey);
        return new Preset(profile, blob, autoConnect);
    }

    public Profile profile() {
        return profile;
    }

    public boolean autoConnect() {
        return autoConnect;
    }

    /**
     * Saves the connection and pins the host key, unless they are already
     * there.
     *
     * A saved connection of the same name is left alone, so an edit made on
     * the handset survives a restart. A pinned key is never replaced: a jar
     * that could overwrite one would be a way past the changed-key refusal.
     */
    public void install(Profile.Store profiles, KnownHosts.Store hostKeys) throws IOException {
        Profile[] saved = profiles.list();
        boolean present = false;
        for (int i = 0; i < saved.length; i++) {
            if (saved[i].name().equals(profile.name())) {
                present = true;
            }
        }
        if (!present) {
            profiles.save(profile);
        }
        if (hostKey != null
                && hostKeys.lookup(profile.hostKeyIdentity(), profile.port()) == null) {
            hostKeys.store(profile.hostKeyIdentity(), profile.port(), hostKey);
        }
    }

    private static int number(String field, String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new SshException("preset " + field + " is not a number: " + value);
        }
    }

    private static byte[] read(String resource) throws IOException {
        InputStream in = Preset.class.getResourceAsStream(resource);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
