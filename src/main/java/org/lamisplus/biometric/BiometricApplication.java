package org.lamisplus.biometric;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.Collections;

@SpringBootApplication
public class BiometricApplication {
	public static String userDir = System.getProperty ("user.dir");
	public static void main(String[] args) {
		SpringApplication springApplication = new SpringApplication(new Class[]{BiometricApplication.class});
		springApplication.setDefaultProperties(Collections.singletonMap("spring.config.additional-location", userDir + File.separator + "biometric-db-config.yml"));
		springApplication.run(args);
	}

}
