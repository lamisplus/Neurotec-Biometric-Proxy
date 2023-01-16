package org.lamisplus.biometric.dto;


import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class CaptureRequestDTO {
    private String biometricType;
    private Long patientId;
    private String templateType;
    private Set<CapturedBiometricDto> captureBiometrics = new HashSet<>();
}
