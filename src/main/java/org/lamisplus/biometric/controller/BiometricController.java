package org.lamisplus.biometric.controller;

import com.neurotec.biometrics.*;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.standards.*;
import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.devices.NFScanner;
import com.neurotec.io.NBuffer;
import com.neurotec.licensing.NLicense;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.controller.vm.DeduplicationResponse;
import org.lamisplus.biometric.controller.vm.MatchedPair;
import org.lamisplus.biometric.domain.dto.*;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.lamisplus.biometric.util.LibraryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BiometricController {
    private NDeviceManager deviceManager;
    private NBiometricClient client;
    // private NBiometricClient clientForDeduplication;
    private final Set<CapturedBiometricDto> capturedBiometricDtos = new HashSet<>();
    private final String BIOMETRICS_URL_VERSION_ONE = "/api/v1/biometrics";
    private final String NEUROTEC_URL_VERSION_ONE = "/api/v1/biometrics/neurotec";
    private final BiometricRepository biometricRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final List<NSubject> galleries = new ArrayList<>();
    @Value("${server.port}")
   private String activePort;

    @Value("${server.quality}")
    private long quality;

    @GetMapping(BIOMETRICS_URL_VERSION_ONE + "/reader")
    public List<Device> getReaders() {
        //GET - http://localhost:8282/api/v1/biometrics//reader
        List<Device> devices = new ArrayList<>();
        getDevices().forEach(device -> {
            Device d = new Device();
            d.setDeviceName(device.getDisplayName());
            d.setId(device.getId());
            devices.add(d);
        });
        return devices;
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/server")
    public ResponseEntity<String> getServerUrl() {
        //GET - http://localhost:8282/api/v1/biometrics/server
        String activeUrl = "http://localhost:"+ activePort;

        return ResponseEntity.ok(activeUrl);
    }

    @PostMapping(BIOMETRICS_URL_VERSION_ONE + "/deduplicate/{patientId}")
    public DeduplicationResponse deduplicate(
            @PathVariable("patientId") String patientId,
            @RequestBody Set<CapturedBiometricDto> capturedBiometricDto
    ){
        LOG.info("Fingers to deduplicate {}", capturedBiometricDto.size());
        return runDeduplication(capturedBiometricDto, patientId);
        // return "Done with deduplication";
    }

    @PostMapping(BIOMETRICS_URL_VERSION_ONE + "/enrollment")
    public CaptureResponse enrollment(@RequestParam String reader, @RequestParam(required = false, defaultValue = "false") Boolean isNew,
                                      @Valid @RequestBody CaptureRequestDTO captureRequestDTO) {
        LOG.info("Captured Size ****, {}", captureRequestDTO.getCaptureBiometrics().size());
        Set<CapturedBiometricDto> capturedBiometricDtosIn =
                captureRequestDTO.getCaptureBiometrics();
        //initializing response
        CaptureResponse result = getBiometricEnrollmentDto(captureRequestDTO);

        //checking if new
        if(Boolean.TRUE.equals(isNew)){
            this.emptyStoreByPersonId(captureRequestDTO.getPatientId());
        }
        try {
            reader = URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ignored) {
        }

        try (NSubject subject = new NSubject()) {
            final NFinger finger = new NFinger();
            finger.setPosition(NFPosition.UNKNOWN);
            subject.getFingers().add(finger);

            if (this.scannerIsNotSet(reader)) {
                result.getMessage().put("ERROR", "Biometrics Scanner not found");
                result.setType(CaptureResponse.Type.ERROR);
                return result;
            }

            NBiometricStatus status = client.capture(subject);

            if (status.equals(NBiometricStatus.OK)) {
                status = client.createTemplate(subject);
                if (status.equals(NBiometricStatus.OK)) {
                    //Checking if fingerprint is already captured in the current capturing process
                    status = deduplicateIfFingerIsAlreadyCapturedInTheCurrentProcess(
                            subject, client, captureRequestDTO
                    );
                    if (status.equals(NBiometricStatus.OK)) {
                        result.getMessage().put("ERROR", "Fingerprint already captured");
                        result.setType(CaptureResponse.Type.ERROR);
                        return result;
                    }
                    //End of check

                    // Check if fingerprint already exists
                    /*status = deduplicateIfFingerIsAlreadyCapturedWithOlderPrints(
                            subject, client ,captureRequestDTO.getFacilityId());

                    if (status.equals(NBiometricStatus.OK)) {
                        result.getMessage().put("ERROR", "Fingerprint already exists");
                        result.setType(CaptureResponse.Type.ERROR);
                        LOG.info("Status is ****** {}", status);
                        return result;
                    }*/

                    //check for quality
                    long imageQuality = subject.getFingers().get(0).getObjects().get(0).getQuality();
                    if (imageQuality < quality) {
                        result.getMessage().put("ERROR", "Image quality is low - " + imageQuality);
                        result.setType(CaptureResponse.Type.ERROR);
                        return result;
                    }

                    result.setDeviceName(reader);
                    if (status.equals(NBiometricStatus.OK)) {
                        if (Boolean.TRUE.equals(isNew)) {
                            result.getMessage().put("ERROR", "Fingerprint already captured");
                            result.setType(CaptureResponse.Type.ERROR);
                            return result;
                        }

                    } else {
                        byte[] isoTemplate = subject.getTemplateBuffer(CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                                CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                                FMRecord.VERSION_ISO_20).toByteArray();

                        /*FMRecord test = new FMRecord(new NBuffer(isoTemplate), BDIFStandard.ISO);
                        if (test.getVersion().getMajor() != 2) {
                            NFTemplate nfTemplate = test.toNFTemplate();
                            FMRecord fmRecord = new FMRecord(nfTemplate, BDIFStandard.ISO, FMRecord.VERSION_ISO_20);
                            isoTemplate = fmRecord.save(BDIFEncodingType.TRADITIONAL).toByteArray();
                        }*/

                        byte firstTwoChar = isoTemplate[0];
                        //String template = "46% OR AC%";
                        String template = Integer.toHexString(firstTwoChar) + "%";

                        System.out.println("********************************************************");
                        System.out.println("firstTwoChar inside: " + firstTwoChar);
                        System.out.println("You convert?: " + template);
                        System.out.println("********************************************************");

                        /*Set<StoredBiometric> biometricsInFacility = biometricRepository
                                .findByFacilityIdWithTemplate(captureRequestDTO.getFacilityId(), template);
    */

                        CapturedBiometricDto capturedBiometricDTO = new CapturedBiometricDto();
                        capturedBiometricDTO.setTemplate(isoTemplate);
                        capturedBiometricDTO.setTemplateType(captureRequestDTO.getTemplateType());
                        capturedBiometricDtosIn.add(capturedBiometricDTO);

                        result.setTemplate(isoTemplate);
                        result.setIso(true);

                        result.setCapturedBiometricsList(capturedBiometricDtosIn);
                        imageQuality = subject.getFingers().get(0).getObjects().get(0).getQuality();
                        result.setImageQuality(imageQuality);
                        String base64Image = Base64.getEncoder().encodeToString(isoTemplate);
                        result.setImage(base64Image);
                        result.setType(CaptureResponse.Type.SUCCESS);
                    }
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
        }
        client.clear();

        return result;
    }

    @SneakyThrows
    private ClientIdentificationDTO clientIdentification (@RequestParam String reader) {
        client.clear();
        reader = URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        Map<String, String> response = new HashMap<>();

        ClientIdentificationDTO clientIdentificationDTO = new ClientIdentificationDTO();

        try (NSubject subject = new NSubject()) {
            final NFinger finger = new NFinger();
            finger.setPosition(NFPosition.UNKNOWN);
            subject.getFingers().add(finger);


            if (this.scannerIsNotSet(reader)) {
                clientIdentificationDTO.setMessageType("ERROR");
                clientIdentificationDTO.setMessage("Biometrics Scanner not found");
                return clientIdentificationDTO;
            }

            NBiometricStatus status = client.capture(subject);
            if (status.equals(NBiometricStatus.OK)) {
                List<Biometric> biometricList =  biometricRepository
                        .getAllFingerPrintsByFacility();

                NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
                biometricList.parallelStream()
                        .filter(fingerPrint -> fingerPrint.getTemplate() != null)
                        .forEach(fingerPrint -> {
                            if (fingerPrint.getTemplate().length > 0){
                                NSubject subjectN = new NSubject();
                                subjectN.setTemplateBuffer(new NBuffer(fingerPrint.getTemplate()));
                                subjectN.setId(fingerPrint.getId() + "#" +fingerPrint.getPersonUuid());
                                try {
                                    task.getSubjects().add(subjectN);
                                } catch (Exception e) {
                                    task.getSubjects().remove(subjectN);
                                }
                            }
                        });
                try {
                    client.performTask(task);
                } catch (Exception e){
                    e.printStackTrace();
                }
                NBiometricStatus s = client.identify(subject);
                if (s.equals(NBiometricStatus.OK)) {
                    String uuid = subject.getMatchingResults().get(0).getId();
                }else {
                    clientIdentificationDTO.setMessageType("SUCCESS_NO_MATCH_FOUND");
                    clientIdentificationDTO.setMessage("Could not identify clients");
                    return  clientIdentificationDTO;
                }

            }
        } catch (Exception e){

        }

        client.clear();

        return clientIdentificationDTO;
    }

    private DeduplicationResponse runDeduplication(Set<CapturedBiometricDto> printsToDeduplicate, String patientId){
        client.clear();
        DeduplicationResponse deduplicationResponse = new DeduplicationResponse();
        final List<NSubject> subjects = new ArrayList<>();

        printsToDeduplicate.forEach(capturedBiometricDto -> {
            if(capturedBiometricDto.getId().isEmpty()){
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

        biometricList.parallelStream()
                .filter(fingerPrint -> fingerPrint.getTemplate() != null)
                .forEach(fingerPrint -> {
                    if (fingerPrint.getTemplate().length > 0){
                        NSubject subject = new NSubject();
                        subject.setTemplateBuffer(new NBuffer(fingerPrint.getTemplate()));
                        subject.setId(fingerPrint.getId() + "#" +fingerPrint.getPersonUuid());
                        subjects.add(subject);
                    }
                });

        NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
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
            client.performTask(task);
        } catch (Exception e){
            e.printStackTrace();
        }

        AtomicReference<Integer> numberOfMatch = new AtomicReference<>(0);

        currentSubjects.parallelStream()
                .forEach(subject -> {
                    NBiometricStatus s = client.identify(subject);
                    if(s.equals(NBiometricStatus.OK)){
                        List<MatchedPair> matchedPairList = new ArrayList<>();

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
                            // Building Match pair data
                        }
                        // Saving Matched Pair data
                        saveMatchPair(matchedPairList);

                        numberOfMatch.updateAndGet(v -> v + 1);
                    }
                });
        deduplicationResponse.setMessageType("SUCCESS");
        deduplicationResponse.setMessage("Deduplication process successful");
        deduplicationResponse.setNumberOfMatchedFingers(numberOfMatch.get());
        client.clear();
        return deduplicationResponse;
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
            NBiometricClient client,
            CaptureRequestDTO captureRequestDTO
            ) {
        //client.clear();
        Set<CapturedBiometricDto> templates = captureRequestDTO.getCaptureBiometrics();
        List<NSubject> currentGallery = new ArrayList<>();
        for (CapturedBiometricDto template : templates) {
            //String id = captureRequestDTO.getId();
            NSubject gallery = new NSubject();

            String id = UUID.randomUUID().toString();

            gallery.setTemplateBuffer(new NBuffer(template.getTemplate()));
            gallery.setId(id);
            currentGallery.add(gallery);

            NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
            task.getSubjects().addAll(currentGallery);
            // LOG.info("Galley Size is **** {}", currentGallery.size());
            try {
                client.performTask(task);
            } catch (Exception e) {
                currentGallery.remove(gallery);
            }
            try {
                if (!task.getStatus().equals(NBiometricStatus.OK)) {
                    currentGallery.remove(gallery);
                }
            } catch (Exception e) {
                currentGallery.remove(gallery);
            }
        }
        // Identifying with existing fingerprints
        return client.identify(subject);
    }

    private boolean scannerIsNotSet(String reader) {
        for (NDevice device : getDevices()) {
            if (device.getId().equals(reader)) {
                client.setFingerScanner((NFScanner) device);
                return false;
            }
        }
        return true;
    }

    private void initDeviceManager() {
        deviceManager = new NDeviceManager();
        deviceManager.setDeviceTypes(EnumSet.of(NDeviceType.FINGER_SCANNER));
        deviceManager.setAutoPlug(true);
        deviceManager.initialize();
    }

    private NDeviceManager.DeviceCollection getDevices() {
        return deviceManager.getDevices();
    }

    private void obtainLicense(String component) {
        try {
            boolean result = NLicense.obtainComponents("/local", "5000", component);
            LOG.info("Obtaining license: {}: {}", component, result);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void createClient() {
        client = new NBiometricClient();
        client.setMatchingThreshold(60);
        client.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        client.setFingersTemplateSize(NTemplateSize.LARGE);
        client.initialize();
    }

    @PostConstruct
    public void init() {
        LibraryManager.initLibraryPath();
        initDeviceManager();

        obtainLicense("Biometrics.FingerExtraction");
        obtainLicense("Biometrics.Standards.FingerTemplates");
        obtainLicense("Biometrics.FingerMatching");

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

}
