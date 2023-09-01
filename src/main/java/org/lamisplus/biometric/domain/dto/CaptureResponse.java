package org.lamisplus.biometric.domain.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Data
public class CaptureResponse {
    private Long patientId;
    private HashMap<String, String> message = new HashMap<>();
    private byte[] template;
    private Set<CapturedBiometricDto> capturedBiometricsList = new HashSet<>();
    @NotBlank(message = "templateType is mandatory")
    private String templateType;
    private String deviceName;
    private String deviceType;
    @NotBlank(message = "biometricType is mandatory")
    private String biometricType;
    public enum Type {ERROR, SUCCESS, WARNING}
    private Type type;
    private boolean iso;
    private int imageHeight;
    private int imageWeight;
    private int imageResolution;
    private int matchingScore;
    private long mainImageQuality = 0;
    private byte[] image;
    private String reason;
    private int age;
    private Integer recapture;
    private String recaptureMessage;
    private String hashed;
    private boolean match;
    private Deduplication deduplication;
    private ClientIdentificationDTO clientIdentificationDTO;

}
