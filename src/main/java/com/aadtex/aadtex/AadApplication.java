package com.aadtex.aadtex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AadApplication {

    public static void main(String[] args) {
        SpringApplication.run(AadApplication.class, args);
    }

}
