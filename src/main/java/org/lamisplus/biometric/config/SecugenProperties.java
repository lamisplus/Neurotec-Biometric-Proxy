
package org.lamisplus.biometric.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author
 */
@Data
@ConfigurationProperties(prefix = "lamisplus.secugen.biometric")
@Configuration("secugenProperties")
public class SecugenProperties {
    
    private String serverUrl = "http://localhost:8282";
    
    private String serverPort = "8282";
    
    private Long timeout = 3000L;
    
    private Long quality = 61L;
    
}
