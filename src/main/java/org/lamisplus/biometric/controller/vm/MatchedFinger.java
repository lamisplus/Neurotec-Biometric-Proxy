package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchedFinger {
    public String id;
    public String fingerType;
    public List<PersonMatched> personsMatched;
}
