package cn.longer233.gamenarrator;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public class NetworkConfigurationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String mode = environment.getProperty("game-narrator.network.mode", "SYSTEM").toUpperCase(Locale.ROOT);
        ProxySelector system = ProxySelector.getDefault();
        Proxy explicit = Proxy.NO_PROXY;
        if ("HTTP".equals(mode)) {
            URI uri = URI.create(environment.getRequiredProperty("game-narrator.network.proxy-url"));
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1
                    || uri.getUserInfo() != null) throw new IllegalArgumentException("Network proxy must be an HTTP host and port without credentials");
            explicit = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        } else if (!List.of("SYSTEM", "DIRECT").contains(mode)) {
            throw new IllegalArgumentException("Network mode must be SYSTEM, DIRECT or HTTP");
        }
        Proxy selected = explicit;
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                String host = uri.getHost();
                if (host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                        || host.equals("[::1]") || host.equals("::1"))) return List.of(Proxy.NO_PROXY);
                return "SYSTEM".equals(mode) && system != null ? system.select(uri) : List.of(selected);
            }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) {
                if ("SYSTEM".equals(mode) && system != null) system.connectFailed(uri, address, failure);
            }
        });
    }
}
