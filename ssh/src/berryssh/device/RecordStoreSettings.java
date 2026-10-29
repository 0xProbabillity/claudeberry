package berryssh.device;

import java.io.IOException;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

import berryssh.protocol.SshException;

/**
 * Settings in RMS: one record, replaced on every save.
 *
 * An unreadable record gives the defaults rather than an error. A look that
 * resets is an inconvenience; an app that will not open over it is not.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class RecordStoreSettings implements Settings.Store {

    private static final String STORE_NAME = "berryssh_settings";

    public Settings load() throws IOException {
        RecordStore store = open();
        try {
            RecordEnumeration records = store.enumerateRecords(null, null, false);
            if (records.hasNextElement()) {
                try {
                    return Settings.decode(records.nextRecord());
                } catch (IOException e) {
                    return Settings.defaults();
                }
            }
            return Settings.defaults();
        } catch (RecordStoreException e) {
            throw new SshException("could not read settings: " + e.getMessage());
        } finally {
            close(store);
        }
    }

    public void save(Settings settings) throws IOException {
        RecordStore store = open();
        try {
            RecordEnumeration records = store.enumerateRecords(null, null, false);
            while (records.hasNextElement()) {
                store.deleteRecord(records.nextRecordId());
            }
            byte[] record = settings.encode();
            store.addRecord(record, 0, record.length);
        } catch (RecordStoreException e) {
            throw new SshException("could not save settings: " + e.getMessage());
        } finally {
            close(store);
        }
    }

    private static RecordStore open() throws IOException {
        try {
            return RecordStore.openRecordStore(STORE_NAME, true);
        } catch (RecordStoreException e) {
            throw new SshException("could not open settings: " + e.getMessage());
        }
    }

    private static void close(RecordStore store) {
        try {
            store.closeRecordStore();
        } catch (RecordStoreException e) {
            // Already written; see RecordStoreProfiles.
        }
    }
}
