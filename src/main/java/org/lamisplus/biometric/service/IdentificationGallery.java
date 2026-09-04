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
import org.lamisplus.biometric.util.StoredTemplate;
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
    private static final long CHECK_INTERVAL_MILLIS = 2000L;


    /**
     * ISO 19794-2: 24 byte header, then finger position, view/impression, quality, minutia count.
     */
    private static final int MINUTIA_COUNT_OFFSET = 27;

    private int unreadable = 0;
    private int tooFewMinutiae = 0;
    private int searchable = 0;

    private final BiometricRepository biometricRepository;
    private final NeurotecProperties neurotecProperties;
    private final DataSource dataSource;
    private final ReentrantLock lock = new ReentrantLock();

    private NBiometricClient client;
    private final Set<String> enrolled = new HashSet<>();

    /** The database state the gallery was last built from; compared for equality only. */
    private String galleryFingerprint;

    private long lastCheckedAt;

    /** Builds off the request thread so the first recall of the day does not wait for it. */
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

    /** Returns null rather than queueing behind a build, which the UI cannot tell from a hang. */
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
        // The probe aggregates the whole table, so a burst of recalls must not repeat it.
        long now = System.currentTimeMillis();
        if (client != null && now - lastCheckedAt < CHECK_INTERVAL_MILLIS) {
            return client;
        }
        lastCheckedAt = now;

        String fingerprint = biometricRepository.getIdentificationGalleryFingerprint();
        if (client != null && fingerprint != null && fingerprint.equals(galleryFingerprint)) {
            return client;
        }

        // A Set, not the List: containsAll against a List is a linear scan per element.
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
        // Read before the build, not after, so a print written mid-build is topped up next recall.
        galleryFingerprint = fingerprint;
        return client;
    }

    /** Separates "the print is not in this database" from "it is enrolled but did not match". */
    public Map<String, Object> status(String personUuid) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("galleryBuilt", client != null);
        report.put("enrolledCount", enrolled.size());
        report.put("identificationThreshold", neurotecProperties.getIdentificationThreshold());
        report.put("printsInDatabase", biometricRepository.countIdentificationGalleryPrints());
        report.put("galleryFingerprint", galleryFingerprint);

        // Entries the matcher still holds for prints the database no longer has: these match and
        // then name an owner whose prints are gone.
        Set<String> live = new HashSet<>(biometricRepository.getIdentificationGalleryIds());
        List<String> stale = enrolled.stream().filter(id -> !live.contains(id)).collect(Collectors.toList());
        report.put("staleGalleryEntryCount", stale.size());
        report.put("staleGalleryEntries", stale.size() > 50 ? stale.subList(0, 50) : stale);

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

    /** Forces a rebuild when a match resolves to a print the database no longer has. */
    public void invalidate() {
        lastCheckedAt = 0L;
        galleryFingerprint = null;
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
        LOG.info("Building the identification gallery at matching threshold {}, minimum {} minutiae",
                neurotecProperties.getIdentificationThreshold(),
                neurotecProperties.getMinimalMinutiaCount());
        client.setMatchingThreshold(neurotecProperties.getIdentificationThreshold());
        if (neurotecProperties.getMinimalMinutiaCount() > 0) {
            client.setFingersMinimalMinutiaCount(neurotecProperties.getMinimalMinutiaCount());
        }
        client.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        // Parallelism only; it cannot change which entry matches.
        client.setMaximalThreadCount(Runtime.getRuntime().availableProcessors());
        enrolled.clear();
        galleryFingerprint = null;
        lastCheckedAt = 0L;
        searchable = 0;
    }

    private void enrol(List<String> ids) {
        long start = System.currentTimeMillis();
        unreadable = 0;
        tooFewMinutiae = 0;
        int loadedNow = 0;
        int nextProgressAt = PROGRESS_EVERY;
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<String> batch = ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE));
            loadedNow += enrolBatch(batch);
            if (loadedNow >= nextProgressAt) {
                LOG.info("Gallery: {} of {} fingerprint(s) loaded into the matcher", loadedNow, ids.size());
                nextProgressAt += PROGRESS_EVERY;
            }
        }
        // Recorded even when a template was skipped, so a bad row is not retried on every recall.
        enrolled.addAll(ids);

        int notLoaded = ids.size() - loadedNow;
        if (notLoaded > 0) {
            LOG.warn("Gallery: {} of {} fingerprint(s) could not be loaded into the matcher and will never "
                            + "match ({} unreadable, {} with fewer than {} minutiae).",
                    notLoaded, ids.size(), unreadable, tooFewMinutiae,
                    neurotecProperties.getMinimalMinutiaCount());
        }
        searchable += loadedNow;
        LOG.info("Gallery: {} of {} fingerprint(s) loaded into the matcher in {}ms, {} searchable in total",
                loadedNow, ids.size(), System.currentTimeMillis() - start, searchable);
    }

    private static byte[] normalisedViewNumber(byte[] template) {
        byte[] copy = template.clone();
        copy[25] = 0x00;
        return copy;
    }

    private int enrolBatch(List<String> ids) {
        List<Biometric> fingerprints = new ArrayList<>();
        biometricRepository.findAllById(ids).forEach(fingerprints::add);

        List<NSubject> subjects = new ArrayList<>();
        for (Biometric fingerPrint : fingerprints) {
            byte[] record = StoredTemplate.toFmr(fingerPrint.getTemplate());
            if (record == null || record.length <= MINUTIA_COUNT_OFFSET) {
                unreadable++;
                continue;
            }
            // A print with no owner can still match, and would then identify nobody.
            if (fingerPrint.getPersonUuid() == null || fingerPrint.getPersonUuid().trim().isEmpty()) {
                unreadable++;
                continue;
            }
            // Too few minutiae to identify anyone reliably, and a standing false-accept risk.
            if ((record[MINUTIA_COUNT_OFFSET] & 0xFF) < neurotecProperties.getMinimalMinutiaCount()) {
                tooFewMinutiae++;
                continue;
            }
            NSubject subject = new NSubject();
            subject.setTemplateBuffer(new NBuffer(normalisedViewNumber(record)));
            subject.setId(fingerPrint.getId() + "#" + fingerPrint.getPersonUuid());
            subjects.add(subject);
        }

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

        // Per-batch reasons repeat; enrol() summarises the run by rejected count.
        if (!NBiometricStatus.OK.equals(task.getStatus())) {
            LOG.debug("Gallery batch task status {}{}", task.getStatus(),
                    task.getError() == null ? "" : " - " + task.getError().getMessage());
        }

        int accepted = 0;
        for (NSubject subject : subjects) {
            if (NBiometricStatus.OK.equals(subject.getStatus())) {
                accepted++;
            }
        }
        return accepted;
    }
}
