package org.lamisplus.biometric.service;

import org.junit.Test;
import org.lamisplus.biometric.config.SecugenProperties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SecugenManagerTest {

    private final SecugenManager secugenManager = new SecugenManager(new SecugenProperties());

    @Test
    public void recognisesTheReaderNameTheBiometricModuleSends() {
        assertTrue(secugenManager.isSecugenReader("SG_DEV_AUTO"));
    }

    @Test
    public void recognisesADriverName() {
        assertTrue(secugenManager.isSecugenReader("Auto-detect"));
        assertTrue(secugenManager.isSecugenReader("USB U20 driver"));
    }

    @Test
    public void recognisesADriverNameWithTheSlashEscapedAsOr() {
        assertTrue(secugenManager.isSecugenReader("USB FDU03 OR SDU03 driver"));
    }

    @Test
    public void recognisesADeviceId() {
        assertTrue(secugenManager.isSecugenReader("255"));
    }

    @Test
    public void doesNotClaimAFutronicReader() {
        assertFalse(secugenManager.isSecugenReader("Futronic FS80H #1"));
    }

    @Test
    public void doesNotClaimAnAbsentReader() {
        assertFalse(secugenManager.isSecugenReader(null));
        assertFalse(secugenManager.isSecugenReader(""));
    }
}
