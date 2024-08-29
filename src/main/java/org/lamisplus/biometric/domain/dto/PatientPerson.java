package org.lamisplus.biometric.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatientPerson {
    String patientId;
    String patientUuid;
    String surname;
    String gender;
    String dateOfBirth;
    String firstName;
    public String address;
    String hospitalNumber;
}
