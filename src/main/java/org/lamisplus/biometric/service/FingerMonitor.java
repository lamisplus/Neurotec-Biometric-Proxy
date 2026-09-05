package org.lamisplus.biometric.service;

import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.standards.CBEFFBDBFormatIdentifiers;
import com.neurotec.biometrics.standards.CBEFFBiometricOrganizations;
import com.neurotec.biometrics.standards.FMRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.domain.dto.ClientIdentificationDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FingerMonitor {

    private static final long MAX_WAIT_MILLIS = 30000L;
    private static final long IDLE_MILLIS = 200L;
    private static final long LOCK_WAIT_MILLIS = 250L;

    private final FingerCapture fingerCapture;
    private final FingerScannerManager fingerScannerManager;
    private final PatientRecall patientRecall;

    public ClientIdentificationDTO identifyNextFinger(NBiometricClient client, String reader, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.min(Math.max(timeoutMillis, 0L), MAX_WAIT_MILLIS);
        while (System.currentTimeMillis() < deadline) {
            byte[] template = readPlaten(client, reader);
            if (template != null) {
                return patientRecall.identify(template);
            }
            if (!pause()) {
                return null;
            }
        }
        return null;
    }

    private byte[] readPlaten(NBiometricClient client, String reader) {
        if (!acquireScanner()) {
            return null;
        }
        try {
            if (!fingerScannerManager.bindScanner(client, reader)) {
                return null;
            }
            try (NSubject subject = new NSubject()) {
                if (!fingerCapture.grabNow(client, subject)) {
                    return null;
                }
                if (!NBiometricStatus.OK.equals(client.createTemplate(subject))) {
                    return null;
                }
                return subject.getTemplateBuffer(
                        CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                        CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                        FMRecord.VERSION_ISO_20).toByteArray();
            } finally {
                client.clear();
            }
        } catch (Exception e) {
            LOG.debug("Monitoring could not read the scanner: {}", e.getMessage());
            return null;
        } finally {
            fingerScannerManager.scannerLock().unlock();
        }
    }

    private boolean acquireScanner() {
        try {
            return fingerScannerManager.scannerLock().tryLock(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean pause() {
        try {
            Thread.sleep(IDLE_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
