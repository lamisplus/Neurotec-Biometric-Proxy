package org.lamisplus.biometric.controller;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lamisplus.biometric.domain.dto.*;
import org.lamisplus.biometric.service.SecugenManager;
import org.lamisplus.biometric.service.SecugenService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SecugenController {
    private final SecugenService secugenService;
    //Versioning through URI Path
    private final String SECUGEN_URL_VERSION_ONE = "/api/v1/biometrics/secugen";
    private final String BIOMETRICS_URL_VERSION_ONE = "/api/v1/biometrics";
    private final SecugenManager secugenManager;

    @PostMapping(SECUGEN_URL_VERSION_ONE + "/store-list/{personId}")
    public ResponseEntity<Boolean> clearStoreList(@PathVariable Long personId) {
        return ResponseEntity.ok (secugenService.emptyStoreByPersonId(personId));
    }

    @GetMapping(SECUGEN_URL_VERSION_ONE + "/server")
    public String getServerUrl() {
        return secugenManager.getSecugenProperties().getServerUrl();
    }

    @GetMapping(SECUGEN_URL_VERSION_ONE + "/reader")
    public ResponseEntity<List<DeviceDTO>> getReaders() {
        List<DeviceDTO> devices = secugenManager.getDevices();
        devices =  devices.stream()
                .filter(d-> Integer.valueOf(d.getId())==255)
                .collect(Collectors.toList());
        return ResponseEntity.ok(devices);
    }

    @PostMapping(SECUGEN_URL_VERSION_ONE + "/enrollment")
    public BiometricEnrollmentDto enrollment(@RequestParam String reader,
                                             @RequestParam(required = false, defaultValue = "false") Boolean isNew,
                                             @RequestParam(required = false, defaultValue = "false") Boolean recapture,
                                             @RequestParam(required = false, defaultValue = "false") Boolean identify,
                                             @Valid @RequestBody CaptureRequestDTO captureRequestDTO) {
        return secugenService.enrollment(reader, identify, isNew, recapture, captureRequestDTO);
    }

    @PostMapping(SECUGEN_URL_VERSION_ONE + "/identify")
    public ClientIdentificationDTO enrollment(@RequestParam String reader) {
        return secugenService.identify(reader);
    }

    @PostMapping(SECUGEN_URL_VERSION_ONE + "/enrollment2")
    public BiometricEnrollmentDto enrollment2(@RequestParam String reader,
                                              @Valid @RequestBody CaptureRequestDTO captureRequestDTO) {
        BiometricEnrollmentDto biometric = secugenService.getBiometricEnrollmentDto(captureRequestDTO);
        biometric.setMessage(new HashMap<String, String>());
        if(!reader.equals("SG_DEV_AUTO")) {
            biometric.getMessage().put("ERROR", "READER NOT AVAILABLE");
            biometric.setType(BiometricEnrollmentDto.Type.ERROR);
            return biometric;
        }
        if(!biometric.getBiometricType().equals("FINGERPRINT")) {
            biometric.getMessage().put("ERROR", "TemplateType not FINGERPRINT");
            biometric.setType(BiometricEnrollmentDto.Type.ERROR);
            return biometric;
        }

        try(InputStream in=new ClassPathResource("biometrics_payload.txt").getInputStream()){
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            BiometricEnrollmentDto biometric1 = mapper.readValue(in, BiometricEnrollmentDto.class);
            return biometric1;
        }
        catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    @GetMapping(SECUGEN_URL_VERSION_ONE + "/boot")
    public ErrorCodeDTO boot(@RequestParam String reader) {
        return secugenService.boot(reader);
    }
}
