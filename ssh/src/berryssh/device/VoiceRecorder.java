package berryssh.device;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.RecordControl;

import berryssh.protocol.SshException;

/**
 * Records a phrase from the microphone, through MMAPI (JSR-135).
 *
 * MMAPI is a standard optional package rather than a RIM API, so recording
 * needs no signature; an unsigned MIDlet gets a permission prompt the first
 * time instead. Each format from {@link VoiceFormats} is tried until one
 * records, and the one that did is reported, because nobody can say in
 * advance which of them this handset will accept.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class VoiceRecorder {

    private Player player;
    private RecordControl control;
    private ByteArrayOutputStream out;
    private String locator = "";

    /** Starts recording. Returns the capture locator that worked. */
    public String start() throws IOException {
        String[] candidates = VoiceFormats.locators(System.getProperty("audio.encodings"));
        String last = "no capture format offered";
        for (int i = 0; i < candidates.length; i++) {
            try {
                begin(candidates[i]);
                locator = candidates[i];
                return locator;
            } catch (SecurityException e) {
                // Refused at the prompt: the same answer for every format.
                cancel();
                throw new SshException("microphone permission was refused");
            } catch (Exception e) {
                last = candidates[i] + ": " + e.getMessage();
                cancel();
            }
        }
        throw new SshException("could not record: " + last);
    }

    private void begin(String candidate) throws IOException, MediaException {
        player = Manager.createPlayer(candidate);
        player.realize();
        control = (RecordControl) player.getControl("RecordControl");
        if (control == null) {
            throw new MediaException("no RecordControl");
        }
        out = new ByteArrayOutputStream();
        control.setRecordStream(out);
        control.startRecord();
        player.start();
    }

    public boolean recording() {
        return player != null;
    }

    /** The capture locator in use, for the server to know the raw rate. */
    public String locator() {
        return locator;
    }

    /** Stops and returns the recording, and what kind of audio it is. */
    public byte[] stop(String[] contentType) throws IOException {
        if (control == null) {
            throw new SshException("not recording");
        }
        try {
            control.stopRecord();
            control.commit();
            contentType[0] = control.getContentType();
            return out.toByteArray();
        } finally {
            cancel();
        }
    }

    /** Drops whatever is in progress. */
    public void cancel() {
        if (player != null) {
            player.close();
        }
        player = null;
        control = null;
    }
}
