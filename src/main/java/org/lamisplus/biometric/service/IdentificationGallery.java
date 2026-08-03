package org.lamisplus.biometric.service;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NMatchingSpeed;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.io.NBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentificationGallery {

    private static final int MATCHING_THRESHOLD = 144;
    private static final int BATCH_SIZE = 500;
    private static final int PROGRESS_EVERY = 10000;

    private final BiometricRepository biometricRepository;

    private NBiometricClient client;
    private final Set<String> enrolled = new HashSet<>();

    /**
     * Builds the gallery off the request thread once the application is serving, so the first
     * recall of the day does not wait minutes for it. A recall arriving mid-build blocks on
     * {@link #upToDateClient()} until it finishes rather than starting a second one.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread warmUp = new Thread(() -> {
            LOG.info("Warming the identification gallery");
            try {
                upToDateClient();
            } catch (Exception e) {
                LOG.error("Could not warm the identification gallery, the first recall will build it: {}",
                        e.getMessage(), e);
            }
        }, "identification-gallery-warmup");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    public synchronized NBiometricClient upToDateClient() {
        List<String> currentIds = biometricRepository.getBaselineFingerprintIds();

        if (client == null || !currentIds.containsAll(enrolled)) {
            // Top-up cannot express a removal, so anything archived or deleted forces a rebuild.
            rebuild();
        }

        List<String> missing = currentIds.stream()
                .filter(id -> !enrolled.contains(id))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            enrol(missing);
        }
        return client;
    }

    private void rebuild() {
        if (client != null) {
            LOG.info("Rebuilding the identification gallery from scratch");
            try {
                client.clear();
                client.dispose();
            } catch (Exception e) {
                LOG.warn("Could not dispose the previous gallery: {}", e.getMessage());
            }
        }
        client = new NBiometricClient();
        client.setMatchingThreshold(MATCHING_THRESHOLD);
        client.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        enrolled.clear();
    }

    private void enrol(List<String> ids) {
        long start = System.currentTimeMillis();
        int enrolledNow = 0;
        int nextProgressAt = PROGRESS_EVERY;
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<String> batch = ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE));
            enrolledNow += enrolBatch(batch);
            if (enrolledNow >= nextProgressAt) {
                LOG.info("Gallery: {} of {} fingerprint(s) enrolled", enrolledNow, ids.size());
                nextProgressAt += PROGRESS_EVERY;
            }
        }
        // Recorded even when a template was skipped, so a bad row is not retried on every recall.
        enrolled.addAll(ids);
        LOG.info("Gallery: enrolled {} fingerprint(s) in {}ms, {} in total",
                enrolledNow, System.currentTimeMillis() - start, enrolled.size());
    }

    private int enrolBatch(List<String> ids) {
        List<Biometric> fingerprints = new ArrayList<>();
        biometricRepository.findAllById(ids).forEach(fingerprints::add);

        List<NSubject> subjects = fingerprints.stream()
                .filter(fingerPrint -> fingerPrint.getTemplate() != null && fingerPrint.getTemplate().length > 25)
                .map(fingerPrint -> {
                    NSubject subject = new NSubject();
                    byte[] template = fingerPrint.getTemplate();
                    template[25] = 0x00;
                    subject.setTemplateBuffer(new NBuffer(template));
                    subject.setId(fingerPrint.getId() + "#" + fingerPrint.getPersonUuid());
                    return subject;
                })
                .collect(Collectors.toList());

        NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
        for (NSubject subject : subjects) {
            try {
                task.getSubjects().add(subject);
            } catch (Exception e) {
                LOG.error("Could not add {} to the gallery: {}", subject.getId(), e.getMessage());
            }
        }

        try {
            client.performTask(task);
        } catch (Exception e) {
            LOG.error("Enrolling a gallery batch failed: {}", e.getMessage(), e);
        }
        return subjects.size();
    }
}
