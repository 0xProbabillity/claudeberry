package berryssh.device;

import berryssh.protocol.SshException;

/**
 * Reads berryssh-transcribe's answer.
 *
 * Over an exec channel stdout and stderr arrive as one stream, so a library's
 * warning can come before the answer. The answer is the last line that starts
 * "OK " or "ERR ", and everything else is ignored.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class VoiceReply {

    private VoiceReply() {
    }

    /** The transcribed text, which may be empty when nothing was heard. */
    public static String parse(String output) throws SshException {
        String answer = null;
        int start = 0;
        while (start <= output.length()) {
            int end = output.indexOf('\n', start);
            if (end < 0) {
                end = output.length();
            }
            String line = output.substring(start, end);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.equals("OK") || line.startsWith("OK ") || line.startsWith("ERR ")) {
                answer = line;
            }
            start = end + 1;
        }
        if (answer == null) {
            String tail = output.trim();
            if (tail.indexOf("berryssh-transcribe") >= 0 && tail.indexOf("not found") >= 0) {
                throw new SshException("berryssh-transcribe is not installed on the server");
            }
            if (tail.length() > 80) {
                tail = tail.substring(tail.length() - 80);
            }
            throw new SshException("no answer from the server" + (tail.length() > 0 ? ": " + tail : ""));
        }
        if (answer.startsWith("ERR ")) {
            throw new SshException(answer.substring(4));
        }
        return answer.length() > 3 ? answer.substring(3).trim() : "";
    }
}
