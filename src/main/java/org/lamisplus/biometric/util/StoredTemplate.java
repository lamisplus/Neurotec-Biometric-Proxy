package org.lamisplus.biometric.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Historic capture paths wrote the same bytes three ways; the SDK accepts only one. */
public final class StoredTemplate {

    private static final byte[] FMR_MAGIC = {0x46, 0x4d, 0x52, 0x00};
    private static final byte[] SERIALISED_STRING_PREFIX = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x74};

    private StoredTemplate() {
    }

    /**
     * @return the raw FMR record, or null when the bytes are not recognisable as one
     */
    public static byte[] toFmr(byte[] stored) {
        if (stored == null || stored.length < FMR_MAGIC.length) {
            return null;
        }
        if (isFmr(stored)) {
            return stored;
        }
        byte[] decoded = fromBase64Text(stored);
        if (decoded != null) {
            return decoded;
        }
        return fromSerialisedString(stored);
    }

    public static boolean isFmr(byte[] candidate) {
        if (candidate == null || candidate.length < FMR_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < FMR_MAGIC.length; i++) {
            if (candidate[i] != FMR_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] fromBase64Text(byte[] stored) {
        try {
            byte[] decoded = Base64.getDecoder().decode(new String(stored, StandardCharsets.US_ASCII).trim());
            return isFmr(decoded) ? decoded : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Reads the payload directly; ObjectInputStream would deserialise any object graph. */
    private static byte[] fromSerialisedString(byte[] stored) {
        if (stored.length <= SERIALISED_STRING_PREFIX.length) {
            return null;
        }
        for (int i = 0; i < SERIALISED_STRING_PREFIX.length; i++) {
            if (stored[i] != SERIALISED_STRING_PREFIX[i]) {
                return null;
            }
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                stored, SERIALISED_STRING_PREFIX.length, stored.length - SERIALISED_STRING_PREFIX.length))) {
            byte[] decoded = in.readUTF().getBytes(StandardCharsets.ISO_8859_1);
            return isFmr(decoded) ? decoded : null;
        } catch (Exception e) {
            return null;
        }
    }
}
