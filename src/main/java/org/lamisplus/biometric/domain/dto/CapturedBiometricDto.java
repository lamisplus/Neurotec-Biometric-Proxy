package org.lamisplus.biometric.domain.dto;

import lombok.Data;

@Data
public class CapturedBiometricDto {
    private byte[] template;
    private String templateType;
    private String hashed;
    private Integer imageQuality;
}
