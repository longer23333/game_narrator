package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalModelCatalogServiceTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void listsInstalledModelsWithHealthResourcesAndActiveRoles() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[{\"name\":\"qwen2.5vl:3b\",\"size\":3200000000}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var service = new LocalModelCatalogService(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), HttpClient.newHttpClient());

        var catalog = service.catalog("qwen2.5vl:3b", "qwen2.5:3b");

        assertThat(catalog.serviceAvailable()).isTrue();
        assertThat(catalog.installed()).singleElement().satisfies(model -> {
            assertThat(model.health()).isEqualTo("READY");
            assertThat(model.activeVision()).isTrue();
            assertThat(model.memoryMb()).isPositive();
            assertThat(model.vramMb()).isPositive();
        });
        assertThat(catalog.recommendations()).isNotEmpty();
    }

    @Test
    void reportsOfflineWithoutPretendingModelsAreHealthy() {
        var service = new LocalModelCatalogService(new ObjectMapper(), "http://127.0.0.1:1", HttpClient.newHttpClient());
        var catalog = service.catalog("vision", "text");
        assertThat(catalog.serviceAvailable()).isFalse();
        assertThat(catalog.health()).isEqualTo("OFFLINE");
        assertThat(catalog.installed()).isEmpty();
    }

    @Test
    void rejectsUnsafePullNames() {
        assertThatThrownBy(() -> LocalModelCatalogService.validateName("name; rm"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
