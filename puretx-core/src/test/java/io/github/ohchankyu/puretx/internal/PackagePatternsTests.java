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
    @DisplayName("a single star still stops at a package separator")
    void singleWildcardDoesNotCrossPackageSeparator() {
        final PackagePatterns patterns = PackagePatterns.of(List.of("com.acme.*"));

        assertThat(patterns.matches("com.acme.OrderService")).isTrue();
        assertThat(patterns.matches("com.acme.orders.OrderService")).isFalse();
        assertThat(patterns.matches("com.acme.orders.OrderService.createOrder")).isFalse();
    }

    @Test
    @DisplayName("a double star crosses separators, which is the whole difference")
    void doubleWildcardCrossesPackageSeparators() {
        final PackagePatterns patterns = PackagePatterns.of(List.of("com.acme.**"));

        assertThat(patterns.matches("com.acme.OrderService")).isTrue();
        assertThat(patterns.matches("com.acme.orders.OrderService")).isTrue();
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
