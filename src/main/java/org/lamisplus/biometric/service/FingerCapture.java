package org.lamisplus.biometric.service;

import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NFPosition;
import com.neurotec.biometrics.NFinger;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.util.concurrent.NAsyncOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FingerCapture {

    private static final long GRAB_TIMEOUT_MILLIS = 1500L;
    private static final long DRAIN_TIMEOUT_MILLIS = 2000L;
    private static final long SETTLE_MILLIS = 250L;

    private final FingerScannerManager fingerScannerManager;

    /** Set false to wait for a finger arrival only, as the SDK does on its own. */
    @Value("${lamisplus.neurotec.grab-current-frame:true}")
    private boolean grabCurrentFrame;

    @Value("${server.quality}")
    private long quality;

    /**
     * A cached scanner handle goes stale when the device re-plugs or the machine sleeps, and the
     * SDK then throws "Device is not available". Re-bind and try once more, as SecuGen reopens.
     */
    public NBiometricStatus capture(NBiometricClient client, NSubject subject, String reader) {
        try {
            return attempt(client, subject);
        } catch (Exception e) {
            LOG.warn("Capture failed on {}: {}. Re-binding the scanner and retrying once.",
                    reader, rootCause(e));
            if (!fingerScannerManager.rebindScanner(client, reader)) {
                return NBiometricStatus.SOURCE_NOT_FOUND;
            }
            return attempt(client, subject);
        }
    }

    public boolean grabNow(NBiometricClient client, NSubject subject) {
        return grabbedCurrentFrame(client, subject);
    }

    private NBiometricStatus attempt(NBiometricClient client, NSubject subject) {
        if (grabCurrentFrame && grabbedCurrentFrame(client, subject)) {
            return NBiometricStatus.OK;
        }
        return waitForFinger(client, subject);
    }

    private static String rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /**
     * Auto capture waits for a finger to arrive, so one already on the platen is never seen.
     * A single attempt only: cancelling leaves native work that must drain before reuse.
     */
    private boolean grabbedCurrentFrame(NBiometricClient client, NSubject subject) {
        resetFinger(subject, EnumSet.of(NBiometricCaptureOption.MANUAL));
        NAsyncOperation<NBiometricStatus> operation = null;
        try {
            operation = client.captureAsync(subject);
            // The device needs a moment to produce a frame before force() can accept one.
            Thread.sleep(SETTLE_MILLIS);
            client.force();
            NBiometricStatus status = operation.get(GRAB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            long quality = qualityOf(subject);
            if (NBiometricStatus.OK.equals(status) && quality >= this.quality) {
                LOG.info("Captured a finger already on the scanner, quality {}", quality);
                return true;
            }
            LOG.debug("No usable finger on the scanner: status {}, quality {}", status, quality);
            return false;
        } catch (InterruptedException e) {
            drain(operation);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOG.debug("Manual capture did not complete: {}", e.getMessage());
            drain(operation);
            return false;
        }
    }

    /** The subject is reused next, so the operation must be finished before it is rewritten. */
    private static void drain(NAsyncOperation<NBiometricStatus> operation) {
        if (operation == null) {
            return;
        }
        try {
            operation.cancel(true);
            operation.get(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Cancelled, failed or timed out: nothing further can be done before the retry.
        }
    }

    private NBiometricStatus waitForFinger(NBiometricClient client, NSubject subject) {
        subject.getFingers().clear();
        NFinger finger = new NFinger();
        finger.setPosition(NFPosition.UNKNOWN);
        subject.getFingers().add(finger);
        return client.capture(subject);
    }

    /** Only for the manual grab: an empty option set is not the same as the SDK default. */
    private static void resetFinger(NSubject subject, EnumSet<NBiometricCaptureOption> options) {
        subject.getFingers().clear();
        NFinger finger = new NFinger();
        finger.setPosition(NFPosition.UNKNOWN);
        finger.setCaptureOptions(options);
        subject.getFingers().add(finger);
    }

    private static long qualityOf(NSubject subject) {
        try {
            return subject.getFingers().get(0).getObjects().get(0).getQuality();
        } catch (Exception e) {
            return 0L;
        }
    }
}
