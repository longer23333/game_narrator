package cn.longer233.gamenarrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:static-compression-test",
        "game-narrator.storage-root=./target/static-compression-test-storage",
        "game-narrator.capacity.minimum-free-bytes=0",
        "game-narrator.capacity.minimum-free-percent=0"
})
class StaticResourceCompressionTest {
    @LocalServerPort int port;

    @Test
    void compressesLargeVersionedFrontendAssets() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/app.js"))
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();

        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(response.body()).isNotEmpty();
        assertThat((long) response.body().length)
                .isLessThan(new ClassPathResource("static/app.js").contentLength());
    }
}
