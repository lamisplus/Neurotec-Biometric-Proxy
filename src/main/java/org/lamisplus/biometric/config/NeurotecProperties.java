package org.lamisplus.biometric.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "lamisplus.neurotec")
@Configuration("neurotecProperties")
public class NeurotecProperties {

    private String pluginSearchPath = "";

    private int identificationThreshold = 96;

    /** Applied to the one to one check that confirms a candidate from the search. */
    private int verificationThreshold = 96;

    /** How many candidates the search offers for verification. Each is confirmed independently. */
    private int candidateCount = 10;

    /** Zero leaves the SDK default. Raise only on evidence: it drops prints from the gallery. */
    private int minimalMinutiaCount = 0;

    private String licenseServer = "/local";

    private String licensePort = "5000";

    private List<String> licenseComponents = new ArrayList<>(Arrays.asList(
            "Biometrics.FingerExtraction",
            "Biometrics.Standards.FingerTemplates",
            "Biometrics.FingerMatching",
            "Devices.FingerScanners"));
}
