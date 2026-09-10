package com.example.orderagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the order agent service.
 *
 * <p>Boots the Spring context: the web layer, the H2 database, and the
 * governed agent loop described in the project README.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderAgentApplication.class, args);
    }
}
