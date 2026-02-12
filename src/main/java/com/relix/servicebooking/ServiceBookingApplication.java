package com.relix.servicebooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServiceBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceBookingApplication.class, args);
    }
}
