package cn.longer233.gamenarrator.importer;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteThumbnailServiceTest {
    @Test
    void upgradesPlatformHttpThumbnailToHttps() {
        URI result = RemoteThumbnailService.upgradeToHttps(
                URI.create("http://i.example.com/cover.jpg?x=1"));

        assertEquals("https://i.example.com/cover.jpg?x=1", result.toString());
    }
}
