package org.lamisplus.biometric.controller;

import com.neurotec.biometrics.*;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.standards.*;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageFormat;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NError;
import com.neurotec.lang.NotActivatedException;
import com.neurotec.util.NVersion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lamisplus.biometric.controller.vm.*;
import org.lamisplus.biometric.controller.vm.MatchedPair;
import org.lamisplus.biometric.domain.dto.*;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.lamisplus.biometric.service.FingerCapture;
import org.lamisplus.biometric.service.FingerScannerManager;
import org.lamisplus.biometric.service.IdentificationGallery;
import org.lamisplus.biometric.service.PatientRecall;
import org.lamisplus.biometric.util.StoredTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@RestController
@Slf4j
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BiometricController {
    private NBiometricClient client;
    private final Set<CapturedBiometricDto> capturedBiometricDtos = new HashSet<>();
    private final String BIOMETRICS_URL_VERSION_ONE = "/api/v1/biometrics";
    private final String NEUROTEC_URL_VERSION_ONE = "/api/v1/biometrics/neurotec";
    private final BiometricRepository biometricRepository;
    private final JdbcTemplate jdbcTemplate;
    private final FingerScannerManager fingerScannerManager;
    private final IdentificationGallery identificationGallery;
    private final FingerCapture fingerCapture;
    private final PatientRecall patientRecall;

    private Deduplication rDeduplicationDTO;
    private final Map<String, String> details = new HashMap<>();

    private static final List<NSubject> galleries = new ArrayList<>();
    @Value("${server.port}")
   private String activePort;

    @Value("${server.quality}")
    private long quality;

    @GetMapping(BIOMETRICS_URL_VERSION_ONE + "/reader")
    public List<Device> getReaders() {
        List<Device> devices = new ArrayList<>();
        fingerScannerManager.getDevices().forEach(device -> {
            Device d = new Device();
            d.setDeviceName(device.getDisplayName());
            d.setId(device.getId());
            devices.add(d);
        });
        LOG.info("Devices ****** {}", devices);
        return devices;
    }


    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/diagnostics")
    public ResponseEntity<Map<String, Object>> diagnostics(
            @RequestParam(required = false, defaultValue = "false") boolean deep) {
        return ResponseEntity.ok(fingerScannerManager.diagnostics(deep));
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/gallery")
    public ResponseEntity<Map<String, Object>> gallery(@RequestParam(required = false) String personUuid) {
        return ResponseEntity.ok(identificationGallery.status(personUuid));
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/boot")
    public ErrorCodeDTO boot(@RequestParam String reader) {
        // Binds too, so the device opens here rather than on the first finger presented.
        fingerScannerManager.bindScanner(client, reader);
        return fingerScannerManager.bootStatus(reader);
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/server")
    public ResponseEntity<String> getServerUrl() {
        String activeUrl = "http://localhost:"+ activePort;

        return ResponseEntity.ok(activeUrl);
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/angular/test")
    public ResponseEntity<Map<String, Object>> angularTest(@RequestParam String reader){

        // Shares the capture client: two clients on one scanner take turns losing the device.
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("message", "Hello, world!");
        responseData.put("status", HttpStatus.OK.value());

        try {
            reader = URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ignored) {
        }

        try (NSubject subject = new NSubject()) {

            if (this.scannerIsNotSet(reader)) {
                responseData.put("ERROR", "Biometrics Scanner not found");
                responseData.put("status", HttpStatus.OK.value());
                return new ResponseEntity<>(responseData, HttpStatus.OK);
            }

            NBiometricStatus status = fingerCapture.capture(client, subject, reader);

            if (status.equals(NBiometricStatus.OK)) {
                status = client.createTemplate(subject);
                if (status.equals(NBiometricStatus.OK)) {
                    byte[] isoTemplate = subject.getTemplateBuffer(CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                            CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                            FMRecord.VERSION_ISO_20).toByteArray();
                    responseData.put("template", isoTemplate);

                    long imageQuality = subject.getFingers().get(0).getObjects().get(0).getQuality();
                    responseData.put("quality", imageQuality);

                    NImage image = subject.getFingers().get(0).getImage();
                    responseData.put("imageWidth", image.getWidth());
                    responseData.put("imageHeight", image.getHeight());

                    NBuffer buffer = image.save();
                    byte[] array = buffer.toByteArray();
                    String encodeImage = Base64.getEncoder().withoutPadding().encodeToString(array);

                    responseData.put("image", "data:image/png;base64,".concat(encodeImage));
                } else {
                    LOG.info("Could not create template");
                }
            } else {
                LOG.info("Could not capture template");
            }
        }
        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

    @PostMapping(BIOMETRICS_URL_VERSION_ONE + "/deduplicate/{patientId}")
    public DeduplicationResponse deduplicate(
            @PathVariable("patientId") String patientId,
            @RequestBody Set<CapturedBiometricDto> capturedBiometricDto
    ){
        LOG.info("Fingers to deduplicate {}", capturedBiometricDto.size());
        return runDeduplication(capturedBiometricDto, patientId);
    }


    @PostMapping(BIOMETRICS_URL_VERSION_ONE + "/enrollment")
    public CaptureResponse enrollment(
            @RequestParam String reader,
            @RequestParam(required = false, defaultValue = "false") Boolean isNew,
            @RequestParam(required = false, defaultValue = "false") Boolean recapture,
            @RequestParam(required = false, defaultValue = "false") Boolean identify,
            @Valid @RequestBody CaptureRequestDTO captureRequestDTO,
            @RequestParam(required = false, defaultValue = "LOCAL") String identificationType
    ) {
        LOG.info("Captured Size ****, {}", captureRequestDTO.getCapturedBiometricsList().size());
        Set<CapturedBiometricDto> capturedBiometricDtosIn =
                captureRequestDTO.getCapturedBiometricsList();
        CaptureResponse result = getBiometricEnrollmentDto(captureRequestDTO);

        if(Boolean.TRUE.equals(isNew)){
            this.emptyStoreByPersonId(captureRequestDTO.getPatientId());
        }
        try {
            reader = URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ignored) {
        }

        try (NSubject subject = new NSubject()) {

            if (this.scannerIsNotSet(reader)) {
                result.getMessage().put("ERROR", "Biometrics Scanner not found");
                result.setDeduplication(captureRequestDTO.getDeduplication());
                result.setType(CaptureResponse.Type.ERROR);
                return result;
            }

            NBiometricStatus status = fingerCapture.capture(client, subject, reader);

            if (status.equals(NBiometricStatus.OK)) {

                status = client.createTemplate(subject);

                if (status.equals(NBiometricStatus.OK)) {
                    result.setDeviceName(reader);

                    byte[] isoTemplate = subject.getTemplateBuffer(CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                            CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                            FMRecord.VERSION_ISO_20).toByteArray();

                    result.setTemplate(isoTemplate);
                    long imageQuality = subject.getFingers().get(0).getObjects().get(0).getQuality();
                    result.setMainImageQuality(imageQuality);

                    if (imageQuality < quality) {
                        result.getMessage().put("ERROR", "Image quality is low - " + imageQuality);
                        result.setType(CaptureResponse.Type.ERROR);
                        result.setDeduplication(captureRequestDTO.getDeduplication());
                        client.clear();
                        return result;
                    }

                    if (identify) {

                        switch (identificationType) {
                            case "PIMS":
                                result.setType(CaptureResponse.Type.SUCCESS);
                                return result;
                            case "LOCAL":
                                ClientIdentificationDTO clientIdentificationDTO = patientRecall.identify(isoTemplate);
                                result.setClientIdentificationDTO(clientIdentificationDTO);
                                result.setType(CaptureResponse.Type.SUCCESS);
                                return result;
                            default:
                                result.getMessage().put("ERROR", "Could not identify the supplied identification type " + identificationType);
                                result.setType(CaptureResponse.Type.ERROR);
                                return result;
                        }

                    }

                    // Cost 12 is a fixed quarter-second or more, and nothing below depends on it.
                    CompletableFuture<String> hashed =
                            CompletableFuture.supplyAsync(() -> bcryptHash(isoTemplate));

                    status = deduplicateIfFingerIsAlreadyCapturedInTheCurrentProcess(
                            subject, captureRequestDTO
                    );
                    if (status.equals(NBiometricStatus.OK)) {
                        result.getMessage().put("ERROR", "Fingerprint already captured");
                        result.setType(CaptureResponse.Type.ERROR);
                        result.setDeduplication(captureRequestDTO.getDeduplication());
                        client.clear();
                        return result;
                    }
                    LOG.info("Recapture choice ******* {}", recapture);
                    Map<String, Object> matchData = new HashMap<>();
                    if(recapture){
                        matchData = matchAgainstBaseline(subject, captureRequestDTO.getPatientId(),
                                captureRequestDTO.getTemplateType(), captureRequestDTO.getDeduplication());
                        result.setDeduplication(captureRequestDTO.getDeduplication());
                    }

                    CapturedBiometricDto capturedBiometricDTO = new CapturedBiometricDto();
                    capturedBiometricDTO.setTemplate(isoTemplate);
                    capturedBiometricDTO.setTemplateType(captureRequestDTO.getTemplateType());
                    capturedBiometricDTO.setHashed(hashed.join());
                    capturedBiometricDTO.setImageQuality((int) imageQuality);
                    if (matchData != null && !matchData.isEmpty()){
                        capturedBiometricDTO.setMatchBiometricId(matchData.get("matchBiometricId").toString());
                        capturedBiometricDTO.setMatchType(matchData.get("matchType").toString());
                        capturedBiometricDTO.setMatchPersonUuid(matchData.get("matchPersonUuid").toString());
                        result.getMessage().put("match", "Perfect ...");
                    } else {
                        result.getMessage().put("match", "Imperfect...");
                    }
                    capturedBiometricDtosIn.add(capturedBiometricDTO);

                    result.setIso(true);
                    result.setCapturedBiometricsList(capturedBiometricDtosIn);

                    String base64Image = Base64.getEncoder().encodeToString(isoTemplate);
                    result.setImage(isoTemplate);
                    result.setType(CaptureResponse.Type.SUCCESS);
                } else {
                    LOG.info("Could not create template");
                    result.getMessage().put("ERROR", "Could not create template");
                    result.setType(CaptureResponse.Type.ERROR);
                }
            } else {
                LOG.info("Could not capture template");
                result.getMessage().put("ERROR", "Could not create template");
                result.setType(CaptureResponse.Type.ERROR);
            }
        } catch (NotActivatedException e) {
            LOG.error("Neurotec licence is not activated on this machine, so capture is unavailable. "
                    + "Check the licence components logged at start-up.");
            result.getMessage().put("ERROR", "Neurotec licence is not activated on this machine");
            result.setDeduplication(captureRequestDTO.getDeduplication());
            result.setType(CaptureResponse.Type.ERROR);
            return result;
        } catch (Exception e) {
            LOG.error("Error while capturing a fingerprint", e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            result.getMessage().put("ERROR", "The scanner reported: " + reason);
            result.setDeduplication(captureRequestDTO.getDeduplication());
            result.setType(CaptureResponse.Type.ERROR);
            return result;
        }
        client.clear();

        return result;
    }

    /**
     * Mutates {@code deduplication} with the recapture counters as a side effect.
     *
     * @return the verification match data, or null when no baseline print matches.
     */
    @SneakyThrows
    private Map<String, Object> matchAgainstBaseline(
            NSubject nSubject, Long patientId, String recapturedTemplateType,
            Deduplication deduplication
    ) {
        NBiometricClient biometricClient = new NBiometricClient();
        try {
            biometricClient.setMatchingThreshold(96);
            biometricClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);
            biometricClient.setFingersQualityThreshold((byte) 75);

            List<Biometric> baselinePrints = biometricRepository.getPatientBaselineFingerprints(patientId);

            List<NSubject> baselineSubjects = baselinePrints.parallelStream()
                    .filter(fingerPrint -> readableRecord(fingerPrint.getTemplate()) != null)
                    .map(fingerPrint -> {
                        NSubject subject = new NSubject();
                        subject.setTemplateBuffer(new NBuffer(normalisedViewNumber(readableRecord(fingerPrint.getTemplate()))));
                        subject.setId(fingerPrint.getId() + "#" + fingerPrint.getPersonUuid());
                        return subject;
                    })
                    .collect(Collectors.toList());

            NBiometricTask task = biometricClient.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
            baselineSubjects
                    .forEach(subject -> {
                        try {
                            task.getSubjects().add(subject);
                        } catch (Exception e) {
                            task.getSubjects().remove(subject);
                        }
                    });

            try {
                biometricClient.performTask(task);
            } catch (Exception e){
                LOG.error("Enrolling the baseline gallery failed: {}", e.getMessage(), e);
            }

            NBiometricStatus status = biometricClient.identify(nSubject);

            if (!status.equals(NBiometricStatus.OK)) {
                deduplication.setUnmatchedCount(deduplication.getUnmatchedCount() + 1);
                return null;
            }

            NMatchingResult best = nSubject.getMatchingResults().get(0);
            String[] matchedId = best.getId().split("#");
            String baselineId = matchedId[0];
            Biometric baseline = baselinePrints
                    .stream()
                    .filter(f -> StringUtils.equals(f.getId(), baselineId))
                    .findFirst().orElse(null);
            if (baseline == null) {
                LOG.warn("Recapture matched {}, which is not among the baseline prints loaded", baselineId);
                deduplication.setUnmatchedCount(deduplication.getUnmatchedCount() + 1);
                return null;
            }
            String baselineTemplateType = baseline.getTemplateType();
            deduplication.setMatchedCount(deduplication.getMatchedCount() + 1);

            String key = "BASELINE_" + baselineTemplateType.toUpperCase().replaceAll(" ", "_");
            String value = "RECAPTURE_" + recapturedTemplateType.toUpperCase().replaceAll(" ", "_");
            details.put(key, value);
            deduplication.setDetails(details);

            boolean samePosition = StringUtils.equals(baselineTemplateType, recapturedTemplateType);
            if (samePosition) {
                deduplication.setPerfectMatchCount(deduplication.getPerfectMatchCount() + 1);
            } else {
                deduplication.setImperfectMatchCount(deduplication.getImperfectMatchCount() + 1);
            }

            Map<String, Object> matchData = new HashMap<>();
            matchData.put("matchBiometricId", baselineId);
            matchData.put("matchPersonUuid", matchedId.length > 1 ? matchedId[1] : baseline.getPersonUuid());
            matchData.put("matchType", samePosition ? "Perfect Match" : "Imperfect Match");
            return matchData;
        } finally {
            biometricClient.dispose();
        }
    }


    private DeduplicationResponse runDeduplication(Set<CapturedBiometricDto> printsToDeduplicate, String patientId){

        NBiometricClient deduplication = null;

        client.clear();
        DeduplicationResponse deduplicationResponse = new DeduplicationResponse();

        try {
            deduplication = new NBiometricClient();
            deduplication.setMatchingThreshold(144);
            deduplication.setFingersMatchingSpeed(NMatchingSpeed.LOW);
            deduplication.setFingersReturnBinarizedImage(true);
            deduplication.setMatchingMaximalResultCount(100);

            printsToDeduplicate.forEach(capturedBiometricDto -> {
                if(StringUtils.isBlank(capturedBiometricDto.getId())){
                    capturedBiometricDto.setId(UUID.randomUUID().toString());
                }
            });

            List<NSubject> currentSubjects = new ArrayList<>();
            for (CapturedBiometricDto template : printsToDeduplicate) {
                NSubject subject = new NSubject();
                subject.setTemplateBuffer(new NBuffer(template.getTemplate()));
                subject.setId(template.getId());
                currentSubjects.add(subject);
            }

            List<Biometric> biometricList =  biometricRepository
                    .getAllFingerPrintsByFacility();

            final List<NSubject> subjects = biometricList.parallelStream()
                    .filter(fingerPrint -> fingerPrint.getTemplate() != null && fingerPrint.getTemplate().length > 0)
                    .map(fingerPrint -> {
                        NSubject subject = new NSubject();
                        subject.setTemplateBuffer(new NBuffer(fingerPrint.getTemplate()));
                        subject.setId(fingerPrint.getId() + "#" +fingerPrint.getPersonUuid());
                        return subject;
                    })
                    .collect(Collectors.toList());

            NBiometricTask task = deduplication.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
            subjects
                    .forEach(nSubject -> {
                        try {
                            task.getSubjects().add(nSubject);
                        } catch (Exception e) {
                            task.getSubjects().remove(nSubject);
                            LOG.error("Error adding subject ***** {}", e.getMessage());
                        }
                    });
            LOG.info("Task is  ******* {}", task.getSubjects().size());
            try {
                deduplication.performTask(task);
            } catch (Exception e){
                e.printStackTrace();
            }

            AtomicReference<Integer> numberOfMatch = new AtomicReference<>(0);

            // Keyed lookups: getProperty raises when the name is absent, and a matching result
            // does not carry the properties set on the subject that was enrolled.
            Map<String, Biometric> galleryById = new HashMap<>();
            for (Biometric fingerPrint : biometricList) {
                galleryById.put(fingerPrint.getId(), fingerPrint);
            }
            Map<String, CapturedBiometricDto> probeById = new HashMap<>();
            for (CapturedBiometricDto template : printsToDeduplicate) {
                probeById.put(template.getId(), template);
            }

            NBiometricClient finalDeduplication = deduplication;
            DeduplicationDetails deduplicationDetails = new DeduplicationDetails();
            List<MatchedFinger> matchedFingerList = Collections.synchronizedList(new ArrayList<>());
            currentSubjects.parallelStream()
                    .forEach(subject -> {
                        NBiometricStatus s = finalDeduplication.identify(subject);
                        if(s.equals(NBiometricStatus.OK)){

                            CapturedBiometricDto probe = probeById.get(subject.getId());
                            if (probe == null) {
                                return;
                            }
                            MatchedFinger matchedFinger = new MatchedFinger();
                            matchedFinger.setFingerType(probe.getTemplateType());
                            matchedFinger.setId(probe.getId());

                            NSubject.MatchingResultCollection nMatchingResults = subject.getMatchingResults();
                            List<PersonMatched> personMatchedList = new ArrayList<>();

                            List<MatchedPair> matchedPairList = new ArrayList<>();

                            for (int j = 0; j < nMatchingResults.size(); j++) {
                                Biometric matched = galleryById.get(
                                        nMatchingResults.get(j).getId().split("#")[0]);
                                if (matched == null) {
                                    continue;
                                }

                                PersonMatched personMatched = new PersonMatched();
                                personMatched.setPatientId("");
                                personMatched.setPatientUuid(matched.getPersonUuid());
                                personMatched.setFingerType(matched.getTemplateType());
                                personMatched.setFingerId(matched.getId());

                                PatientPerson patientPerson = findByUuid(matched.getPersonUuid());
                                personMatched.setFirstName(patientPerson.getFirstName());
                                personMatched.setSurname(patientPerson.getSurname());
                                personMatched.setPatientId(patientPerson.getPatientId());
                                personMatched.setPatientUuid(patientPerson.getPatientUuid());
                                personMatched.setHospitalNumber(patientPerson.getHospitalNumber());
                                personMatched.setAddress(patientPerson.getAddress());

                                personMatchedList.add(personMatched);
                            }
                            matchedFinger.setPersonsMatched(personMatchedList);
                            matchedFingerList.add(matchedFinger);

                            for (int j = 0; j < subject.getMatchingResults().size(); j++) {
                                String [] id = subject.getMatchingResults().get(j).getId().split("#");
                                String matchedId = id[1];
                                String matchFingerId = id[0];
                                Integer score = subject.getMatchingResults().get(j).getScore();
                                MatchedPair matchedPair = new MatchedPair();

                                matchedPair.setDuplicatePatientId(matchedId);
                                matchedPair.setEnrolledPatientId(patientId);

                                String matchedPersonTemplateType = biometricList
                                        .stream()
                                        .filter(f -> Objects.requireNonNull(f.getId()).equals(matchFingerId))
                                        .map(Biometric::getTemplateType)
                                        .findFirst().orElse(null);
                                matchedPair.setDuplicatePatientFingerType(String.valueOf(matchedPersonTemplateType));

                                String enrolledPatientTemplateType = printsToDeduplicate
                                        .stream()
                                        .filter(f -> f.getId().equals(subject.getId()))
                                        .map(CapturedBiometricDto::getTemplateType)
                                        .findFirst().orElse(null);
                                matchedPair.setEnrolledPatientFingerType(enrolledPatientTemplateType);
                                matchedPair.setScore(score);
                                matchedPairList.add(matchedPair);
                            }

                            numberOfMatch.updateAndGet(v -> v + 1);
                        }
                    });
            deduplicationDetails.setMatchedFingers(matchedFingerList);
            deduplicationResponse.setMessageType("SUCCESS");
            deduplicationResponse.setMessage("Deduplication process successful");
            deduplicationResponse.setNumberOfMatchedFingers(numberOfMatch.get());

        }catch (Throwable th){
            th.printStackTrace();
            LOG.error("An error occurred *********** {}", th.getMessage());
        } finally {
            // clear() empties the subject database but leaves the native engine alive.
            if (deduplication != null) {
                try {
                    deduplication.dispose();
                } catch (Exception e) {
                    LOG.warn("Could not dispose the deduplication engine: {}", e.getMessage());
                }
            }
        }
        return deduplicationResponse;
    }

    public PatientPerson findByUuid(String uuid) {
        String sql = "SELECT id, uuid, surname, sex, date_of_birth, first_name, address->'address'->0->'line'->0 AS address, hospital_number " +
                "FROM patient_person " +
                "WHERE uuid = ?";
        return jdbcTemplate.queryForObject(sql, new Object[]{uuid}, (rs, rowNum) -> {
            PatientPerson patientPerson = new PatientPerson();
            patientPerson.setPatientId(rs.getString("id"));
            patientPerson.setPatientUuid(rs.getString("uuid"));
            patientPerson.setSurname(rs.getString("surname"));
            patientPerson.setGender(rs.getString("sex"));
            patientPerson.setDateOfBirth(rs.getString("date_of_birth"));
            patientPerson.setFirstName(rs.getString("first_name"));
            patientPerson.setAddress(rs.getString("address"));
            patientPerson.setHospitalNumber(rs.getString("hospital_number"));
            return patientPerson;
        });
    }

    private void saveMatchPair(List<MatchedPair> matchedPairList) {
        matchedPairList.forEach(m -> {
            LOG.info(String.valueOf(m));
            jdbcTemplate.update(
                    "INSERT INTO matched_pair (enrolled_patient_id, duplicate_patient_id, " +
                            "enrolled_patient_finger_type, duplicate_patient_finger_type," +
                            "score) " +
                            "VALUES ((select uuid from patient_person where id = cast(? as bigint)), ?, ?, ?, ?)", m.getEnrolledPatientId(), m.getDuplicatePatientId(),
                    m.getEnrolledPatientFingerType(), m.getDuplicatePatientFingerType(),
                    m.getScore());
        });
    }

    private String returnMatchInfo(String patientUUID){
        patientUUID = patientUUID.split("#")[0];
        String query = "select concat ('Fingerprint matches patient with<br> Name: ', surname, ' ', " +
                "first_name, ' <br> Hospital Number: ', hospital_number) " +
                "from patient_person " +
                "where uuid = ?";
        return jdbcTemplate.queryForObject(query, new Object[] {patientUUID}, String.class);
    }

    private NBiometricStatus deduplicateIfFingerIsAlreadyCapturedInTheCurrentProcess (
            NSubject subject,
            CaptureRequestDTO captureRequestDTO
            ) {
        Set<CapturedBiometricDto> templates = captureRequestDTO.getCapturedBiometricsList();
        if (templates.isEmpty()) {
            return NBiometricStatus.MATCH_NOT_FOUND;
        }

        NBiometricClient biometricClient = new NBiometricClient();
        try {
            biometricClient.setMatchingThreshold(144);
            biometricClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);

            NBiometricTask task = biometricClient.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
            for (CapturedBiometricDto template : templates) {
                try {
                    NSubject gallery = new NSubject();
                    gallery.setTemplateBuffer(new NBuffer(normalisedViewNumber(template.getTemplate())));
                    gallery.setId(UUID.randomUUID().toString());
                    task.getSubjects().add(gallery);
                } catch (Exception e) {
                    LOG.warn("Could not add an already captured finger to the in-session gallery: {}",
                            e.getMessage());
                }
            }

            try {
                biometricClient.performTask(task);
            } catch (Exception e) {
                LOG.error("Enrolling the in-session gallery failed: {}", e.getMessage());
            }

            return biometricClient.identify(subject);
        } finally {
            biometricClient.dispose();
        }
    }

    /** @return true when no such scanner is attached, in which case the caller must not capture. */
    private boolean scannerIsNotSet(String reader) {
        LOG.debug("Reader from REST **** {}", reader);
        return !fingerScannerManager.bindScanner(client, reader);
    }

    private void createClient() {
        client = new NBiometricClient();
        client.setMatchingThreshold(96);
        client.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        client.setFingersTemplateSize(NTemplateSize.LARGE);
        client.initialize();
    }

    @PostConstruct
    public void init() {
        createClient();
    }

    public Boolean emptyStoreByPersonId(Long personId){
        Boolean hasCleared = false;
        if(!BiometricStoreDTO.getPatientBiometricStore().isEmpty() && BiometricStoreDTO.getPatientBiometricStore().get(personId) != null){
            BiometricStoreDTO.getPatientBiometricStore().remove(personId);
            hasCleared = true;
        }
        return hasCleared;
    }

    public CaptureResponse getBiometricEnrollmentDto(CaptureRequestDTO captureRequestDTO){
        CaptureResponse biometricEnrollmentDto = new CaptureResponse();
        biometricEnrollmentDto.setBiometricType(captureRequestDTO.getBiometricType());
        biometricEnrollmentDto.setTemplateType(captureRequestDTO.getTemplateType());
        biometricEnrollmentDto.setPatientId(captureRequestDTO.getPatientId());
        biometricEnrollmentDto.setReason(captureRequestDTO.getReason());
        return biometricEnrollmentDto;
    }

    /** Byte 25 holds viewNumber|impressionType; most stored templates carry one the SDK rejects. */
    private static byte[] readableRecord(byte[] stored) {
        byte[] record = StoredTemplate.toFmr(stored);
        return record != null && record.length > 25 ? record : null;
    }

    static byte[] normalisedViewNumber(byte[] template) {
        byte[] copy = template.clone();
        copy[25] = 0x00;
        return copy;
    }

    public String bcryptHash(byte[] template) {
        String encoded = Base64.getEncoder().encodeToString(template);
        return BCrypt.hashpw(encoded, "$2a$12$MklNDNgs4Agd50cSasj91O");
    }


}
