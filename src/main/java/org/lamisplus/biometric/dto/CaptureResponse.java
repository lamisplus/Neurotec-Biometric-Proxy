package org.lamisplus.biometric.dto;


import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Data
public class CaptureResponse {
    private String biometricType;
    private Set<CapturedBiometric> capturedBiometricsList = new HashSet<>();
    private String deviceName;
    private String image;
    private long imageHeight;
    private long imageQuality;
    private long imageResolution;
    private long imageWeight;
    private boolean iso;
    private long matchingScore;
    private HashMap<String, String> message;
    private long patientID;
    private byte[] template;
    private String templateType;
    private String type;
}
