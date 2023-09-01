package org.lamisplus.biometric.domain.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdentifiedClient {
    public Long id;
    public String uuid;
    public String hospitalNumber;
    public String surname;
    public String otherName;
    public String firstName;
    public String sex;
}
