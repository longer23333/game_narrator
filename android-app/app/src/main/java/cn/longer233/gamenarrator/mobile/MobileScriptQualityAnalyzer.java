package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MobileScriptQualityAnalyzer {
    private MobileScriptQualityAnalyzer(){ }
    public static final class Segment {private final String key,subtitle,narration,effectCue;private final long durationMs;public Segment(String key,long durationMs,String subtitle,String narration,String effectCue){this.key=key;this.durationMs=durationMs;this.subtitle=safe(subtitle);this.narration=safe(narration);this.effectCue=safe(effectCue);}public String key(){return key;}}
    public static final class Issue {private final String clipKey,message;Issue(String clipKey,String message){this.clipKey=clipKey;this.message=message;}public String clipKey(){return clipKey;}public String message(){return message;}}
    public static final class Result {private final int score;private final List<Issue> issues;Result(int score,List<Issue> issues){this.score=score;this.issues=List.copyOf(issues);}public int score(){return score;}public boolean passed(){return issues.isEmpty();}public List<Issue> issues(){return issues;}public String summary(){return passed()?"文案结构完整":"文案需要人工复核";}}

    private static final class VisualKeyword {
        private final String text;
        private final String labelFragment;
        VisualKeyword(String text,String labelFragment){this.text=text;this.labelFragment=labelFragment.toLowerCase(Locale.CHINA);}
    }

    private static final List<VisualKeyword> VISUAL_KEYWORDS=List.of(
            new VisualKeyword("人物","person"),new VisualKeyword("人","person"),
            new VisualKeyword("汽车","car"),new VisualKeyword("轿车","car"),new VisualKeyword("车","car"),
            new VisualKeyword("卡车","truck"),new VisualKeyword("货车","truck"),
            new VisualKeyword("公交车","bus"),new VisualKeyword("巴士","bus"),
            new VisualKeyword("摩托车","motorcycle"),new VisualKeyword("摩托","motorcycle"),
            new VisualKeyword("自行车","bicycle"),new VisualKeyword("单车","bicycle"),
            new VisualKeyword("飞机","airplane"),new VisualKeyword("客机","airliner"),
            new VisualKeyword("船","boat"),new VisualKeyword("轮船","boat"),
            new VisualKeyword("火车","train"),new VisualKeyword("列车","train"),
            new VisualKeyword("山","mountain"),new VisualKeyword("森林","forest"),new VisualKeyword("树","tree"),
            new VisualKeyword("水","water"),new VisualKeyword("湖","lake"),new VisualKeyword("海","sea"),
            new VisualKeyword("猫","cat"),new VisualKeyword("狗","dog"),new VisualKeyword("鸟","bird"),
            new VisualKeyword("马","horse"),new VisualKeyword("牛","cow"),new VisualKeyword("羊","sheep"),
            new VisualKeyword("花","flower"),new VisualKeyword("建筑","building"),new VisualKeyword("大楼","skyscraper"),
            new VisualKeyword("街道","street"),new VisualKeyword("马路","street"),new VisualKeyword("公路","road"),
            new VisualKeyword("手机","telephone"),new VisualKeyword("电脑","computer"),new VisualKeyword("笔记本","notebook"),
            new VisualKeyword("键盘","keyboard"),new VisualKeyword("鼠标","mouse"),new VisualKeyword("屏幕","screen"),
            new VisualKeyword("球","ball"),new VisualKeyword("篮球","basketball"),new VisualKeyword("足球","football"),
            new VisualKeyword("书","book"),new VisualKeyword("食物","food"),new VisualKeyword("菜","food"),
            new VisualKeyword("乐器","instrument"),new VisualKeyword("吉他","guitar"),new VisualKeyword("钢琴","piano"),
            new VisualKeyword("帽子","hat"),new VisualKeyword("眼镜","glasses"),new VisualKeyword("手表","watch"));

    public static Result analyze(List<Segment> segments){
        List<Issue> issues=new ArrayList<>();Map<String,String> seenNarration=new HashMap<>(),seenSubtitle=new HashMap<>();
        for(Segment segment:segments){
            if(segment.narration.isBlank())issues.add(new Issue(segment.key,"解说文案为空"));
            if(segment.subtitle.isBlank())issues.add(new Issue(segment.key,"字幕为空"));
            if(segment.effectCue.isBlank())issues.add(new Issue(segment.key,"特效提示为空"));
            if(!segment.narration.isBlank()&&segment.narration.length()>Math.max(1,segment.durationMs/1000d)*5)issues.add(new Issue(segment.key,"文案长度可能超过当前镜头的可配音时长"));
            if(hasMojibake(segment.narration)||hasMojibake(segment.subtitle)||hasMojibake(segment.effectCue))issues.add(new Issue(segment.key,"文本包含明显乱码字符"));
            duplicate(issues,seenNarration,normalize(segment.narration),segment.key,"解说文案与其他分镜重复");duplicate(issues,seenSubtitle,normalize(segment.subtitle),segment.key,"字幕与其他分镜重复");
        }
        Set<String> unique=new HashSet<>();List<Issue> deduplicated=new ArrayList<>();for(Issue issue:issues)if(unique.add(issue.clipKey+"\n"+issue.message))deduplicated.add(issue);
        return new Result(Math.max(0,100-deduplicated.size()*10),deduplicated);
    }
    private static void duplicate(List<Issue> issues,Map<String,String> seen,String value,String key,String message){if(value.length()<4)return;String previous=seen.putIfAbsent(value,key);if(previous!=null&&!previous.equals(key)){issues.add(new Issue(previous,message));issues.add(new Issue(key,message));}}
    private static boolean hasMojibake(String value){return value.contains("�")||value.contains("锟斤拷")||value.contains("\u0000");}
    private static String normalize(String value){return value.toLowerCase(Locale.CHINA).replaceAll("[\\p{P}\\p{Z}\\s]+","");}
    private static String safe(String value){return value==null?"":value.trim();}

    /**
     * Lightweight offline visual consistency check: when the narration names a
     * common visual object, the detected frame labels should contain a matching
     * category. This is a heuristic hint, not semantic understanding.
     */
    public static List<Issue> visualConsistency(String clipKey, String narration, List<String> detectedLabels){
        List<Issue> issues=new ArrayList<>();
        String text=narration==null?"":narration.toLowerCase(Locale.CHINA);
        if(text.isBlank())return issues;
        String joined=String.join(" ",detectedLabels==null?List.of():detectedLabels).toLowerCase(Locale.CHINA);
        for(VisualKeyword keyword:VISUAL_KEYWORDS){
            if(text.contains(keyword.text)&&!joined.contains(keyword.labelFragment)){
                issues.add(new Issue(clipKey,"画面识别未找到与“"+keyword.text+"”对应的对象，事实一致性需要人工复核"));
            }
        }
        return issues;
    }
}
