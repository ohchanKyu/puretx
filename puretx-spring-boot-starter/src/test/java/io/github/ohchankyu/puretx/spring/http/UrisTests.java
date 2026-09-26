package io.github.ohchankyu.puretx.spring.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrisTests {

    @Test
    @DisplayName("the query string never reaches a report, because that is where tokens travel")
    void dropsTheQuery() {
        assertThat(Uris.describe(URI.create("https://api.example.com/v1/charge?access_token=SECRET&user=me")))
                .isEqualTo("https://api.example.com/v1/charge");
    }

    @Test
    @DisplayName("user info and the fragment go too; the port stays, because it tells services apart")
    void dropsUserInfoAndFragmentKeepsPort() {
        assertThat(Uris.describe(URI.create("http://bob:hunter2@127.0.0.1:8081/charge#top")))
                .isEqualTo("http://127.0.0.1:8081/charge");
    }

    @Test
    @DisplayName("an empty path reads as the root, and a relative URI keeps what it has minus the query")
    void edgeShapes() {
        assertThat(Uris.describe(URI.create("https://api.example.com"))).isEqualTo("https://api.example.com/");
        assertThat(Uris.describe(URI.create("/charge?token=x"))).isEqualTo("/charge");
        assertThat(Uris.describe(null)).isEmpty();
    }
}
