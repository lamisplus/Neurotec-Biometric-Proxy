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
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.domain.dto.*;
import org.lamisplus.biometric.repository.BiometricRepository;
import org.lamisplus.biometric.util.LibraryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BiometricController {
    private NDeviceManager deviceManager;
    private NBiometricClient client;
    private final Set<CapturedBiometricDto> capturedBiometricDtos = new HashSet<>();
    private final String BIOMETRICS_URL_VERSION_ONE = "/api/v1/biometrics";
    private final String NEUROTEC_URL_VERSION_ONE = "/api/v1/biometrics/neurotec";
    private final BiometricRepository biometricRepository;
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

    @PostMapping(BIOMETRICS_URL_VERSION_ONE + "/enrollment")
    public CaptureResponse enrollment(@RequestParam String reader, @RequestParam(required = false, defaultValue = "false") Boolean isNew,
                                      @Valid @RequestBody CaptureRequestDTO captureRequestDTO) {

        //initializing response
        CaptureResponse result = getBiometricEnrollmentDto(captureRequestDTO);
        //checking if new
        if(isNew){
            this.emptyStoreByPersonId(captureRequestDTO.getPatientId());
        }
        try {
            reader = URLDecoder.decode(reader, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ignored) {
        }

        final NSubject subject = new NSubject();
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
                Set<CapturedBiometricDto> templates1 = captureRequestDTO.getCaptureBiometrics();
                List<NSubject> currentGallery = new ArrayList<>();
                for (CapturedBiometricDto template : templates1) {
                    NSubject gallery = new NSubject();
                    gallery.setTemplateBuffer(new NBuffer(template.getTemplate()));
                    gallery.setId(captureRequestDTO.getPatientId().toString());
                    currentGallery.add(gallery);
                    NBiometricTask task = client.createTask(EnumSet.of(NBiometricOperation.ENROLL), null);
                    task.getSubjects().addAll(currentGallery);
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
                status = client.identify(subject);
                //check for quality
                long imageQuality = subject.getFingers().get(0).getObjects().get(0).getQuality();
                if(imageQuality < quality){
                    result.getMessage().put("ERROR", "Image quality is low - " + imageQuality);
                    result.setType(CaptureResponse.Type.ERROR);
                    return result;
                }

                result.setDeviceName(reader);
                if (status.equals(NBiometricStatus.OK)) {
                    if (isNew) {
                        result.getMessage().put("ERROR", "Fingerprint already captured");
                        result.setType(CaptureResponse.Type.ERROR);
                        return result;
                    }

                } else {
                    byte[] isoTemplate = subject.getTemplateBuffer(CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                        CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                        FMRecord.VERSION_ISO_CURRENT).toByteArray();

                    FMRecord test = new FMRecord(new NBuffer(isoTemplate), BDIFStandard.ISO);
                    if (test.getVersion().getMajor() != 2) {
                        NFTemplate nfTemplate = test.toNFTemplate();
                        FMRecord fmRecord = new FMRecord(nfTemplate, BDIFStandard.ISO, FMRecord.VERSION_ISO_20);
                        isoTemplate = fmRecord.save(BDIFEncodingType.TRADITIONAL).toByteArray();
                    }

                    byte firstTwoChar = isoTemplate[0];
                    //String template = "46% OR AC%";
                    String template = Integer.toHexString(firstTwoChar)+"%";

                    System.out.println("********************************************************");
                    System.out.println("firstTwoChar inside: "+firstTwoChar);
                    System.out.println("You convert?: "+template);
                    System.out.println("********************************************************");

                    Set<StoredBiometric> biometricsInFacility = biometricRepository
                            .findByFacilityIdWithTemplate(captureRequestDTO.getFacilityId(), template);

                    CapturedBiometricDto capturedBiometricDTO = new CapturedBiometricDto();
                    capturedBiometricDTO.setTemplate(isoTemplate);
                    capturedBiometricDTO.setTemplateType(captureRequestDTO.getTemplateType());
                    capturedBiometricDtos.add(capturedBiometricDTO);

                    result.setTemplate(isoTemplate);
                    result.setIso(true);

                    result.setCapturedBiometricsList(capturedBiometricDtos);
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
        client.clear();

        return result;
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
