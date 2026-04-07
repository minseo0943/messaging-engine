package com.jdc.presence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.jdc")
public class PresenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PresenceServiceApplication.class, args);
    }
}
