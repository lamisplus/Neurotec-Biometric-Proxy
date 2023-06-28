package org.lamisplus.biometric.controller.vm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeduplicationResponse {
    private String message;
    private String messageType;
    private Integer numberOfMatchedFingers;
    private Boolean leftThumbMatched;
    private Boolean leftIndexMatched;
    private Boolean leftMiddleMatched;
    private Boolean leftRingMatched;
    private Boolean leftLittleMatched;
    private Boolean rightThumbMatched;
    private Boolean rightIndexMatched;
    private Boolean rightMiddleMatched;
    private Boolean rightRingMatched;
    private Boolean rightLittleMatched;

    private MatchFingerInformation matchDetails;
}
