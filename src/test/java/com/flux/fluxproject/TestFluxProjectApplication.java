package com.flux.fluxproject;

import org.springframework.boot.SpringApplication;

public class TestFluxProjectApplication {

    public static void main(String[] args) {
        SpringApplication.from(FluxProjectApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
