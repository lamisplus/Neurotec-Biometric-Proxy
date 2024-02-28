package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeduplicationDetails {
    String patientId ;
    String patientUuid;
    String surname;
    String gender;
    LocalDate dateOfBirth;
    String firstName;
    String address;
    String hospitalNumber;
    List<MatchedFinger> matchedFingers;
}
