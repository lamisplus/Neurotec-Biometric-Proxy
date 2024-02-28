package org.lamisplus.biometric.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

@Data
public class BiometricEnrollmentDto implements Serializable {
    @NotNull(message = "patientId is mandatory")
    private Long patientId;
    private HashMap<String, String> message;
    private byte[] template;
    private List<CapturedBiometricDto> capturedBiometricsList;
    @NotBlank(message = "templateType is mandatory")
    private String templateType;
    private String deviceName;
    private String deviceType;
    @NotBlank(message = "biometricType is mandatory")
    private String biometricType;
    public enum Type {ERROR, SUCCESS, WARNING}
    private Type type=null;
    private boolean iso;
    private int imageHeight;
    private int imageWeight;
    private int imageResolution;
    private int matchingScore;
    private Integer mainImageQuality=0;
    private byte[] image;
    private String reason;
    private int age;
    private Integer recapture;
    private LocalDate replaceDate;
    private String recaptureMessage;
    private String hashed;
    private boolean match;
    private Deduplication deduplication;
    private LocalDate enrollmentDate;
    private ClientIdentificationDTO clientIdentificationDTO;
}
