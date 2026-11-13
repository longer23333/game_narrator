package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WikimediaAssetClientTest {
    @Test
    void encodesSearchTermsExactlyOnce() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/w/api.php", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = "{\"query\":{\"pages\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var client = new WikimediaAssetClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), true, 3,
                    new AssetSearchResilience(1, 0, 10, 6, 16));
            client.search(new AssetSearchRequest("战斗 test", "IMAGE", 5, 1, true, true, null, "RELEVANCE"));
            assertThat(query.get()).contains("gsrsearch=%E6%88%98%E6%96%97%20test");
            assertThat(query.get()).doesNotContain("%25E6", "%2520");
        } finally { server.stop(0); }
    }
}
