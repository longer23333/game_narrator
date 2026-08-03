package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilibiliAssetClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void normalizesSearchMetadataAndPagination() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/x/web-interface/search/type", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                    {"code":0,"data":{"result":[{"bvid":"BV1TEST","title":"<em>Boss</em> fight",
                    "author":"UP主","pic":"//i.example/cover.jpg","duration":"01:23","typename":"游戏",
                    "play":1234,"video_review":56,"tag":"动作,高能"}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        BilibiliAssetClient client = client();
        JsonNode item = client.search(new AssetSearchRequest("boss", "VIDEO", 12, 3,
                true, true, "BILIBILI", "POPULAR")).path("results").get(0);

        assertTrue(query.get().contains("page=3"));
        assertTrue(query.get().contains("order=click"));
        assertEquals("BV1TEST:VIDEO", item.path("id").asText());
        assertEquals("Boss fight", item.path("title").asText());
        assertEquals(83_000, item.path("duration").asLong());
        assertEquals("游戏", item.path("tags").get(0).asText());
        assertTrue(item.path("attribution").asText().contains("播放 1234，弹幕 56"));
    }

    @Test
    void exposesVideoCoversAndAudioTracksAsRightsReviewCandidates() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/x/web-interface/search/type", exchange -> {
            byte[] body = """
                    {"code":0,"data":{"result":[{"bvid":"BV1MEDIA","title":"素材候选",
                    "author":"UP主","pic":"//i.example/cover.jpg","duration":"00:09","typename":"生活",
                    "play":12,"video_review":3,"tag":"搞笑"}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        BilibiliAssetClient client = client();
        assertTrue(client.supports("VIDEO"));
        assertFalse(client.supports("MEME"));
        assertTrue(client.supports("SFX"));
        assertTrue(client.supports("BGM"));
        JsonNode audio = client.search(new AssetSearchRequest("funny", "SFX", 12, 1,
                true, true, "BILIBILI", "RELEVANCE")).path("results").get(0);

        assertEquals("BV1MEDIA:SFX", audio.path("id").asText());
        assertEquals("视频音轨候选", audio.path("tags").get(0).asText());
    }

    @Test
    void rejectsProviderErrorSoCatalogCanApplyCooldown() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/x/web-interface/search/type", exchange -> {
            byte[] body = "{\"code\":-412,\"message\":\"request blocked\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client().search(new AssetSearchRequest("boss", "VIDEO", 12, 1,
                        true, true, "BILIBILI", "RELEVANCE")));
        assertTrue(error.getMessage().contains("code=-412"));
    }

    @Test
    void resolvesCanonicalVideoTitleCreatorAndTagsByBvid() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/x/web-interface/view", exchange -> respond(exchange, """
                {"code":0,"data":{"title":"曹操玩笑记(三)","owner":{"name":"Rarondo9"},
                "tname":"影视剪辑","duration":560,"pic":"//i.example/cover.jpg"}}
                """));
        server.createContext("/x/tag/archive/tags", exchange -> respond(exchange, """
                {"code":0,"data":[{"tag_name":"曹操"},{"tag_name":"三国演义"}]}
                """));
        server.start();

        BilibiliAssetClient.VideoMetadata metadata = client().metadata("BV17e356iEEA");

        assertEquals("曹操玩笑记(三)", metadata.title());
        assertEquals("Rarondo9", metadata.creator());
        assertEquals(560_000, metadata.durationMs());
        assertEquals(java.util.List.of("影视剪辑", "曹操", "三国演义"), metadata.tags());
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private BilibiliAssetClient client() {
        return new BilibiliAssetClient(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), true, 0, 2);
    }
}
