package com.flux.fluxproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FluxProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxProjectApplication.class, args);
    }

}
