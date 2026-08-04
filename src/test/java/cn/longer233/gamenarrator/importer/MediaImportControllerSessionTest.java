package cn.longer233.gamenarrator.importer;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaImportControllerSessionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsCookieTokenUploadedByAnotherBrowserSession() {
        YtDlpMediaImporter importer = mock(YtDlpMediaImporter.class);
        String token = "d38e4bd7-bf8f-428d-9f7b-31b6169b179c";
        when(importer.uploadCookies(any(), anyString())).thenReturn(token);
        MediaImportController controller = new MediaImportController(importer, mock(RemoteThumbnailService.class),
                mock(MediaDownloadJobService.class), mock(PlatformContentClassifier.class),
                mock(cn.longer233.gamenarrator.asset.AssetCatalogService.class),
                mock(RemoteProjectImportService.class));
        MockHttpSession owner = new MockHttpSession();
        MockHttpSession otherUser = new MockHttpSession();
        controller.uploadCookies(new MockMultipartFile("file", "cookies.txt", "text/plain",
                        "cookie".getBytes()), "https://www.bilibili.com/video/example", owner);

        assertThrows(IllegalStateException.class, () -> controller.resolve(
                new MediaResolveRequest("https://www.bilibili.com/video/example", true, token), otherUser));
    }

    @Test
    void previewFileIsOnlyVisibleToTheCreatingBrowserSession() throws Exception {
        YtDlpMediaImporter importer = mock(YtDlpMediaImporter.class);
        Path video = Files.write(temporaryDirectory.resolve("preview.mp4"), new byte[]{1, 2, 3});
        when(importer.download(any())).thenReturn(new MediaDownloadResult(
                "COMPLETED", video.toString(), "preview.mp4", 3, null, null));
        MediaImportController controller = new MediaImportController(importer, mock(RemoteThumbnailService.class),
                mock(MediaDownloadJobService.class), mock(PlatformContentClassifier.class),
                mock(cn.longer233.gamenarrator.asset.AssetCatalogService.class),
                mock(RemoteProjectImportService.class));
        MockHttpSession owner = new MockHttpSession();
        MediaDownloadRequest request = new MediaDownloadRequest("https://example.com/video", "18",
                false, false, true, null, "title", null, null, 1.0, List.of());

        MediaPreviewResult created = controller.createPreview(request, owner);
        UUID token = UUID.fromString(created.previewUrl().substring(created.previewUrl().lastIndexOf('/') + 1));

        assertEquals(200, controller.preview(token, owner).getStatusCode().value());
        assertEquals(404, controller.preview(token, new MockHttpSession()).getStatusCode().value());
    }
}
