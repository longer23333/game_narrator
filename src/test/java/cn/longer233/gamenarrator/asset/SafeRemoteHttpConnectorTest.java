package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeRemoteHttpConnectorTest {
    private final SafeRemoteHttpConnector connector = new SafeRemoteHttpConnector();

    @Test
    void rejectsNonHttpsResource() {
        assertThatThrownBy(() -> connector.validatePublicHttps(URI.create("http://example.com/audio.mp3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsLoopbackResource() {
        assertThatThrownBy(() -> connector.validatePublicHttps(URI.create("https://127.0.0.1/audio.mp3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内网");
    }
}
