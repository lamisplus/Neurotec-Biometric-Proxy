package org.lamisplus.biometric.domain.dto;

import lombok.Data;

@Data
public class ClientIdentificationDTO {
    public String messageType;
    public String message;
    public String personUuid;
    public Long id;
    public String hospitalNumber;
    public String surname;
    public String otherName;
    public String firstName;
    public String sex;
}
