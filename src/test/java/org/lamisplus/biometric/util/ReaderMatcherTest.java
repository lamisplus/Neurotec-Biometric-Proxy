package org.lamisplus.biometric.util;

import org.junit.Test;
import org.lamisplus.biometric.util.ReaderMatcher.Candidate;
import org.lamisplus.biometric.util.ReaderMatcher.Match;
import org.lamisplus.biometric.util.ReaderMatcher.Strategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReaderMatcherTest {

    private static final String FUTRONIC_READER = "Futronic FS80H %231";

    private static final Candidate FUTRONIC_FS80H =
            new Candidate("Futronic FS80H #1", "Futronic", "FS80H", "Futronic FS80H #1");
    private static final Candidate FUTRONIC_FS88H =
            new Candidate("Futronic FS88H #1", "Futronic", "FS88H", "Futronic FS88H #1");
    private static final Candidate SECUGEN_U20 =
            new Candidate("SecuGen U20 #1", "SecuGen", "U20", "SecuGen U20 #1");

    @Test
    public void matchesTheEncodedNameTheModuleSends() {
        Match match = ReaderMatcher.match(FUTRONIC_READER, Collections.singletonList(FUTRONIC_FS80H));

        assertNotNull(match);
        assertEquals(0, match.getIndex());
        assertEquals(Strategy.EXACT_NAME, match.getStrategy());
    }

    @Test
    public void picksTheFutronicWhenASecugenIsAlsoAttached() {
        List<Candidate> attached = Arrays.asList(SECUGEN_U20, FUTRONIC_FS80H);

        Match match = ReaderMatcher.match(FUTRONIC_READER, attached);

        assertNotNull(match);
        assertEquals(1, match.getIndex());
    }

    @Test
    public void neverFallsBackToAScannerFromAnotherVendor() {
        Match match = ReaderMatcher.match(FUTRONIC_READER, Collections.singletonList(SECUGEN_U20));

        assertNull(match);
    }

    @Test
    public void ignoresTheInstanceSuffixWhenTheDriverDoesNotReportOne() {
        Candidate withoutSuffix = new Candidate("Futronic FS80H", "Futronic", "FS80H", "ftr-0");

        Match match = ReaderMatcher.match(FUTRONIC_READER, Collections.singletonList(withoutSuffix));

        assertNotNull(match);
        assertEquals(Strategy.NAME_WITHOUT_INSTANCE_SUFFIX, match.getStrategy());
    }

    @Test
    public void fallsBackToMakeAndModelWhenTheDisplayNameDiffers() {
        Candidate oddDisplayName = new Candidate("USB Fingerprint Reader", "Futronic", "FS80H", "ftr-0");

        Match match = ReaderMatcher.match(FUTRONIC_READER, Collections.singletonList(oddDisplayName));

        assertNotNull(match);
        assertEquals(Strategy.MAKE_AND_MODEL, match.getStrategy());
    }

    @Test
    public void acceptsAnotherModelFromTheSameVendorWhenItIsTheOnlyOne() {
        List<Candidate> attached = Arrays.asList(SECUGEN_U20, FUTRONIC_FS88H);

        Match match = ReaderMatcher.match(FUTRONIC_READER, attached);

        assertNotNull(match);
        assertEquals(1, match.getIndex());
        assertEquals(Strategy.VENDOR_ONLY, match.getStrategy());
    }

    @Test
    public void refusesToGuessBetweenTwoModelsOfTheSameVendor() {
        List<Candidate> attached = Arrays.asList(FUTRONIC_FS88H,
                new Candidate("Futronic FS26 #1", "Futronic", "FS26", "Futronic FS26 #1"));

        assertNull(ReaderMatcher.match(FUTRONIC_READER, attached));
    }

    @Test
    public void matchesRegardlessOfCaseAndSpacing() {
        Candidate shouty = new Candidate("  FUTRONIC   FS80H  #1 ", "FUTRONIC", "FS80H", "ftr-0");

        assertNotNull(ReaderMatcher.match("futronic fs80h #1", Collections.singletonList(shouty)));
    }

    @Test
    public void matchesOnDeviceIdWhenThatIsWhatWasConfigured() {
        Candidate byId = new Candidate("USB Fingerprint Reader", "Futronic", "FS80H", "ftrScan-0");

        Match match = ReaderMatcher.match("ftrScan-0", Collections.singletonList(byId));

        assertNotNull(match);
        assertEquals(Strategy.EXACT_NAME, match.getStrategy());
    }

    @Test
    public void returnsNothingWhenNoScannerIsAttached() {
        assertNull(ReaderMatcher.match(FUTRONIC_READER, Collections.<Candidate>emptyList()));
    }

    @Test
    public void returnsNothingForABlankOrNullReader() {
        List<Candidate> attached = Collections.singletonList(FUTRONIC_FS80H);

        assertNull(ReaderMatcher.match(null, attached));
        assertNull(ReaderMatcher.match("   ", attached));
    }

    @Test
    public void leavesAReaderNameAloneWhenItIsNotValidPercentEncoding() {
        assertEquals("100% sure", ReaderMatcher.decode("100% sure"));
    }
}
