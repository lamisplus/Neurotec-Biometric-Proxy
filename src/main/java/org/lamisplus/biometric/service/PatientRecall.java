package org.lamisplus.biometric.service;

import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NMatchingResult;
import com.neurotec.biometrics.NMatchingSpeed;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.io.NBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.config.NeurotecProperties;
import org.lamisplus.biometric.domain.dto.ClientIdentificationDTO;
import org.lamisplus.biometric.domain.dto.IdentifiedClient;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.lamisplus.biometric.util.StoredTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Search then verify. The gallery proposes candidates; each is confirmed one to one against the
 * template read back from the database, which also says who it belongs to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientRecall {

    static final String MATCH_FOUND = "SUCCESS_MATCH_FOUND";
    static final String NO_MATCH_FOUND = "SUCCESS_NO_MATCH_FOUND";

    private static final int MINIMUM_TEMPLATE_LENGTH = 28;

    private static final String PATIENT_SQL =
            "select id, uuid, first_name, sex, surname, other_name, hospital_number, date_of_birth "
                    + "from patient_person where uuid = ?";

    private final IdentificationGallery gallery;
    private final BiometricRepository biometricRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NeurotecProperties neurotecProperties;

    private NBiometricClient verifier;

    @PostConstruct
    void createVerifier() {
        verifier = new NBiometricClient();
        verifier.setMatchingThreshold(neurotecProperties.getVerificationThreshold());
        verifier.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        verifier.initialize();
    }

    @PreDestroy
    void disposeVerifier() {
        try {
            verifier.dispose();
        } catch (Exception e) {
            LOG.warn("Could not dispose the recall verifier: {}", e.getMessage());
        }
    }

    /**
     * @param probeTemplate the ISO 19794-2 record just captured
     * @return never null, and never throws: recall degrades to no match
     */
    public ClientIdentificationDTO identify(byte[] probeTemplate) {
        try {
            return search(probeTemplate);
        } catch (Exception e) {
            LOG.error("Recall failed, reporting no match", e);
            return outcome(NO_MATCH_FOUND, "Could not identify clients");
        }
    }

    private ClientIdentificationDTO search(byte[] probeTemplate) {
        if (probeTemplate == null || probeTemplate.length < MINIMUM_TEMPLATE_LENGTH) {
            LOG.warn("Recall was given no usable template");
            return outcome(NO_MATCH_FOUND, "Could not identify clients");
        }

        NBiometricClient galleryClient = gallery.upToDateClientOrNull();
        if (galleryClient == null) {
            return outcome(NO_MATCH_FOUND, "Fingerprint gallery is still loading, please try again shortly");
        }

        List<Candidate> candidates = candidates(galleryClient, probeTemplate);
        if (candidates.isEmpty()) {
            LOG.info("Recall found no candidate over the threshold");
            return outcome(NO_MATCH_FOUND, "Could not identify clients");
        }

        for (Candidate candidate : candidates) {
            ClientIdentificationDTO confirmed = confirm(candidate, probeTemplate);
            if (confirmed != null) {
                return confirmed;
            }
        }
        LOG.info("Recall rejected all {} candidate(s) at verification", candidates.size());
        return outcome(NO_MATCH_FOUND, "Could not identify clients");
    }

    /** Candidate ids and scores only; the gallery's view of who owns a print is not trusted. */
    private List<Candidate> candidates(NBiometricClient galleryClient, byte[] probeTemplate) {
        List<Candidate> candidates = new ArrayList<>();
        try (NSubject probe = subjectOf(probeTemplate)) {
            NBiometricStatus status = galleryClient.identify(probe);
            if (!NBiometricStatus.OK.equals(status)) {
                return candidates;
            }
            NSubject.MatchingResultCollection results = probe.getMatchingResults();
            for (int i = 0; i < results.size(); i++) {
                NMatchingResult result = results.get(i);
                String printId = printIdOf(result.getId());
                if (printId != null) {
                    candidates.add(new Candidate(printId, result.getScore()));
                }
            }
        }
        return candidates;
    }

    /**
     * @return the identified client, or null when this candidate does not hold up
     */
    private ClientIdentificationDTO confirm(Candidate candidate, byte[] probeTemplate) {
        Optional<Biometric> stored = biometricRepository.findById(candidate.printId);
        if (!stored.isPresent()) {
            LOG.warn("Recall candidate {} is no longer in the database; the gallery is stale", candidate.printId);
            gallery.invalidate();
            return null;
        }

        Biometric print = stored.get();
        byte[] record = StoredTemplate.toFmr(print.getTemplate());
        if (record == null || record.length < MINIMUM_TEMPLATE_LENGTH) {
            LOG.warn("Recall candidate {} has no readable template", candidate.printId);
            return null;
        }

        if (!verified(probeTemplate, record)) {
            LOG.info("Recall candidate {} scored {} in the search but failed verification",
                    candidate.printId, candidate.score);
            return null;
        }

        String personUuid = print.getPersonUuid();
        if (personUuid == null || personUuid.trim().isEmpty()) {
            LOG.warn("Recall candidate {} is verified but belongs to nobody", candidate.printId);
            return null;
        }

        List<IdentifiedClient> found = jdbcTemplate.query(PATIENT_SQL, new Object[] { personUuid },
                new BeanPropertyRowMapper<>(IdentifiedClient.class));
        if (found.isEmpty()) {
            LOG.warn("Recall verified print {} for person {}, who has no patient record",
                    candidate.printId, personUuid);
            return null;
        }

        LOG.info("Recall identified person {} from print {} (facility {}, {}), search score {}",
                personUuid, candidate.printId, facilityOf(candidate.printId),
                print.getTemplateType(), candidate.score);
        return identified(found.get(0), personUuid);
    }

    private String facilityOf(String printId) {
        try {
            return biometricRepository.getPrintFacility(printId).map(String::valueOf).orElse("unknown");
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** One to one against the stored bytes, so a wrong entry in the gallery cannot decide. */
    private boolean verified(byte[] probeTemplate, byte[] storedRecord) {
        try (NSubject probe = subjectOf(probeTemplate);
             NSubject stored = subjectOf(storedRecord)) {
            return NBiometricStatus.OK.equals(verifier.verify(probe, stored));
        } catch (Exception e) {
            LOG.warn("Recall verification could not run: {}", e.getMessage());
            return false;
        }
    }

    private static NSubject subjectOf(byte[] record) {
        NSubject subject = new NSubject();
        subject.setTemplateBuffer(new NBuffer(normalisedViewNumber(record)));
        return subject;
    }

    /** Byte 25 holds viewNumber|impressionType; most stored templates carry one the SDK rejects. */
    private static byte[] normalisedViewNumber(byte[] record) {
        byte[] copy = record.clone();
        copy[25] = 0x00;
        return copy;
    }

    /** Gallery ids are biometricId#personUuid. */
    private static String printIdOf(String galleryId) {
        if (galleryId == null) {
            return null;
        }
        int separator = galleryId.indexOf('#');
        return separator < 0 ? galleryId : separator == 0 ? null : galleryId.substring(0, separator);
    }

    private static ClientIdentificationDTO identified(IdentifiedClient client, String personUuid) {
        ClientIdentificationDTO dto = outcome(MATCH_FOUND, "Client identified");
        dto.setId(client.getId());
        dto.setPersonUuid(personUuid);
        dto.setSex(client.getSex());
        dto.setSurname(client.getSurname());
        dto.setFirstName(client.getFirstName());
        dto.setOtherName(client.getOtherName());
        dto.setHospitalNumber(client.getHospitalNumber());
        return dto;
    }

    private static ClientIdentificationDTO outcome(String messageType, String message) {
        ClientIdentificationDTO dto = new ClientIdentificationDTO();
        dto.setMessageType(messageType);
        dto.setMessage(message);
        return dto;
    }

    private static final class Candidate {
        private final String printId;
        private final int score;

        private Candidate(String printId, int score) {
            this.printId = printId;
            this.score = score;
        }
    }
}
