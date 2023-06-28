package org.lamisplus.biometric.domain.dto;

import lombok.Data;

@Data
public class ClientIdentificationDTO {
    public String messageType;
    public String message;
    public String patientUUID;
    public String patientId;
}
