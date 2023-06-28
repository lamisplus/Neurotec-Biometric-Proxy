package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchFingerInformation {
    private String enrolledFingerId;
    private String enrolledFingerType;
    private String enrolledFInger;

    private String duplicateFingerId;
    private String duplicateFingerType;
    private String duplicateFingerName;
    private String duplicateFIngerHospitalNumber;
}
