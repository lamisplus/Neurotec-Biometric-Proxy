package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Fingerprint {
    private String id;
    byte[] template;
    private String patientId;
    private String templateType;
}
