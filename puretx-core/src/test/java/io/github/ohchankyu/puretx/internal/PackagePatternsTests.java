package io.github.ohchankyu.puretx.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PackagePatternsTests {

    @Test
    @DisplayName("a pattern with no wildcards covers the package and everything below it")
    void plainPatternCoversSubPackages() {
        PackagePatterns patterns = PackagePatterns.of(List.of("com.acme.legacy"));

        assertThat(patterns.matches("com.acme.legacy")).isTrue();
        assertThat(patterns.matches("com.acme.legacy.OrderService")).isTrue();
        assertThat(patterns.matches("com.acme.legacy.deep.OrderService")).isTrue();
        assertThat(patterns.matches("com.acme.legacyish.OrderService")).isFalse();
        assertThat(patterns.matches("com.acme.orders.OrderService")).isFalse();
    }

    @Test
    @DisplayName("a single star stops at the package separator, a double star does not")
    void wildcardScoping() {
        assertThat(PackagePatterns.of(List.of("com.acme.*.OrderService"))
                .matches("com.acme.orders.OrderService")).isTrue();
        assertThat(PackagePatterns.of(List.of("com.acme.*.OrderService"))
                .matches("com.acme.orders.deep.OrderService")).isFalse();
        assertThat(PackagePatterns.of(List.of("com.acme.**.OrderService"))
                .matches("com.acme.orders.deep.OrderService")).isTrue();
    }

    @Test
    @DisplayName("a pattern matches a transaction name too, wildcards or not")
    void matchesTransactionNamesAsWellAsClassNames() {
        // Spring names a transaction FQCN + "." + method, so an ignore pattern written for a class
        // has to cover the name with the method on the end. Both branches of toRegex must agree.
        final String type = "com.acme.orders.OrderService";
        final String transaction = type + ".createOrder";

        for (final String pattern : List.of(
                "com.acme.orders",
                "com.acme.orders.OrderService",
                "com.acme.*.OrderService",
                "com.acme.**.OrderService",
                "com.acme.orders.*",
                "com.acme.**")) {
            final PackagePatterns patterns = PackagePatterns.of(List.of(pattern));
            assertThat(patterns.matches(type)).as("%s should match the class", pattern).isTrue();
            assertThat(patterns.matches(transaction))
                    .as("%s should match the transaction name", pattern).isTrue();
        }
    }

    @Test
    @DisplayName("an empty configuration matches nothing and short-circuits")
    void emptyMatchesNothing() {
        assertThat(PackagePatterns.of(List.of()).isEmpty()).isTrue();
        assertThat(PackagePatterns.of(null).matches("anything")).isFalse();
        assertThat(PackagePatterns.of(List.of("   ")).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a dot is a dot, not a regex wildcard")
    void dotsAreLiteral() {
        assertThat(PackagePatterns.of(List.of("com.acme.Order")).matches("comXacmeXOrder")).isFalse();
    }
}
