package org.lamisplus.biometric.domain.dto;
import lombok.Data;


@Data
public class CapturedBiometricDto {
    private String id;
    private byte[] template;
    private String templateType;
}
