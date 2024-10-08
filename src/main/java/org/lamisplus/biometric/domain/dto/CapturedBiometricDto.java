package org.lamisplus.biometric.domain.dto;
import lombok.Data;


@Data
public class CapturedBiometricDto {
    private String id;
    private byte[] template;
    private String templateType;
    private String hashed;
    private Integer imageQuality;
    private String matchType;
    private String matchBiometricId;
    private String matchPersonUuid;
}
