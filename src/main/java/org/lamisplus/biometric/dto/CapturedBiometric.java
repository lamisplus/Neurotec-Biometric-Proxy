package org.lamisplus.biometric.dto;

import lombok.Data;

@Data
public class CapturedBiometric {
    private byte[] template;
    private String templateType;
}
