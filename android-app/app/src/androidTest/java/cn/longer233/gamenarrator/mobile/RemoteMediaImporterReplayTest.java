package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Environment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RemoteMediaImporterReplayTest {
    private MockWebServer server;
    private Context context;

    @Before public void setUp() throws Exception {
        server=new MockWebServer();
        server.start();
        context=ApplicationProvider.getApplicationContext();
        clearImports();
        RemoteMediaImporter.configureTimeoutsForTest(500,500);
    }

    @After public void tearDown() throws Exception {
        RemoteMediaImporter.configureTimeoutsForTest(15_000,30_000);
        clearImports();
        server.shutdown();
    }

    @Test public void downloads200WithoutContentLength() throws Exception {
        byte[] body="fixture-media".getBytes();
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type","video/mp4")
                .setChunkedBody(new okio.Buffer().write(body),3));
        RemoteMediaImporter.Result result=RemoteMediaImporter.download(context,url(),(p,d,t)->{});
        assertArrayEquals(body,Files.readAllBytes(result.file().toPath()));
    }

    @Test public void resumes206WithCorrectRange() throws Exception {
        String raw=url();
        File part=partFor(raw);
        Files.write(part.toPath(),"head".getBytes());
        server.enqueue(new MockResponse().setResponseCode(206).setHeader("Content-Type","video/mp4")
                .setHeader("Content-Range","bytes 4-7/8").setBody("tail"));
        RemoteMediaImporter.Result result=RemoteMediaImporter.download(context,raw,(p,d,t)->{});
        assertEquals("bytes=4-",server.takeRequest().getHeader("Range"));
        assertEquals("headtail",Files.readString(result.file().toPath()));
    }

    @Test public void restartsAfter416AndWrongRange() throws Exception {
        String raw=url();
        Files.write(partFor(raw).toPath(),"stale".getBytes());
        server.enqueue(new MockResponse().setResponseCode(416));
        server.enqueue(media(200,"fresh"));
        assertEquals("fresh",Files.readString(RemoteMediaImporter.download(context,raw,(p,d,t)->{}).file().toPath()));

        clearImports();
        Files.write(partFor(raw).toPath(),"stale".getBytes());
        server.enqueue(new MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 0-4/5"));
        server.enqueue(media(200,"fresh"));
        assertEquals("fresh",Files.readString(RemoteMediaImporter.download(context,raw,(p,d,t)->{}).file().toPath()));
    }

    @Test public void faultsRemainResumableAndAreClassified() throws Exception {
        for(int status:new int[]{403,429,500}) {
            server.enqueue(new MockResponse().setResponseCode(status));
            try { RemoteMediaImporter.download(context,url(),(p,d,t)->{}); fail("expected HTTP "+status); }
            catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("HTTP "+status)); }
        }
        server.enqueue(media(200,"partial-body").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        try { RemoteMediaImporter.download(context,url(),(p,d,t)->{}); fail("expected disconnect"); }
        catch(Exception expected) { assertTrue(partFor(url()).isFile()); }
        server.enqueue(media(200,"slow").setBodyDelay(2,TimeUnit.SECONDS));
        try { RemoteMediaImporter.download(context,url(),(p,d,t)->{}); fail("expected timeout"); }
        catch(Exception expected) { assertTrue(expected instanceof java.net.SocketTimeoutException || expected.getCause() instanceof java.net.SocketTimeoutException); }
    }

    private MockResponse media(int status,String body) { return new MockResponse().setResponseCode(status).setHeader("Content-Type","video/mp4").setBody(body); }
    private String url() { return server.url("/video.mp4").toString(); }
    private File imports() { return new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),"imports"); }
    private File partFor(String raw) throws Exception { File root=imports();assertTrue(root.isDirectory()||root.mkdirs());return new File(root,"resume-"+RemoteMediaImporter.resumeKey(java.net.URI.create(raw))+".part"); }
    private void clearImports() throws Exception { File root=imports();if(root.isDirectory())for(File file:root.listFiles())Files.deleteIfExists(file.toPath()); }
}
