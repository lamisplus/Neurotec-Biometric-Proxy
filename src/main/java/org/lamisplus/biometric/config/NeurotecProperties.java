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

    /**
     * Searching a large gallery is many comparisons, so a 1:1 threshold false-accepts at 1:N.
     */
    private int identificationThreshold = 144;

    /** Below this a template is too weak to identify anyone, whatever the matcher scores it. */
    private int minimalMinutiaCount = 8;

    private String licenseServer = "/local";

    private String licensePort = "5000";

    private List<String> licenseComponents = new ArrayList<>(Arrays.asList(
            "Biometrics.FingerExtraction",
            "Biometrics.Standards.FingerTemplates",
            "Biometrics.FingerMatching",
            "Devices.FingerScanners"));
}
