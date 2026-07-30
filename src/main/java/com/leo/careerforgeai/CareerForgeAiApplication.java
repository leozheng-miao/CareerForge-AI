package com.leo.careerforgeai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CareerForgeAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerForgeAiApplication.class, args);
    }

}