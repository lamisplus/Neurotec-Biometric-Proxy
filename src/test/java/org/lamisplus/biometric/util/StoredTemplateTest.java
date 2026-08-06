package org.lamisplus.biometric.util;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class StoredTemplateTest {

    /** A short but structurally real FMR record: "FMR\0 20\0" then arbitrary body bytes. */
    private static final byte[] FMR = fmr();

    private static byte[] fmr() {
        byte[] record = new byte[64];
        record[0] = 0x46;
        record[1] = 0x4d;
        record[2] = 0x52;
        record[3] = 0x00;
        record[4] = 0x20;
        record[5] = 0x32;
        record[6] = 0x30;
        record[7] = 0x00;
        for (int i = 8; i < record.length; i++) {
            // Spans the full byte range, including 0x00 and values above 0x7F.
            record[i] = (byte) (i * 7);
        }
        return record;
    }

    private static byte[] serialisedAsLatin1String(byte[] bytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(new String(bytes, StandardCharsets.ISO_8859_1));
        }
        return out.toByteArray();
    }

    @Test
    public void passesARawRecordThrough() {
        assertSame(FMR, StoredTemplate.toFmr(FMR));
    }

    @Test
    public void decodesARecordStoredAsBase64Text() {
        byte[] stored = Base64.getEncoder().encodeToString(FMR).getBytes(StandardCharsets.US_ASCII);

        assertArrayEquals(FMR, StoredTemplate.toFmr(stored));
    }

    @Test
    public void decodesARecordStoredAsASerialisedJavaString() throws Exception {
        byte[] stored = serialisedAsLatin1String(FMR);

        assertArrayEquals(FMR, StoredTemplate.toFmr(stored));
    }

    @Test
    public void serialisedFormStartsWithTheSignatureSeenInProduction() throws Exception {
        byte[] stored = serialisedAsLatin1String(FMR);

        assertArrayEquals(new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x74},
                new byte[]{stored[0], stored[1], stored[2], stored[3], stored[4]});
    }

    @Test
    public void rejectsBytesThatAreNotARecordInAnyEncoding() {
        assertNull(StoredTemplate.toFmr("not a fingerprint at all".getBytes(StandardCharsets.US_ASCII)));
        assertNull(StoredTemplate.toFmr(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05}));
    }

    @Test
    public void rejectsBase64ThatDecodesToSomethingElse() {
        byte[] stored = Base64.getEncoder().encodeToString("hello world padding".getBytes(StandardCharsets.US_ASCII))
                .getBytes(StandardCharsets.US_ASCII);

        assertNull(StoredTemplate.toFmr(stored));
    }

    @Test
    public void rejectsAbsentOrTruncatedInput() {
        assertNull(StoredTemplate.toFmr(null));
        assertNull(StoredTemplate.toFmr(new byte[0]));
        assertNull(StoredTemplate.toFmr(new byte[]{0x46, 0x4d}));
    }
}
