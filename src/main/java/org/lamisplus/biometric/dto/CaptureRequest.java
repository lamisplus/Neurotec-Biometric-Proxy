package org.lamisplus.biometric.dto;


import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class CaptureRequest {
    private String biometricType;
    private Long patientId;
    private String templateType;
    private Set<CapturedBiometric> captureBiometrics = new HashSet<>();
}
