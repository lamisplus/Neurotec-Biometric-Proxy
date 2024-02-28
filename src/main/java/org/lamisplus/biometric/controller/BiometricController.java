package org.lamisplus.biometric.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neurotec.biometrics.*;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.standards.*;
import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.devices.NFScanner;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageFormat;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NError;
import com.neurotec.licensing.NLicense;
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
import org.lamisplus.biometric.util.LibraryManager;
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


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

    private Deduplication rDeduplicationDTO;
    private final Map<String, String> details = new HashMap<>();

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
        LOG.info("Devices ****** {}", devices);
        return devices;
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/server")
    public ResponseEntity<String> getServerUrl() {
        //GET - http://localhost:8282/api/v1/biometrics/server
        String activeUrl = "http://localhost:"+ activePort;

        return ResponseEntity.ok(activeUrl);
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/angular/test")
    public ResponseEntity<Map<String, Object>> angularTest(@RequestParam String reader){

        NBiometricClient testClient = null;
        testClient = new NBiometricClient();
        testClient.setMatchingThreshold(150);
        testClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        testClient.setFingersReturnBinarizedImage(true);
        short s = 60;
        testClient.setFingersQualityThreshold((byte) s);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("message", "Hello, world!");
        responseData.put("status", HttpStatus.OK.value());

        try {
            reader = URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ignored) {
        }

        for (NDevice device : getDevices()) {
            if (device.getDisplayName().equals(reader)) {
                testClient.setFingerScanner((NFScanner) device);
            }
        }

        try (NSubject subject = new NSubject()) {
            final NFinger finger = new NFinger();
            finger.setPosition(NFPosition.UNKNOWN);
            subject.getFingers().add(finger);

            if (this.scannerIsNotSet(reader)) {
                responseData.put("ERROR", "Biometrics Scanner not found");
                responseData.put("status", HttpStatus.OK.value());
                return new ResponseEntity<>(responseData, HttpStatus.OK);
            }

            NBiometricStatus status = testClient.capture(subject);

            if (status.equals(NBiometricStatus.OK)) {
                status = client.createTemplate(subject);
                if (status.equals(NBiometricStatus.OK)) {
                    // Converting template to ISO format
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
        testClient.dispose();
        // Return ResponseEntity with the map and HTTP status OK
        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

    @GetMapping(NEUROTEC_URL_VERSION_ONE + "/hashed")
    public String testHash(){
        /*NBiometricClient biometricClient = null;

        try {
            biometricClient = new NBiometricClient();
            biometricClient.setMatchingThreshold(144);
            biometricClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);

            //GET - http://localhost:8282/api/v1/biometrics/hashed
            List<Biometric> biometrics = biometricRepository.getPatientBaselineFingerprints1("f53d4269-ffd3-4ab8-ad0b-ccfa9242b9f8");
            NBiometricClient finalBiometricClient = biometricClient;
            biometrics.forEach(b -> {
                byte[] template = b.getTemplate();
                String hashedTemplate = bcryptHash(template);

                // Convert to version three and back to version two
                byte [] createdTemplate = b.getTemplate();// convertANSIToISO(convertISOToANSI(template, b.getTemplateType()));
                String hash2 = bcryptHash(createdTemplate);

                FMRecord fmRecord = new FMRecord(new NBuffer(createdTemplate), BDIFStandard.ISO);
                FMRFingerView fmrFingerView = fmRecord.getFingerViews().get(0);
                NFRecord nfRecord = fmrFingerView.toNFRecord();

                // NFMinutia nfMinutia = new NFMinutia();
                // nfRecord.getMinutiae().add(nfMinutia);
                Random random = new Random();
                int minutiaeSize = nfRecord.getMinutiae().size();
                int index = random.nextInt(minutiaeSize);
                LOG.info("Index 1 {} ***** {}",index, b.getTemplateType());
                nfRecord.getMinutiae().remove(index);

                // Converting NFRecord to FMRecord
                FMRecord newR = new FMRecord(nfRecord, BDIFStandard.ISO, FMRecord.VERSION_ISO_20);
                byte[] storedFMRecord = newR.save().toByteArray();
                String hashStoredFMRecord = bcryptHash(storedFMRecord);

                int minutiaeSize1 = nfRecord.getMinutiae().size();
                int index1;
                do {
                    index1 = random.nextInt(minutiaeSize1);
                } while(index == index1);
                LOG.info("Index 2 {} ***** {}",index1, b.getTemplateType());

                nfRecord.getMinutiae().remove(index1);
                LOG.info("Minutiae new 2 size {} is {}", nfRecord.getMinutiae().size(), b.getTemplateType());
                FMRecord newR1 = new FMRecord(nfRecord, BDIFStandard.ISO, FMRecord.VERSION_ISO_20);
                byte[] storedFMRecord1 = newR1.save().toByteArray();
                String hashStoredFMRecord1 = bcryptHash(storedFMRecord1);

                byte [] createdPackedNFRecord = convertANSIToISO(convertISOToANSI(storedFMRecord, b.getTemplateType()));
                String hashedPackedNFRecord = bcryptHash(createdPackedNFRecord);

                NSubject nSubject = new NSubject();
                nSubject.setTemplateBuffer(new NBuffer(storedFMRecord1));
                nSubject.setId(b.getId());

                if(hashStoredFMRecord1.equals(hashedTemplate)){
                    LOG.info("Template {} ***** equal", b.getTemplateType());
                }else {
                    LOG.info("Template {} ***** not equal", b.getTemplateType());
                }
                List<NSubject> currentGallery = new ArrayList<>();

                NSubject gallery = new NSubject();
                String id = UUID.randomUUID().toString();

                gallery.setTemplateBuffer(new NBuffer(b.getTemplate()));
                gallery.setId(b.getId());
                currentGallery.add(gallery);

                NBiometricTask task = finalBiometricClient.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
                task.getSubjects().addAll(currentGallery);
                // LOG.info("Galley Size is **** {}", currentGallery.size());
                try {
                    finalBiometricClient.performTask(task);
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

                NBiometricStatus s = finalBiometricClient.identify(nSubject);

                if(s.equals(NBiometricStatus.OK)) {
                    LOG.info("Template {} ***** match", b.getTemplateType());
                } else {
                    LOG.info("Template {} ***** no match", b.getTemplateType());
                }
                // Identifying with existing fingerprints
            });

        }catch (Throwable th){
            th.printStackTrace();
            LOG.error("An error occurred *********** {}", th.getMessage());
        }
*/
        return "Hash test successfully...";
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
                result.setDeduplication(captureRequestDTO.getDeduplication());
                result.setType(CaptureResponse.Type.ERROR);
                return result;
            }

            NBiometricStatus status = client.capture(subject);

            if (status.equals(NBiometricStatus.OK)) {

                status = client.createTemplate(subject);

                if (status.equals(NBiometricStatus.OK)) {
                    // subject.getTemplateBuffer().toByteArray();
                    result.setDeviceName(reader);

                    // Converting template to ISO format
                    byte[] isoTemplate = subject.getTemplateBuffer(CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                            CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                            FMRecord.VERSION_ISO_20).toByteArray();

                    result.setTemplate(isoTemplate);
                    //check for quality
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
                                ClientIdentificationDTO clientIdentificationDTO = clientIdentification(subject);
                                result.setClientIdentificationDTO(clientIdentificationDTO);
                                result.setType(CaptureResponse.Type.SUCCESS);
                                return result;
                            default:
                                result.getMessage().put("ERROR", "Could not identify the supplied identification type " + identificationType);
                                result.setType(CaptureResponse.Type.ERROR);
                                return result;
                        }

                    }

                    //Checking if fingerprint is already captured in the current capturing process
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

                    //Running deduplication against baseline fingerprints
                    LOG.info("Recapture choice ******* {}", recapture);
                    if(recapture){
                        Deduplication recaptureDeduplication =
                                deduplicationForRecapturedPrints(subject, captureRequestDTO.getPatientId(),
                                        captureRequestDTO.getTemplateType(), captureRequestDTO.getDeduplication());
                        result.setDeduplication(recaptureDeduplication);
                    }

                    byte firstTwoChar = isoTemplate[0];
                    String template = Integer.toHexString(firstTwoChar) + "%";


                    CapturedBiometricDto capturedBiometricDTO = new CapturedBiometricDto();
                    capturedBiometricDTO.setTemplate(isoTemplate);
                    capturedBiometricDTO.setTemplateType(captureRequestDTO.getTemplateType());
                    capturedBiometricDTO.setHashed(bcryptHash(capturedBiometricDTO.getTemplate()));
                    capturedBiometricDTO.setImageQuality((int) imageQuality);
                    capturedBiometricDtosIn.add(capturedBiometricDTO);

                    result.setIso(true);

                    result.setCapturedBiometricsList(capturedBiometricDtosIn);
                    // imageQuality = subject.getFingers().get(0).getObjects().get(0).getQuality();
                    // NImage image = subject.getFingers().get(0).getImage();

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
        }
        client.clear();

        return result;
    }

    @SneakyThrows
    private ClientIdentificationDTO clientIdentification (NSubject subject) {

        NBiometricClient identifcationClient = null;
        identifcationClient = new NBiometricClient();
        identifcationClient.setMatchingThreshold(144);
        identifcationClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        identifcationClient.setFingersReturnBinarizedImage(true);

        ClientIdentificationDTO clientIdentificationDTO = new ClientIdentificationDTO();
        final List<NSubject> subjectsForIdentification = new ArrayList<>();

        List<Biometric> biometricList =  biometricRepository
                .getAllFingerPrintsByFacility()
                .parallelStream()
                .filter(fingerPrint -> fingerPrint.getRecapture() == 0)
                .collect(Collectors.toList());

        biometricList.parallelStream()
                .filter(fingerPrint -> fingerPrint.getTemplate() != null)
                .forEach(fingerPrint -> {
                    if (fingerPrint.getTemplate().length > 0){
                        NSubject nSubject = new NSubject();
                        byte [] template = fingerPrint.getTemplate();
                        template[25] = 0x00;
                        nSubject.setTemplateBuffer(new NBuffer(template));
                        nSubject.setId(fingerPrint.getId() + "#" +fingerPrint.getPersonUuid());
                        subjectsForIdentification.add(nSubject);
                    }
                });
        LOG.info("Biometric size is *********** {}", biometricList.size());
        
        NBiometricTask task1 = identifcationClient.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
        subjectsForIdentification
                .forEach(nSubject -> {
                    try {
                        task1.getSubjects().add(nSubject);
                    } catch (Exception e) {
                        task1.getSubjects().remove(nSubject);
                        LOG.error("Error adding subject ***** {}", e.getMessage());
                    }
                });
        try {
            identifcationClient.performTask(task1);
        } catch (Exception e){
            e.printStackTrace();
        }

        NBiometricStatus s = identifcationClient.identify(subject);
        if (s.equals(NBiometricStatus.OK)) {

            String [] id = subject.getMatchingResults().get(0).getId().split("#");

            String matchedId = id[1];

            String sql = "select id, uuid, first_name, sex, surname, other_name, hospital_number, date_of_birth \n" +
                    "from patient_person where uuid = ?";

            clientIdentificationDTO.setMessageType("SUCCESS_MATCH_FOUND");
            clientIdentificationDTO.setMessage("Client identified");
            clientIdentificationDTO.setPersonUuid(matchedId);

            IdentifiedClient identifiedClient = (IdentifiedClient) jdbcTemplate
                    .queryForObject(sql, new Object[] { matchedId }, new BeanPropertyRowMapper(IdentifiedClient.class));

            assert identifiedClient != null;
            clientIdentificationDTO.setId(identifiedClient.getId());
            clientIdentificationDTO.setPersonUuid(matchedId);
            clientIdentificationDTO.setSex(identifiedClient.getSex());
            clientIdentificationDTO.setSurname(identifiedClient.getSurname());
            clientIdentificationDTO.setFirstName(identifiedClient.getFirstName());
            clientIdentificationDTO.setOtherName(identifiedClient.getOtherName());
            clientIdentificationDTO.setHospitalNumber(identifiedClient.getHospitalNumber());


        }else {
            clientIdentificationDTO.setMessageType("SUCCESS_NO_MATCH_FOUND");
            clientIdentificationDTO.setMessage("Could not identify clients");
            return  clientIdentificationDTO;
        }

        identifcationClient.clear();

        return clientIdentificationDTO;
    }


    @SneakyThrows
    private Deduplication deduplicationForRecapturedPrints(
            NSubject nSubject, Long patientId, String recapturedTemplateType,
            Deduplication deduplication
    ) {
        NBiometricClient biometricClient = null;
        biometricClient = new NBiometricClient();
        biometricClient.setMatchingThreshold(144);
        biometricClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);
        biometricClient.setFingersReturnBinarizedImage(true);
        // biometricClient.setFingersQualityThreshold((byte) 75);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode parentNode = mapper.createObjectNode();

        List<Biometric> baselinePrints = biometricRepository.getPatientBaselineFingerprints(patientId);
        List<NSubject> baselineSubjects = new ArrayList<>();

        baselinePrints.parallelStream()
                .filter(fingerPrint -> fingerPrint.getTemplate() != null)
                .forEach(fingerPrint -> {
                    if (fingerPrint.getTemplate().length > 0){
                        NSubject subject = new NSubject();
                        byte [] template  = fingerPrint.getTemplate();
                        template[25] = 0x00;
                        subject.setTemplateBuffer(new NBuffer(template));
                        subject.setId(fingerPrint.getId() + "#" + fingerPrint.getPersonUuid());
                        baselineSubjects.add(subject);
                    }
                });


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
            e.printStackTrace();
        }

        NBiometricStatus status = biometricClient.identify(nSubject);
//        deduplication.setUnMatchCount(0);
//        deduplication.setMatchCount(0);

        if(status.equals(NBiometricStatus.OK)) {
            String [] baselineSubjectId = nSubject.getMatchingResults().get(0).getId().split("#");
            String baselineId = baselineSubjectId[0];
            String baselineTemplateType = baselinePrints
                    .stream()
                    .filter(f -> StringUtils.equals(f.getId(), baselineId))
                    .map(Biometric::getTemplateType)
                    .findFirst().orElse(null);
            deduplication.setMatchedCount(deduplication.getMatchedCount() + 1);

            assert baselineTemplateType != null;
            String key = "BASELINE_" + baselineTemplateType.toUpperCase().replaceAll(" ", "_");
            String value = "RECAPTURE_" + recapturedTemplateType.toUpperCase().replaceAll(" ", "_");
            details.put(key, value);
            deduplication.setDetails(details);

            if (StringUtils.equals(baselineTemplateType, recapturedTemplateType)){
                deduplication.setPerfectMatchCount(deduplication.getPerfectMatchCount() + 1);
            } else {
                deduplication.setImperfectMatchCount(deduplication.getImperfectMatchCount() + 1);
            }

        }else {
            deduplication.setUnmatchedCount(deduplication.getUnmatchedCount() + 1);
        }
        biometricClient.clear();

        return deduplication;
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

            final List<NSubject> subjects = new ArrayList<>();

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
                subject.setProperty("data", template);
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
                            subject.setProperty("data", fingerPrint);
                            subjects.add(subject);
                        }
                    });

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

            NBiometricClient finalDeduplication = deduplication;
            DeduplicationDetails deduplicationDetails = new DeduplicationDetails();
            List<MatchedFinger> matchedFingerList = new ArrayList<>();
            currentSubjects.parallelStream()
                    .forEach(subject -> {
                        NBiometricStatus s = finalDeduplication.identify(subject);
                        if(s.equals(NBiometricStatus.OK)){

                            Biometric subjectBiometric = subject.getProperty("data", Biometric.class);
                            MatchedFinger matchedFinger = new MatchedFinger();
                            matchedFinger.setFingerType(subjectBiometric.getTemplateType());
                            matchedFinger.setId(subjectBiometric.getId());

                            NSubject.MatchingResultCollection nMatchingResults = subject.getMatchingResults();
                            List<PersonMatched> personMatchedList = new ArrayList<>();

                            List<MatchedPair> matchedPairList = new ArrayList<>();

                            for (int j = 0; j < nMatchingResults.size(); j++) {
                                Biometric matched = subject.getMatchingResults().get(j).getProperty("data", Biometric.class);

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
                                // Building Match pair data
                            }
                            // Saving Matched Pair data
                            /// saveMatchPair(matchedPairList);

                            numberOfMatch.updateAndGet(v -> v + 1);
                        }
                    });
            deduplicationDetails.setMatchedFingers(matchedFingerList);
            deduplicationResponse.setMessageType("SUCCESS");
            deduplicationResponse.setMessage("Deduplication process successful");
            deduplicationResponse.setNumberOfMatchedFingers(numberOfMatch.get());
            deduplication.clear();

        }catch (Throwable th){
            th.printStackTrace();
            LOG.error("An error occurred *********** {}", th.getMessage());
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
        NBiometricClient biometricClient = null;
        biometricClient = new NBiometricClient();
        biometricClient.setMatchingThreshold(144);
        biometricClient.setFingersMatchingSpeed(NMatchingSpeed.LOW);

        Set<CapturedBiometricDto> templates = captureRequestDTO.getCapturedBiometricsList();
        List<NSubject> currentGallery = new ArrayList<>();
        for (CapturedBiometricDto template : templates) {
            //String id = captureRequestDTO.getId();
            NSubject gallery = new NSubject();

            String id = UUID.randomUUID().toString();
            byte [] template1  = template.getTemplate();
            template1[25] = 0x00;
            gallery.setTemplateBuffer(new NBuffer(template1));
            gallery.setId(id);
            currentGallery.add(gallery);

            NBiometricTask task = biometricClient.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
            task.getSubjects().addAll(currentGallery);
            // LOG.info("Galley Size is **** {}", currentGallery.size());
            try {
                biometricClient.performTask(task);
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
        NBiometricStatus s = biometricClient.identify(subject);
        biometricClient.clear();
        return s;
    }

    private boolean scannerIsNotSet(String reader) {
        LOG.info("Reader from REST **** {}", reader);
        for (NDevice device : getDevices()) {
            if (device.getDisplayName().equals(reader)) {
                client.setFingerScanner((NFScanner) device);
                return false;
            } else if (reader.equals("Futronic FS80H #1")){
                client.setFingerScanner((NFScanner) device);
                return false;
            }
        }
        return true;
    }

    private void initDeviceManager() {
        try {
            deviceManager = new NDeviceManager();
        } catch (Exception e) {
            LOG.error("Error ********* {}", e.getMessage());
        }

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
        client.setMatchingThreshold(96);
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

    public String bcryptHash(byte[] template) {
        String encoded = Base64.getEncoder().encodeToString(template);
        return BCrypt.hashpw(encoded, "$2a$12$MklNDNgs4Agd50cSasj91O");
    }


}
