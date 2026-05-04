package com.qespe.fiscal_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // required by FiscalDocumentRetryScheduler
public class FiscalServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FiscalServiceApplication.class, args);
	}

}
