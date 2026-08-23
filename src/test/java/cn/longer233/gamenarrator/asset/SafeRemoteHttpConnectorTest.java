package cn.longer233.gamenarrator.asset;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void pinsValidatedAddressesAndRefusesUnexpectedDnsLookups() throws Exception {
        InetAddress selected=InetAddress.getByAddress("public.example",new byte[]{1,1,1,1});
        var resolver=SafeRemoteHttpConnector.pinnedDnsResolver("public.example",new InetAddress[]{selected});

        assertThat(resolver.resolve("PUBLIC.EXAMPLE")).containsExactly(selected);
        assertThatThrownBy(() -> resolver.resolve("rebound.example"))
                .isInstanceOf(java.net.UnknownHostException.class)
                .hasMessageContaining("未校验");
    }
}
