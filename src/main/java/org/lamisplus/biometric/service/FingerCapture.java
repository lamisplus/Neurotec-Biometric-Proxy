package org.lamisplus.biometric.service;

import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NFPosition;
import com.neurotec.biometrics.NFinger;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.util.concurrent.NAsyncOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Auto capture waits for a finger to arrive; this grabs one already resting on the platen. */
@Slf4j
@Service
public class FingerCapture {

    private static final long GRAB_BUDGET_MILLIS = 2000L;
    private static final long GRAB_TIMEOUT_MILLIS = 700L;
    private static final long POLL_INTERVAL_MILLIS = 150L;

    @Value("${server.quality}")
    private long quality;

    public NBiometricStatus capture(NBiometricClient client, NSubject subject) {
        long deadline = System.currentTimeMillis() + GRAB_BUDGET_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (NBiometricStatus.OK.equals(grab(client, subject)) && qualityOf(subject) >= quality) {
                return NBiometricStatus.OK;
            }
            if (!pause()) {
                break;
            }
        }
        return waitForFinger(client, subject);
    }

    private NBiometricStatus grab(NBiometricClient client, NSubject subject) {
        resetFinger(subject, EnumSet.of(NBiometricCaptureOption.MANUAL));
        NAsyncOperation<NBiometricStatus> operation;
        try {
            operation = client.captureAsync(subject);
        } catch (Exception e) {
            LOG.debug("Could not start a manual capture: {}", e.getMessage());
            return NBiometricStatus.CAPTURE_ERROR;
        }
        try {
            client.force();
            return operation.get(GRAB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            operation.cancel(true);
            return NBiometricStatus.TIMEOUT;
        } catch (InterruptedException e) {
            operation.cancel(true);
            Thread.currentThread().interrupt();
            return NBiometricStatus.CANCELED;
        } catch (Exception e) {
            LOG.debug("Manual capture did not complete: {}", e.getMessage());
            return NBiometricStatus.CAPTURE_ERROR;
        }
    }

    private NBiometricStatus waitForFinger(NBiometricClient client, NSubject subject) {
        resetFinger(subject, EnumSet.noneOf(NBiometricCaptureOption.class));
        return client.capture(subject);
    }

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

    private static boolean pause() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
