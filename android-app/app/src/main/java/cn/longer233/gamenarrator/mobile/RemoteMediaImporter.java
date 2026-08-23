package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

public final class RemoteMediaImporter {
    private static int connectTimeoutMillis = 15_000;
    private static int readTimeoutMillis = 30_000;
    enum ResponseAction { ACCEPT_NEW, APPEND, RESTART, RETRY_LATER, REAUTHENTICATE, FAIL }

    static ResponseAction responseAction(int status,long offset,String contentRange) {
        if (status == 429 || status >= 500) return ResponseAction.RETRY_LATER;
        if (status == 401 || status == 403) return ResponseAction.REAUTHENTICATE;
        if (status == 416 && offset > 0) return ResponseAction.RESTART;
        if (status == 206 && offset > 0) {
            return contentRangeStartsAt(contentRange, offset) ? ResponseAction.APPEND : ResponseAction.RESTART;
        }
        if (status >= 200 && status < 300) return ResponseAction.ACCEPT_NEW;
        return ResponseAction.FAIL;
    }

    static void configureTimeoutsForTest(int connectMillis,int readMillis) {
        connectTimeoutMillis=connectMillis;
        readTimeoutMillis=readMillis;
    }
    public interface Progress { void update(int percent,long downloaded,long total); }
    public static final class Result {
        private final File file;private final String mimeType,displayName;
        Result(File file,String mimeType,String displayName){this.file=file;this.mimeType=mimeType;this.displayName=displayName;}
        public File file(){return file;}public String mimeType(){return mimeType;}public String displayName(){return displayName;}
    }
    private RemoteMediaImporter() { }

    public static String resolveDirectUrl(String rawUrl,String formatPreference) throws Exception {
        return resolveDirectUrl(rawUrl,formatPreference,"");
    }

    public static String resolveDirectUrl(String rawUrl,String formatPreference,String cookies) throws Exception {
        HttpURLConnection connection=open(sourceUri(rawUrl));
        applyCookies(connection,cookies);
        try{
            connection.connect();
            int status=connection.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("服务器返回 HTTP "+status);
            String mime=connection.getContentType();if(mime!=null&&mime.contains(";"))mime=mime.substring(0,mime.indexOf(';'));if(mime==null)mime="";
            if(mime.startsWith("video/")||mime.startsWith("audio/")||"application/octet-stream".equals(mime))return sourceUri(rawUrl).toString();
            if(!mime.startsWith("text/html"))throw new IllegalArgumentException("地址返回的不是媒体或可解析网页（"+mime+"）");
            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream input=connection.getInputStream()){byte[] bytes=new byte[8192];int read,total=0;while((read=input.read(bytes))>=0){total+=read;if(total>2*1024*1024)break;buffer.write(bytes,0,read);}}
            List<MediaLinkExtractor.Candidate> candidates=MediaLinkExtractor.extract(new String(buffer.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),sourceUri(rawUrl).toString());
            if(candidates.isEmpty())throw new IllegalArgumentException("网页中没有发现可下载的媒体直链");
            for(MediaLinkExtractor.Candidate candidate:candidates)if(MediaFormatPreference.matches(candidate.url(),candidate.mimeType(),formatPreference))return candidate.url();
            throw new IllegalArgumentException("网页媒体直链不符合所选格式："+formatPreference);
        }finally{connection.disconnect();}
    }

    public static Result download(Context context,String rawUrl,Progress progress) throws Exception {
        return download(context,rawUrl,"",progress);
    }

    public static Result download(Context context,String rawUrl,String cookies,Progress progress) throws Exception {
        URI source=sourceUri(rawUrl);
        File movies=context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);if(movies==null)throw new IllegalStateException("设备没有可用的影片目录");File root=new File(movies,"imports");if(!root.isDirectory()&&!root.mkdirs())throw new IllegalStateException("无法创建导入目录");
        File part=new File(root,"resume-"+resumeKey(source)+".part");
        long offset=part.isFile()?part.length():0;
        HttpURLConnection connection=open(source);
        try {
            applyCookies(connection,cookies);
            if(offset>0)connection.setRequestProperty("Range","bytes="+offset+"-");
            connection.connect();
            int status=connection.getResponseCode();
            ResponseAction action=responseAction(status,offset,connection.getHeaderField("Content-Range"));
            if(action==ResponseAction.RESTART){if(part.exists()&&!part.delete())throw new IllegalStateException("旧断点文件无法清理");connection.disconnect();return download(context,rawUrl,cookies,progress);}
            if(action==ResponseAction.RETRY_LATER)throw new IllegalStateException("服务器暂时不可用，请稍后从断点重试（HTTP "+status+"）");
            if(action==ResponseAction.REAUTHENTICATE)throw new IllegalStateException("平台会话已失效或无权访问（HTTP "+status+"）");
            if(status<200||status>=300)throw new IllegalStateException("服务器返回 HTTP "+status);
            boolean append=action==ResponseAction.APPEND;
            if(!append)offset=0;
            String mime=connection.getContentType();if(mime!=null&&mime.contains(";"))mime=mime.substring(0,mime.indexOf(';'));if(mime==null)mime="application/octet-stream";if(!mime.startsWith("video/")&&!mime.startsWith("audio/")&&!"application/octet-stream".equals(mime)){part.delete();throw new IllegalArgumentException("地址返回的不是音频或视频："+mime+"）");}
            String name=fileName(connection,source,mime);if("application/octet-stream".equals(mime)&&!name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|mov|webm|mkv|mp3|m4a|aac|wav|ogg|opus)$")){part.delete();throw new IllegalArgumentException("服务器未返回媒体类型，文件扩展名也无法识别");}File output=unique(root,name);long remaining=connection.getContentLengthLong(),total=remaining>0?offset+remaining:-1,done=offset,maxBytes=Math.max(0,allocatableBytes(context,root)-64L*1024*1024)+offset;if(total>0&&total>maxBytes)throw new IllegalStateException("可用空间不足，已保留 64 MiB 安全余量");
            try(BufferedInputStream input=new BufferedInputStream(connection.getInputStream());FileOutputStream stream=new FileOutputStream(part,append)){byte[] buffer=new byte[64*1024];int read;while((read=input.read(buffer))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedException("下载已取消，可稍后从断点继续");done+=read;if(done>maxBytes)throw new IllegalStateException("下载已暂停：可用空间低于 64 MiB 安全余量");stream.write(buffer,0,read);progress.update(total>0?(int)Math.min(99,done*100/total):-1,done,total);}stream.getFD().sync();}
            finalizeDownload(part,output);progress.update(100,done,total);return new Result(output,mime,output.getName());
        } finally {
            connection.disconnect();
        }
    }

    private static URI sourceUri(String rawUrl) throws Exception {
        URI source=URI.create(rawUrl.trim());String scheme=source.getScheme();if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))throw new IllegalArgumentException("只支持 HTTP 或 HTTPS 媒体地址");if(source.getUserInfo()!=null)throw new IllegalArgumentException("媒体地址不能包含用户名或密码");
        return source;
    }

    private static HttpURLConnection open(URI source) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(source.toString()).openConnection();connection.setConnectTimeout(connectTimeoutMillis);connection.setReadTimeout(readTimeoutMillis);connection.setInstanceFollowRedirects(true);connection.setRequestProperty("User-Agent","GameNarrator-Android/"+BuildConfig.VERSION_NAME);
        return connection;
    }

    private static void applyCookies(HttpURLConnection connection,String cookies){
        if(cookies!=null&&!cookies.isBlank())connection.setRequestProperty("Cookie",cookies.trim());
    }

    static boolean contentRangeStartsAt(String value,long offset){
        return value!=null&&value.matches("bytes\\s+"+offset+"-\\d+/((\\d+)|\\*)");
    }

    static String resumeKey(URI source) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(source.normalize().toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder value=new StringBuilder();for(int i=0;i<12;i++)value.append(String.format(Locale.ROOT,"%02x",digest[i]));return value.toString();
    }

    static void finalizeDownload(File partial, File output) throws Exception {
        try {
            Files.move(partial.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(partial.toPath(), output.toPath());
        }
    }

    private static long allocatableBytes(Context context, File root) {
        StorageManager storage = context.getSystemService(StorageManager.class);
        if (storage == null) return root.getUsableSpace();
        try {
            return storage.getAllocatableBytes(storage.getUuidForPath(root));
        } catch (Exception unavailable) {
            return root.getUsableSpace();
        }
    }

    private static String fileName(HttpURLConnection connection,URI source,String mime){String value=connection.getHeaderField("Content-Disposition");if(value!=null){int marker=value.toLowerCase(Locale.ROOT).indexOf("filename=");if(marker>=0)value=value.substring(marker+9).replace("\"","").trim();else value=null;}if(value==null||value.isBlank()){String path=source.getPath();value=path==null?"":path.substring(path.lastIndexOf('/')+1);}value=value.replaceAll("[^A-Za-z0-9._-]","_");if(value.isBlank()||!value.contains("."))value="remote-"+System.currentTimeMillis()+(mime.startsWith("audio/")?".m4a":".mp4");return value;}
    private static File unique(File root,String name){File value=new File(root,name);if(!value.exists())return value;int dot=name.lastIndexOf('.');String base=dot>0?name.substring(0,dot):name,ext=dot>0?name.substring(dot):"";return new File(root,base+"-"+System.currentTimeMillis()+ext);}
}
