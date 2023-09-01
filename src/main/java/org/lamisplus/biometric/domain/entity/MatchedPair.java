package org.lamisplus.biometric.domain.entity;

import javax.persistence.Entity;

public class MatchedPair {
    public String enrolledPatientId;
    public String duplicatePatientId;
    public String enrolledPatientFingerType;
    public String duplicatePatientFingerType;
    public Integer score;
}
