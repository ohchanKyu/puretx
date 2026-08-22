package com.example.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A tiny shop with one impure transaction and one clean one.
 *
 * <p>Run it and call both endpoints:
 * <pre>
 * ./gradlew :puretx-sample:run
 * curl -X POST localhost:8080/orders/impure
 * curl -X POST localhost:8080/orders/clean
 * </pre>
 * The first one prints a puretx report. The second one does not.
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
