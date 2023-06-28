package org.lamisplus.biometric.domain.dto;

import lombok.Data;

import java.util.Set;

@Data
public class DeduplicationDTO {
    Set<CapturedBiometricDto> capturedPrints;
    Long facilityId;
}
