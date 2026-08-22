package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ohchankyu.puretx.PuretxSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code PuretxProperties} and {@code PuretxSettings} agreeing about what the defaults are.
 *
 * <p>They have to be stated twice — {@code spring-boot-configuration-processor} only reads
 * compile-time constants, and it is what puts the default into an IDE's autocompletion. So the
 * duplication is deliberate, and this test is what stops it drifting. It has already caught one:
 * {@code call-path-depth} was 8 in one file and 12 in the other.
 */
class PuretxPropertyDefaultsTests {

    @Test
    @DisplayName("an untouched PuretxProperties produces exactly the core defaults")
    void propertyDefaultsMatchCoreDefaults() {
        final PuretxSettings fromProperties = new PuretxProperties().toSettings();
        final PuretxSettings fromCore = PuretxSettings.defaults();

        assertThat(fromProperties.enabled()).isEqualTo(fromCore.enabled());
        assertThat(fromProperties.mode()).isEqualTo(fromCore.mode());
        assertThat(fromProperties.maxDuration()).isEqualTo(fromCore.maxDuration());
        assertThat(fromProperties.includeCallPath()).isEqualTo(fromCore.includeCallPath());
        assertThat(fromProperties.callPathDepth()).isEqualTo(fromCore.callPathDepth());
        assertThat(fromProperties.recordLimit()).isEqualTo(fromCore.recordLimit());
        assertThat(fromProperties.detectInTestTransactions())
                .isEqualTo(fromCore.detectInTestTransactions());
        assertThat(fromProperties.ignore()).isEqualTo(fromCore.ignore());
        assertThat(fromProperties.appPackages()).isEqualTo(fromCore.appPackages());
    }

    @Test
    @DisplayName("every detector is on by default, in both places")
    void detectorDefaultsMatchCoreDefaults() {
        final PuretxSettings fromProperties = new PuretxProperties().toSettings();

        assertThat(fromProperties.describe()).isEqualTo(PuretxSettings.defaults().describe());
    }
}
