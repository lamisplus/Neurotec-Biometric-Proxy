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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds the enrolled fingerprint gallery used for client identification. Rebuilding it per
 * request means enrolling every baseline template in the facility on every recall, which takes
 * minutes and starves the JVM; this keeps one gallery alive and only enrols what is new.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentificationGallery {

    private static final int MATCHING_THRESHOLD = 144;

    private final BiometricRepository biometricRepository;

    private NBiometricClient client;
    private final Set<String> enrolled = new HashSet<>();

    /**
     * Returns a client whose gallery is up to date with the database. Serialised because the
     * gallery is shared: two concurrent recalls must not enrol the same templates twice.
     */
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
            LOG.error("Enrolling the gallery failed: {}", e.getMessage(), e);
        }

        // Recorded even when a template was skipped, so a bad row is not retried on every recall.
        enrolled.addAll(ids);
        LOG.info("Gallery: enrolled {} fingerprint(s) in {}ms, {} in total",
                subjects.size(), System.currentTimeMillis() - start, enrolled.size());
    }
}
