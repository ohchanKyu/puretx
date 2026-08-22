package com.acme.orders;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the test application without a web server.
 *
 * <p>{@code NONE} matters here: spring-webflux is on the test classpath so the WebClient detector
 * can be exercised, and without this Boot would decide the application is reactive and skip the
 * auto-configuration that supplies {@code RestTemplateBuilder}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = PuretxTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public @interface PuretxIntegrationTest {
}
