package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubtitleFileCodec {
    private static final Pattern TIMING = Pattern.compile("(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{3}).*");
    private SubtitleFileCodec() { }

    public static final class Cue {
        private final long startMs, endMs;
        private final String text;
        public Cue(long startMs,long endMs,String text){
            if(startMs<0||endMs<=startMs)throw new IllegalArgumentException("字幕时间范围无效");
            this.startMs=startMs;this.endMs=endMs;this.text=text==null?"":text.trim();
        }
        public long startMs(){return startMs;} public long endMs(){return endMs;} public String text(){return text;}
    }

    public static List<Cue> parse(String value){
        String normalized=(value==null?"":value).replace("\r\n","\n").replace('\r','\n').replace("\uFEFF","");
        List<Cue> cues=new ArrayList<>();
        for(String block:normalized.split("\\n\\s*\\n")){
            String[] lines=block.strip().split("\\n");
            int timingIndex=-1;Matcher match=null;
            for(int i=0;i<lines.length;i++){Matcher candidate=TIMING.matcher(lines[i].trim());if(candidate.matches()){timingIndex=i;match=candidate;break;}}
            if(match==null||timingIndex+1>=lines.length)continue;
            long start=timestamp(match,1),end=timestamp(match,5);if(end<=start)continue;
            StringBuilder text=new StringBuilder();for(int i=timingIndex+1;i<lines.length;i++){if(text.length()>0)text.append('\n');text.append(lines[i].trim());}
            if(!text.toString().isBlank())cues.add(new Cue(start,end,text.toString()));
            if(cues.size()>10000)throw new IllegalArgumentException("字幕条目不能超过 10000 条");
        }
        return cues;
    }

    public static String format(List<Cue> cues){
        StringBuilder out=new StringBuilder();int index=1;
        for(Cue cue:cues){if(cue.text().isBlank())continue;out.append(index++).append('\n').append(timestamp(cue.startMs())).append(" --> ").append(timestamp(cue.endMs())).append('\n').append(cue.text()).append("\n\n");}
        return out.toString();
    }

    private static long timestamp(Matcher match,int offset){return (((Long.parseLong(match.group(offset))*60+Long.parseLong(match.group(offset+1)))*60+Long.parseLong(match.group(offset+2)))*1000)+Long.parseLong(match.group(offset+3));}
    private static String timestamp(long ms){long hours=ms/3_600_000;ms%=3_600_000;long minutes=ms/60_000;ms%=60_000;long seconds=ms/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d,%03d",hours,minutes,seconds,ms%1000);}
}
