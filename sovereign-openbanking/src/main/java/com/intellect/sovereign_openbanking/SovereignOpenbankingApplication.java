package com.intellect.sovereign_openbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.intellect.sovereign")
public class SovereignOpenbankingApplication {

	public static void main(String[] args) {
		SpringApplication.run(SovereignOpenbankingApplication.class, args);
	}

}
