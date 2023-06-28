package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchedPair {
    public String enrolledPatientId;
    public String duplicatePatientId;
    public String enrolledPatientFingerType;
    public String duplicatePatientFingerType;
    public Integer score;
}
