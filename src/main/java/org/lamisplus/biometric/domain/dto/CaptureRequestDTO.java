package org.lamisplus.biometric.domain.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class CaptureRequestDTO {
    private String id;
    private Long patientId;

    //@NotBlank(message = "templateType is mandatory")
    private String templateType;

    //@NotBlank(message = "biometricType is mandatory")
    private String biometricType;

    private Long facilityId=0L;

    private Set<CapturedBiometricDto> capturedBiometricsList = new HashSet<>();
    private String reason;

    private Deduplication deduplication;
}
