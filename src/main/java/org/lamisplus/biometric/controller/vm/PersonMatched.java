package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PersonMatched {
    String patientId;
    String patientUuid;
    String surname;
    String firstName;
    String fingerType;
    String fingerId;
    int matchingScore;
    String address;
    String hospitalNumber;
}
