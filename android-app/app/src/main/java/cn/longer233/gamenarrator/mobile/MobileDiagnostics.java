
package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.StatFs;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MobileDiagnostics {
    private MobileDiagnostics() { }

    public static String inspect(Context context,boolean ttsReady) {
        File files=context.getFilesDir();StatFs stat=new StatFs(files.getAbsolutePath());
        long available=stat.getAvailableBytes(),total=stat.getTotalBytes();
        int avc=0,hevc=0,aac=0;
        for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos())if(codec.isEncoder()){
            for(String type:codec.getSupportedTypes()){
                if("video/avc".equalsIgnoreCase(type))avc++;
                if("video/hevc".equalsIgnoreCase(type))hevc++;
                if("audio/mp4a-latm".equalsIgnoreCase(type))aac++;
            }
        }
        Runtime runtime=Runtime.getRuntime();
        return "GameNarrator Android 诊断报告（不含账号、Cookie、令牌或媒体内容）\n"
                +"生成时间: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date())+"\n"
                +"应用版本: "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")\n"
                +"Android: "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\n"
                +"设备: "+Build.MANUFACTURER+" "+Build.MODEL+"\n"
                +"ABI: "+String.join(", ",Build.SUPPORTED_ABIS)+"\n"
                +"CPU 核心: "+runtime.availableProcessors()+"\n"
                +"Java 最大堆: "+formatBytes(runtime.maxMemory())+"\n"
                +"应用存储可写: "+files.canWrite()+"\n"
                +"应用存储可用/总量: "+formatBytes(available)+" / "+formatBytes(total)+"\n"
                +"H.264 编码器: "+avc+"\nHEVC 编码器: "+hevc+"\nAAC 编码器: "+aac+"\n"
                +"本地数据库: "+formatBytes(context.getDatabasePath("game-narrator-mobile.db").length())+"\n"
                +"应用缓存: "+formatBytes(directorySize(context.getCacheDir()))+"\n"
                +"端侧转写引擎: "+(WhisperModelRunner.isReady(context)?"已安装":"未安装")+"\n"
                +"端侧视觉模型: "+(MobileModelDirectory.check(context).vision()?"已安装":"未安装")+"\n"
                +"端侧文案模型: "+(MobileModelDirectory.check(context).text()?"已安装":"未安装")+"\n"
                +"端侧 TTS: "+(ttsReady?"Android 系统中文引擎可用":"未检测到中文语音包")+"\n";
    }

    public static File export(Context context,String report) throws Exception {
        File root=new File(context.getCacheDir(),"diagnostics");if(!root.isDirectory()&&!root.mkdirs())throw new IllegalStateException("无法创建诊断目录");
        File output=new File(root,"GameNarrator-diagnostics-"+System.currentTimeMillis()+".txt");
        try(FileOutputStream stream=new FileOutputStream(output)){stream.write(report.getBytes(StandardCharsets.UTF_8));}
        return output;
    }

    public static long clearCache(Context context) {
        File root=context.getCacheDir();long before=directorySize(root);File[] children=root.listFiles();if(children!=null)for(File child:children)deleteInside(root,child);return before-directorySize(root);
    }

    private static void deleteInside(File root,File target){
        try{String base=root.getCanonicalPath()+File.separator;String path=target.getCanonicalPath();if(!path.startsWith(base))return;}catch(Exception ignored){return;}
        if(target.isDirectory()){File[] children=target.listFiles();if(children!=null)for(File child:children)deleteInside(root,child);}
        target.delete();
    }
    private static long directorySize(File file){if(file==null||!file.exists())return 0;if(file.isFile())return file.length();long total=0;File[] children=file.listFiles();if(children!=null)for(File child:children)total+=directorySize(child);return total;}
    public static String formatBytes(long bytes){if(bytes<1024)return bytes+" B";if(bytes<1024*1024)return String.format(Locale.CHINA,"%.1f KiB",bytes/1024d);if(bytes<1024L*1024*1024)return String.format(Locale.CHINA,"%.1f MiB",bytes/1024d/1024);return String.format(Locale.CHINA,"%.2f GiB",bytes/1024d/1024/1024);}
}
