package cn.longer233.gamenarrator;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkConfigurationInitializerTest {
    @Test
    void systemModeDelegatesPublicRequestsAndKeepsLocalServicesDirect() {
        ProxySelector original = ProxySelector.getDefault();
        Proxy expected = new Proxy(Proxy.Type.HTTP, new java.net.InetSocketAddress("127.0.0.1", 9999));
        try (var context = new GenericApplicationContext()) {
            ProxySelector.setDefault(new ProxySelector() {
                @Override public java.util.List<Proxy> select(URI uri) { return java.util.List.of(expected); }
                @Override public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException failure) { }
            });
            new NetworkConfigurationInitializer().initialize(context);
            assertThat(ProxySelector.getDefault().select(URI.create("https://example.com"))).containsExactly(expected);
            assertThat(ProxySelector.getDefault().select(URI.create("http://localhost:11434"))).containsExactly(Proxy.NO_PROXY);
        } finally { ProxySelector.setDefault(original); }
    }

    @Test
    void explicitProxyBypassesLoopbackAndDirectModeDisablesIt() {
        ProxySelector original = ProxySelector.getDefault();
        try (var context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("network", Map.of(
                    "game-narrator.network.mode", "HTTP", "game-narrator.network.proxy-url", "http://127.0.0.1:7890")));
            new NetworkConfigurationInitializer().initialize(context);
            assertThat(ProxySelector.getDefault().select(URI.create("https://example.com")).getFirst().type()).isEqualTo(Proxy.Type.HTTP);
            for (String host : java.util.List.of("localhost", "127.0.0.1", "[::1]")) {
                assertThat(ProxySelector.getDefault().select(URI.create("http://" + host + ":11434")))
                        .containsExactly(Proxy.NO_PROXY);
            }
            context.getEnvironment().getPropertySources().replace("network", new MapPropertySource("network",
                    Map.of("game-narrator.network.mode", "DIRECT")));
            new NetworkConfigurationInitializer().initialize(context);
            assertThat(ProxySelector.getDefault().select(URI.create("https://example.com"))).containsExactly(Proxy.NO_PROXY);
        } finally { ProxySelector.setDefault(original); }
    }

    @Test
    void rejectsProxyCredentialsBeforeChangingGlobalSelector() {
        ProxySelector original = ProxySelector.getDefault();
        try (var context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("network", Map.of(
                    "game-narrator.network.mode", "HTTP", "game-narrator.network.proxy-url", "http://user:secret@localhost:7890")));
            assertThatThrownBy(() -> new NetworkConfigurationInitializer().initialize(context))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ProxySelector.getDefault()).isSameAs(original);
        }
    }
}
