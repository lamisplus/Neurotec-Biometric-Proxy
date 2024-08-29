package org.lamisplus.biometric.domain;

public interface ClientIdentificationProject {
    String getMessageType();
    String getMessage();
    String getPersonUuid();
    Long getId();
    String getHospitalNumber();
    String getSurname();
    String getOtherName();
    String getFirstName();
    String getSex();
}
