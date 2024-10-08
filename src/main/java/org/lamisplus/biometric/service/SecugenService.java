package org.lamisplus.biometric.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.domain.ClientIdentificationProject;
import org.lamisplus.biometric.domain.dto.*;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.lamisplus.biometric.domain.enumeration.ErrorCode;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecugenService {
    public static final int PAGE_SIZE = 2000;
    public static final String ERROR_MESSAGE = "ERROR";
    public static final String MATCH = "match";
    public static final String RECAPTURE_MESSAGE = "RECAPTURE_MESSAGE";
    private static List<StoredBiometric> biometricsInFacility = new ArrayList<>();
    public static final int RECAPTURE = 0;
    public static Integer totalPage=0;
    public static final String WARNING = "WARNING";
    public static final String RECAPTURE_NO_BASELINE = "No baseline biometrics for recapturing\nFingerprint exist but not same patient";
    public static final String RECAPTURE_NO_MATCH_MESSAGE = "No baseline biometrics for recaptured finger";
    public static final String FINGERPRINT_ALREADY_CAPTURED = "Fingerprint already captured";
    public static final int IMAGE_QUALITY = 61;
    private final SecugenManager secugenManager;
    private final BiometricRepository biometricRepository;
    public static String MATCHED_PERSON_UUID;
    private static String TEMPLATE_TYPE;
    private final String LEFT_MIDDLE_FINGER = "Left Middle Finger";
    private final String LEFT_INDEX_FINGER = "Left Index Finger";
    private final String LEFT_RING_FINGER = "Left Ring Finger";
    private final String LEFT_THUMB = "Left Thumb";
    private final String LEFT_LITTLE_FINGER =  "Left Little Finger";
    private final String RIGHT_MIDDLE_FINGER = "Right Middle Finger";
    private final String RIGHT_INDEX_FINGER = "Right Index Finger";
    private final String RIGHT_THUMB = "Right Thumb";
    private final String RIGHT_RING_FINGER =  "Right Ring Finger";
    private final String RIGHT_LITTLE_FINGER =  "Right Little Finger";


    /**
     * Biometric enrollment
     * @param reader
     * @param isNew
     * @param recapture
     * @param captureRequestDTO
     * @return BiometricEnrollmentDto
     */
    public BiometricEnrollmentDto enrollment(String reader, Boolean identify, Boolean isNew, Boolean recapture, CaptureRequestDTO captureRequestDTO){
        if(isNew){
            //clear store
            this.emptyStoreByPersonId(captureRequestDTO.getPatientId());
            if(!biometricsInFacility.isEmpty()) biometricsInFacility.clear();
        }

        BiometricEnrollmentDto biometric = getBiometricEnrollmentDto(captureRequestDTO);

        if(biometric.getMessage() == null)biometric.setMessage(new HashMap<>());
            // checks if the secugen device is active
        if (this.scannerIsNotSet(reader)) {
            biometric.getMessage().put(ERROR_MESSAGE, "READER NOT AVAILABLE");
            biometric.setType(BiometricEnrollmentDto.Type.ERROR);
            return biometric;
        }

        biometric.setDeviceName(reader);
        biometric.getMessage().put("STARTED CAPTURING", "PROCEEDING...");

        Long error = secugenManager.boot(secugenManager.getDeviceId(reader));
        if (error > 0L) {
            ErrorCode errorCode = ErrorCode.getErrorCode(error);
            biometric.getMessage().put(ERROR_MESSAGE, errorCode.getErrorName() + ": " + errorCode.getErrorMessage());
            return biometric;
        }

        try {
            biometric = secugenManager.captureFingerPrint(biometric);
            byte firstTwoChar = biometric.getTemplate()[0];
            //String template = "46% OR AC%";
            String template = Integer.toHexString(firstTwoChar)+"%";

            if(!identify) {
                if(captureRequestDTO.getCapturedBiometricsList() != null && !captureRequestDTO.getCapturedBiometricsList().isEmpty()) {
                    captureRequestDTO.getCapturedBiometricsList().forEach(capturedBiometricDto -> {
                        if(captureRequestDTO.getPatientId() != null) {
                            BiometricStoreDTO.addCapturedBiometrics(captureRequestDTO.getPatientId(), capturedBiometricDto);
                        }
                    });
                }
            } else if(identify){
                biometric.setClientIdentificationDTO(identify(reader));
                return biometric;
            }

            AtomicReference<Boolean> matched = new AtomicReference<>(false);
            if (biometric.getTemplate().length > 200 && biometric.getMainImageQuality() >= IMAGE_QUALITY ) {
                if(biometricsInFacility.isEmpty()) {
                    biometricsInFacility = biometricRepository.findByFacilityIdWithTemplate(template);
                }

                //recapture
                if(recapture) {
                    Optional<String> optionalPersonUuid= biometricRepository.getPersonUuid(captureRequestDTO.getPatientId());
                    BiometricEnrollmentDto recaptureEnrollment = recaptureOrIdentify(true, optionalPersonUuid, template, biometric);
                    LOG.info("Type is {}", recaptureEnrollment.getType());
                }else {
                    if(getMatch(biometricsInFacility, biometric.getTemplate())){
                        this.addMessage(ERROR_MESSAGE, biometric, FINGERPRINT_ALREADY_CAPTURED);
                        biometric.setType(BiometricEnrollmentDto.Type.ERROR);
                        LOG.info(FINGERPRINT_ALREADY_CAPTURED);
                        return biometric;
                    }
                }

                byte[] scannedTemplate = biometric.getTemplate();
                if(biometric.getTemplate() != null && !BiometricStoreDTO.getPatientBiometricStore().isEmpty()) {
                    final List<CapturedBiometricDto> capturedBiometricsListDTO = BiometricStoreDTO
                            .getPatientBiometricStore()
                            .values()
                            .stream()
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList());

                    if(!recapture) {
                        for (CapturedBiometricDto capturedBiometricsDTO : capturedBiometricsListDTO) {
                            matched.set(secugenManager.matchTemplate(capturedBiometricsDTO.getTemplate(), biometric.getTemplate()));
                            if (matched.get()) {
                                return this.addMessage(ERROR_MESSAGE, biometric, "Fingerprint already captured");
                            }
                        }
                    }
                } else {
                    biometric.setCapturedBiometricsList(new ArrayList<>());
                }
                biometric.getMessage().put("CAPTURING", "PROCEEDING...");
                if(biometric.getType() == null) {
                    biometric.setType(BiometricEnrollmentDto.Type.SUCCESS);
                }
                CapturedBiometricDto capturedBiometrics = new CapturedBiometricDto();
                capturedBiometrics.setTemplate(scannedTemplate);
                capturedBiometrics.setTemplateType(biometric.getTemplateType());
                capturedBiometrics.setHashed(bcryptHash(biometric.getTemplate()));
                capturedBiometrics.setImageQuality(biometric.getMainImageQuality());

                List<CapturedBiometricDto> capturedBiometricsList =
                        BiometricStoreDTO.addCapturedBiometrics(biometric.getPatientId(), capturedBiometrics)
                                .get(biometric.getPatientId());

                biometric.setCapturedBiometricsList(capturedBiometricsList);
                biometric.setTemplate(scannedTemplate);
            }else {
                return this.addMessage(ERROR_MESSAGE, biometric, null);
            }

        } catch (Exception exception) {
            exception.printStackTrace();
            biometric.setType(BiometricEnrollmentDto.Type.ERROR);
            return this.addMessage(ERROR_MESSAGE, biometric, exception.getMessage());
        }
        return biometric;
    }


    /**
     * Checking if scanner is set
     * @param reader
     * @return boolean
     */
    private boolean scannerIsNotSet(String reader) {
        Long readerId = secugenManager.getDeviceId(reader);
        for (DeviceDTO deviceDTO : secugenManager.getDevices()) {
            if (deviceDTO.getId().equals(String.valueOf(readerId))) {
                secugenManager.boot(readerId);
                return false;
            }
        }
        return true;
    }

    /**
     * Booting secugen scanner
     * @param reader
     * @return ErrorCodeDTO
     */
    public ErrorCodeDTO boot(String reader) {
        ErrorCode errorCode = ErrorCode.getErrorCode(secugenManager.boot(secugenManager.getDeviceId(reader)));
        return ErrorCodeDTO.builder()
                .errorID(errorCode.getErrorID())
                .errorName(errorCode.getErrorName())
                .errorMessage(errorCode.getErrorMessage())
                .errorType(errorCode.getType())
                .build();
    }

    /**
     * Setting BiometricEnrollmentDto from CaptureRequestDTO
     * @param captureRequestDTO
     * @return BiometricEnrollmentDto
     */
    public BiometricEnrollmentDto getBiometricEnrollmentDto(CaptureRequestDTO captureRequestDTO){
        BiometricEnrollmentDto biometricEnrollmentDto = new BiometricEnrollmentDto();
        biometricEnrollmentDto.setBiometricType(captureRequestDTO.getBiometricType());
        biometricEnrollmentDto.setTemplateType(captureRequestDTO.getTemplateType());
        biometricEnrollmentDto.setPatientId(captureRequestDTO.getPatientId());
        return biometricEnrollmentDto;
    }

    /**
     * Creating a custom error message
     * @param messageKey
     * @param biometricEnrollmentDto
     * @Param customMessage
     * @return BiometricEnrollmentDto
     */
    private BiometricEnrollmentDto addMessage(String messageKey,BiometricEnrollmentDto biometricEnrollmentDto, String customMessage){
        int imageQuality = biometricEnrollmentDto.getMainImageQuality();
        int templateLength = biometricEnrollmentDto.getTemplate().length;
        biometricEnrollmentDto.getMessage().put(messageKey, "ERROR WHILE CAPTURING... " +
                "\nImage Quality: " + (imageQuality < 61 ? "Bad - " + imageQuality : "Good - " + imageQuality) +
                "\nTemplate Length: " + (templateLength < 200 ? "Bad - " + templateLength : "Good - " + templateLength) +
                "\n" + (customMessage != null ? customMessage : "")
        );
        biometricEnrollmentDto.setType(BiometricEnrollmentDto.Type.ERROR);
        return biometricEnrollmentDto;
    }

    /**
     * emptying biometric store based on PersonId
     * @param personId
     * @return Boolean
     */
    public Boolean emptyStoreByPersonId(Long personId){
        Boolean hasCleared = false;
        if(!BiometricStoreDTO.getPatientBiometricStore().isEmpty() && BiometricStoreDTO.getPatientBiometricStore().get(personId) != null){
            BiometricStoreDTO.getPatientBiometricStore().remove(personId);
            hasCleared = true;
        }
        return hasCleared;
    }

    /**
     * check for biometric match
     * @param storedBiometrics
     * @param scannedTemplate
     * @return Boolean
     */
    public Boolean getMatch(List<StoredBiometric> storedBiometrics, byte[] scannedTemplate) {
        Boolean matched = Boolean.FALSE;
        MATCHED_PERSON_UUID = null;
        for (StoredBiometric biometric : storedBiometrics) {
            if(null != biometric.getPersonUuid()) {
                MATCHED_PERSON_UUID = biometric.getPersonUuid();
                LOG.info("MATCHED_PERSON_UUID {}", MATCHED_PERSON_UUID);
            }
            if (biometric.getLeftMiddleFinger() != null && biometric.getLeftMiddleFinger().length != 0) {
                if(matched)break;
                matched = secugenManager.matchTemplate(biometric.getLeftMiddleFinger(), scannedTemplate);
                TEMPLATE_TYPE = LEFT_MIDDLE_FINGER;
            }
            if (biometric.getLeftIndexFinger() != null && biometric.getLeftIndexFinger().length != 0) {
                if(matched)break;
                matched = secugenManager.matchTemplate(biometric.getLeftIndexFinger(), scannedTemplate);
                TEMPLATE_TYPE = LEFT_INDEX_FINGER;
            }
            if (biometric.getLeftMiddleFinger() != null && biometric.getLeftMiddleFinger().length != 0) {
                if(matched)break;
                matched = secugenManager.matchTemplate(biometric.getLeftMiddleFinger(), scannedTemplate);
                TEMPLATE_TYPE = LEFT_MIDDLE_FINGER;
            }
            if (biometric.getLeftThumb() != null && biometric.getLeftThumb().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getLeftThumb(), scannedTemplate);
                TEMPLATE_TYPE = LEFT_THUMB;
            }
            if (biometric.getLeftLittleFinger() != null && biometric.getLeftLittleFinger().length != 0) {
                if(matched)break;
                matched = secugenManager.matchTemplate(biometric.getLeftLittleFinger(), scannedTemplate);
                TEMPLATE_TYPE = LEFT_LITTLE_FINGER;
            }
            if (biometric.getLeftRingFinger() != null && biometric.getLeftRingFinger().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getLeftRingFinger(), scannedTemplate);
                TEMPLATE_TYPE = LEFT_RING_FINGER;
            }
            if (biometric.getRightIndexFinger() != null && biometric.getRightIndexFinger().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getRightIndexFinger(), scannedTemplate);
                TEMPLATE_TYPE = RIGHT_INDEX_FINGER;
            }
            if (biometric.getRightMiddleFinger() != null && biometric.getRightMiddleFinger().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getRightMiddleFinger(), scannedTemplate);
                TEMPLATE_TYPE = RIGHT_MIDDLE_FINGER;
            }
            if (biometric.getRightThumb() != null && biometric.getRightThumb().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getRightThumb(), scannedTemplate);
                TEMPLATE_TYPE = RIGHT_THUMB;
            }
            if (biometric.getRightRingFinger() != null && biometric.getRightRingFinger().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getRightRingFinger(), scannedTemplate);
                TEMPLATE_TYPE = RIGHT_RING_FINGER;
            }
            if (biometric.getRightLittleFinger() != null && biometric.getRightLittleFinger().length != 0) {
                if(matched)break;
                matched =  secugenManager.matchTemplate(biometric.getRightLittleFinger(), scannedTemplate);
                TEMPLATE_TYPE = RIGHT_LITTLE_FINGER;
            }
        }
        return matched;
    }

    private Boolean setMatch(byte[] capturedFinger, byte[] dbPrint, String personUuid){
        MATCHED_PERSON_UUID = personUuid;
        return secugenManager.matchTemplate(capturedFinger, dbPrint);
    }

    /**
     * Get person biometric by person uuid and recapture.
     * @param template
     * @return a hashed value of the base 64 template
     */
    public String bcryptHash(byte[] template) {
        String encoded = Base64.getEncoder().encodeToString(template);
        return BCrypt.hashpw(encoded, "$2a$12$MklNDNgs4Agd50cSasj91O");
    }

    /**
     * Get Client Identification
     * @param reader
     * @return ClientIdentificationDTO
     */
    public ClientIdentificationDTO identify(String reader){
        //clear if not empty
        if(!biometricsInFacility.isEmpty())biometricsInFacility.clear();
        if (this.scannerIsNotSet(reader)) {
            throw new EntityNotFoundException("Scanner does not exist");
        }
        LOG.info("level 1 ...");
        BiometricEnrollmentDto biometric = secugenManager.captureFingerPrint(new BiometricEnrollmentDto());
        byte firstTwoChar = biometric.getTemplate()[0];
        //String template = "46% OR AC%";
        String template = Integer.toHexString(firstTwoChar)+"%";
        LOG.info("level 2 ...");
        biometricsInFacility = biometricRepository
                    .findByFacilityIdWithTemplate(template);
        if(getMatch(biometricsInFacility, biometric.getTemplate())){
            if (MATCHED_PERSON_UUID != null) {
                Optional<ClientIdentificationProject> clientId = biometricRepository.getBiometricPersonData(MATCHED_PERSON_UUID);
                if (clientId.isPresent()) {
                    ClientIdentificationProject clientIdentificationProject = clientId.get();
                    ClientIdentificationDTO clientIdentification = setClientDetails(clientIdentificationProject);
                    clientIdentification.setMessageType("SUCCESS_MATCH_FOUND");
                    clientIdentification.setMessage("Client identified");
                    return clientIdentification;
                }
            }
        }
        ClientIdentificationDTO clientIdentificationDTO = new ClientIdentificationDTO();
        clientIdentificationDTO.setMessageType("SUCCESS_NO_MATCH_FOUND");
        clientIdentificationDTO.setMessage("Could not identify clients");
        return clientIdentificationDTO;
    }

    private ClientIdentificationDTO setClientDetails(ClientIdentificationProject clientIdentificationProject) {
        ClientIdentificationDTO clientIdentificationDTO = new ClientIdentificationDTO();
        clientIdentificationDTO.setFirstName(clientIdentificationProject.getFirstName());
        clientIdentificationDTO.setSex(clientIdentificationProject.getSex());
        clientIdentificationDTO.setId(clientIdentificationProject.getId());
        clientIdentificationDTO.setSurname(clientIdentificationDTO.getSurname());
        clientIdentificationDTO.setHospitalNumber(clientIdentificationDTO.getHospitalNumber());

        return clientIdentificationDTO;
    }


    /**
         * Recapture or identify Person.
         * @param recapture
         * @param optionalPersonUuid
         * @param biometricEnrollmentDto
         * @return BiometricEnrollmentDto
         */
    private BiometricEnrollmentDto recaptureOrIdentify(Boolean recapture,
                                        Optional<String> optionalPersonUuid,
                                        String template,
                                        BiometricEnrollmentDto biometricEnrollmentDto){
        if(recapture) {
            HashMap<String, String> mapDetails = new HashMap<>();
            String personUuid = optionalPersonUuid.get();
            List<StoredBiometric> personBiometrics = biometricRepository.findByFacilityIdWithTemplateAndPersonUuid(personUuid, RECAPTURE, template);
            if (!personBiometrics.isEmpty()) {
                if (getMatch(personBiometrics, biometricEnrollmentDto.getTemplate())) {
                    biometricEnrollmentDto.setMatch(true);
                    if (TEMPLATE_TYPE.equalsIgnoreCase(biometricEnrollmentDto.getTemplateType())) {
                        LOG.info("Perfect match...");
                        biometricEnrollmentDto.getMessage().put(MATCH, "Perfect...");
                        biometricEnrollmentDto.setType(BiometricEnrollmentDto.Type.SUCCESS);
                        biometricEnrollmentDto.getMessage().put(RECAPTURE_MESSAGE, "SUCCESSFULLY RECAPTURED, PERFECT MATCH");
                        return biometricEnrollmentDto;
    
                    } else {
                        LOG.info("Imperfect match...");
                        biometricEnrollmentDto.getMessage().put(RECAPTURE_MESSAGE, "SUCCESSFULLY RECAPTURED, IMPERFECT MATCH");
                        biometricEnrollmentDto.setType(BiometricEnrollmentDto.Type.WARNING);
                        biometricEnrollmentDto.getMessage().put(MATCH, "Imperfect...");
                        String key = "BASELINE_" + biometricEnrollmentDto.getTemplateType().toUpperCase().replaceAll(" ", "_");
                        String value = "RECAPTURE_" + TEMPLATE_TYPE.toUpperCase().replaceAll(" ", "_");
                        mapDetails.put(key, value);
                        Deduplication deduplication = new Deduplication();
                        if(biometricEnrollmentDto.getDeduplication() != null) {
                            deduplication = biometricEnrollmentDto.getDeduplication();
                        }

                        if(deduplication.getMapDetails()!= null){
                            HashMap<String, String> addedDetails = deduplication.getMapDetails();
                            addedDetails.put(key, value);
                            deduplication.setDetails(addedDetails);
                            //deduplication.getImperfectMatchCount();
                        }else {
                            deduplication.setDeduplicationDate(LocalDate.now());
                        }
                        return biometricEnrollmentDto;
                    }
                }
            }
            LOG.info("no match...");
            biometricEnrollmentDto.setType(BiometricEnrollmentDto.Type.WARNING);
            biometricEnrollmentDto.getMessage().put(MATCH, "Biometric not found...");
            biometricEnrollmentDto.getMessage().put(RECAPTURE_MESSAGE, "NO MATCH...");
        }
        return biometricEnrollmentDto;
    }
}