package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The main class for the Spring Boot application.
 * @SpringBootApplication enables autoconfiguration and component scanning.
 */
@SpringBootApplication
public class MySpringAppApplication {

    static void main(String[] args) {
        SpringApplication.run(MySpringAppApplication.class, args);
    }

}
