package org.lamisplus.biometric.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
@Configuration("applicationProperties")
@Data
public class Application {
    private String serverUrl;
    private String  libraryPath;
    private int quality;
    public static String biometricDirectory;

    @PostConstruct
    public void setBiometricDirectory(){
         biometricDirectory = getLibraryPath();
    }
}
