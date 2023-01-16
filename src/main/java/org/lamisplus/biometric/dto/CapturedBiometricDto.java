package org.lamisplus.biometric.dto;

import lombok.Data;

@Data
public class CapturedBiometricDto {
    private byte[] template;
    private String templateType;
}
