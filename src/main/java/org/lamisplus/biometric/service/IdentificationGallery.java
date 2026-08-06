package org.lamisplus.biometric.service;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NMatchingSpeed;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.io.NBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.config.NeurotecProperties;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentificationGallery {

    private static final int BATCH_SIZE = 500;
    private static final int PROGRESS_EVERY = 10000;

    private static final int WAIT_FOR_GALLERY_SECONDS = 5;
    private static final int MAX_REJECTIONS_LOGGED = 20;

    private int rejectionsLogged = 0;

    private final BiometricRepository biometricRepository;
    private final NeurotecProperties neurotecProperties;
    private final DataSource dataSource;
    private final ReentrantLock lock = new ReentrantLock();

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
            lock.lock();
            try {
                upToDateClient();
            } catch (Exception e) {
                LOG.error("Could not warm the identification gallery, the first recall will build it: {}",
                        e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }, "identification-gallery-warmup");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    /**
     * Returns null rather than queueing behind a build in progress. A recall that waits minutes
     * for the start-up warm-up is indistinguishable from a hung request at the UI.
     */
    public NBiometricClient upToDateClientOrNull() {
        try {
            if (!lock.tryLock(WAIT_FOR_GALLERY_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("Identification gallery is still building; skipping this recall");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            return upToDateClient();
        } finally {
            lock.unlock();
        }
    }

    private NBiometricClient upToDateClient() {
        // A Set, not the List the query returns: containsAll against a List is a linear scan
        // per element, which on a full gallery costs minutes on every recall.
        Set<String> currentIds = new HashSet<>(biometricRepository.getIdentificationGalleryIds());

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

    /**
     * Reports whether a person's stored prints actually made it into the gallery, which
     * separates "the print is not visible to this database" from "it is enrolled but did
     * not match".
     */
    public Map<String, Object> status(String personUuid) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("galleryBuilt", client != null);
        report.put("enrolledCount", enrolled.size());
        report.put("identificationThreshold", neurotecProperties.getIdentificationThreshold());
        report.put("printsInDatabase", biometricRepository.getIdentificationGalleryIds().size());

        report.put("database", databaseUrl());

        if (personUuid != null && !personUuid.trim().isEmpty()) {
            List<String> ids = biometricRepository.getIdentificationGalleryIdsForPerson(personUuid.trim());
            List<String> inGallery = ids.stream().filter(enrolled::contains).collect(Collectors.toList());
            report.put("personUuid", personUuid.trim());
            report.put("personPrintsAnyState", biometricRepository.getAllPrintIdsForPerson(personUuid.trim()).size());
            report.put("personPrintsInDatabase", ids.size());
            report.put("personPrintsInGallery", inGallery.size());
            report.put("personPrintIds", ids);
        }
        return report;
    }

    private String databaseUrl() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (Exception e) {
            return "unknown: " + e.getMessage();
        }
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
        client.setMatchingThreshold(neurotecProperties.getIdentificationThreshold());
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
        int rejected = ids.size() - enrolledNow;
        if (rejected > 0) {
            LOG.warn("Gallery: {} of {} fingerprint(s) were rejected by the SDK and cannot be matched. "
                    + "Enable DEBUG on this logger for the per-batch reason.", rejected, ids.size());
        }
        LOG.info("Gallery: enrolled {} fingerprint(s) in {}ms, {} in total",
                enrolledNow, System.currentTimeMillis() - start, enrolled.size());
    }

    private static byte[] normalisedViewNumber(byte[] template) {
        byte[] copy = template.clone();
        copy[25] = 0x00;
        return copy;
    }

    private int enrolBatch(List<String> ids) {
        List<Biometric> fingerprints = new ArrayList<>();
        biometricRepository.findAllById(ids).forEach(fingerprints::add);

        List<Biometric> usable = fingerprints.stream()
                .filter(fingerPrint -> fingerPrint.getTemplate() != null && fingerPrint.getTemplate().length > 25)
                .collect(Collectors.toList());

        List<NSubject> subjects = usable.stream()
                .map(fingerPrint -> {
                    NSubject subject = new NSubject();
                    subject.setTemplateBuffer(new NBuffer(normalisedViewNumber(fingerPrint.getTemplate())));
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
            return 0;
        }

        // One line per batch is noise; the per-batch reason repeats and the run is summarised
        // by the rejected count in enrol().
        if (!NBiometricStatus.OK.equals(task.getStatus())) {
            LOG.debug("Gallery batch task status {}{}", task.getStatus(),
                    task.getError() == null ? "" : " - " + task.getError().getMessage());
        }

        int accepted = 0;
        for (int i = 0; i < subjects.size(); i++) {
            NSubject subject = subjects.get(i);
            if (NBiometricStatus.OK.equals(subject.getStatus())) {
                accepted++;
            } else if (rejectionsLogged < MAX_REJECTIONS_LOGGED) {
                rejectionsLogged++;
                byte[] template = usable.get(i).getTemplate();
                // The leading bytes name the format, which is what decides whether a rejected
                // template can be converted or has to be captured again.
                LOG.warn("Gallery rejected {} status={} bytes={} header={} length={}",
                        subject.getId(), subject.getStatus(),
                        leadingBytesHex(template), printableHeader(template), template.length);
            }
        }
        return accepted;
    }

    private static String leadingBytesHex(byte[] template) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(12, template.length); i++) {
            hex.append(String.format("%02x", template[i]));
        }
        return hex.toString();
    }

    private static String printableHeader(byte[] template) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(12, template.length); i++) {
            char c = (char) (template[i] & 0xFF);
            text.append(c >= 0x20 && c < 0x7F ? c : '.');
        }
        return text.toString();
    }
}
