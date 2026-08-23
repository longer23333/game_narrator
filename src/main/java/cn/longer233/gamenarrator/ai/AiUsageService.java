package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.common.AtomicArtifactWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AiUsageService {
    private static final BigDecimal MAX_DATABASE_COST=new BigDecimal("9999999999.99999999");
    private static final double MAX_COST=9_999_999_999d;
    private final ObjectMapper mapper;
    private final Path file;
    private long sessionInput;
    private long sessionOutput;
    private long sessionCached;
    private double sessionCost;
    private UsageSnapshot last = UsageSnapshot.empty();
    private final JdbcTemplate jdbc;
    private final CurrentUserContext currentUser;

    public AiUsageService(ObjectMapper mapper,
            @Value("${game-narrator.data-root:${GAME_NARRATOR_DATA_ROOT:./data}}") String dataRoot) {
        this(mapper, dataRoot, null, null);
    }

    @Autowired
    public AiUsageService(ObjectMapper mapper,
            @Value("${game-narrator.data-root:${GAME_NARRATOR_DATA_ROOT:./data}}") String dataRoot,
            JdbcTemplate jdbc, CurrentUserContext currentUser) {
        this.mapper = mapper;
        this.file = Path.of(dataRoot).toAbsolutePath().normalize().resolve("config").resolve("ai-usage.json");
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    public synchronized void record(String provider, String model, long input, long output, long cached,
                                    double inputPrice, double outputPrice, double cachedPrice) {
        input=Math.max(0,input); output=Math.max(0,output); cached=Math.min(input,Math.max(0,cached));
        double cost=((input-cached)*finitePrice(inputPrice)+output*finitePrice(outputPrice)+
                cached*finitePrice(cachedPrice))/1_000_000d;
        if (!Double.isFinite(cost)) cost=MAX_COST;
        else cost=Math.min(MAX_COST,cost);
        sessionInput=saturatedAdd(sessionInput,input); sessionOutput=saturatedAdd(sessionOutput,output);
        sessionCached=Math.min(sessionInput,saturatedAdd(sessionCached,cached)); sessionCost=finiteAdd(sessionCost,cost);
        DailyUsage daily=readDaily();
        if(!LocalDate.now().toString().equals(daily.date())) daily=DailyUsage.empty();
        long dailyInput=saturatedAdd(daily.input(),input);
        daily=new DailyUsage(LocalDate.now().toString(),dailyInput,saturatedAdd(daily.output(),output),
                Math.min(dailyInput,saturatedAdd(daily.cached(),cached)),finiteAdd(daily.cost(),cost));
        writeDaily(daily);
        recordDatabase(provider, model, input, output, cached, cost);
        last=new UsageSnapshot(input,output,sessionInput,sessionOutput,sessionCached,cachePercent(sessionInput,sessionCached),
                cost,daily.cost(),provider,model);
    }

    private void recordDatabase(String provider, String model, long input, long output, long cached, double cost) {
        if (jdbc == null || currentUser == null) return;
        String safeProvider = databaseText(provider,"UNKNOWN",40);
        String safeModel = databaseText(model,"UNKNOWN",160);
        BigDecimal decimalCost=BigDecimal.valueOf(Math.min(MAX_COST,Math.max(0,cost)));
        int changed = jdbc.update("UPDATE user_ai_usage_daily SET input_tokens=CASE WHEN input_tokens>? THEN ? ELSE input_tokens+? END,output_tokens=CASE WHEN output_tokens>? THEN ? ELSE output_tokens+? END,cached_tokens=CASE WHEN cached_tokens>? THEN ? ELSE cached_tokens+? END,estimated_cost=CASE WHEN estimated_cost>? THEN ? ELSE estimated_cost+? END,request_count=CASE WHEN request_count=? THEN ? ELSE request_count+1 END WHERE user_id=? AND usage_date=? AND provider=? AND model_name=?",
                Long.MAX_VALUE-input,Long.MAX_VALUE,input,Long.MAX_VALUE-output,Long.MAX_VALUE,output,
                Long.MAX_VALUE-cached,Long.MAX_VALUE,cached,MAX_DATABASE_COST.subtract(decimalCost),MAX_DATABASE_COST,decimalCost,
                Long.MAX_VALUE,Long.MAX_VALUE,currentUser.userId(),LocalDate.now(),safeProvider,safeModel);
        if (changed == 0) jdbc.update("INSERT INTO user_ai_usage_daily(user_id,usage_date,provider,model_name,input_tokens,output_tokens,cached_tokens,estimated_cost,request_count) VALUES(?,?,?,?,?,?,?,?,1)",
                currentUser.userId(), LocalDate.now(), safeProvider, safeModel, input, output, cached, decimalCost);
    }

    public synchronized UsageSnapshot snapshot() {
        DailyUsage daily=readDaily();
        double today=LocalDate.now().toString().equals(daily.date())?daily.cost():0;
        return new UsageSnapshot(last.turnInput(),last.turnOutput(),sessionInput,sessionOutput,sessionCached,
                cachePercent(sessionInput,sessionCached),last.turnCost(),today,last.provider(),last.model());
    }

    private double cachePercent(long input,long cached) { return input==0?0:cached*100d/input; }
    private double finitePrice(double value) { return Double.isFinite(value)?Math.max(0,value):0; }
    private long saturatedAdd(long left,long right) {
        left=Math.max(0,left); right=Math.max(0,right);
        return left>Long.MAX_VALUE-right?Long.MAX_VALUE:left+right;
    }
    private double finiteAdd(double left,double right) {
        if(!Double.isFinite(left)||left<0) left=0;
        double result=left+right;
        return Double.isFinite(result)?Math.min(MAX_COST,result):MAX_COST;
    }
    private String databaseText(String value,String fallback,int maximumLength) {
        String normalized=value==null||value.isBlank()?fallback:value.trim();
        if (normalized.length()<=maximumLength) return normalized;
        String digest;
        try {
            digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)),0,16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 不可用",impossible);
        }
        int prefixLength=maximumLength-digest.length()-1;
        if (prefixLength>0 && Character.isHighSurrogate(normalized.charAt(prefixLength-1))) prefixLength--;
        return normalized.substring(0,Math.max(0,prefixLength))+"~"+digest;
    }
    private DailyUsage readDaily() {
        try {
            if(Files.isRegularFile(file)) {
                DailyUsage value=mapper.readValue(file.toFile(),DailyUsage.class);
                long input=Math.max(0,value.input());
                return new DailyUsage(value.date(),input,Math.max(0,value.output()),
                        Math.min(input,Math.max(0,value.cached())),finiteAdd(value.cost(),0));
            }
        }
        catch(Exception ignored) { }
        return DailyUsage.empty();
    }
    private void writeDaily(DailyUsage value) {
        try {
            AtomicArtifactWriter.writeJson(mapper,file,value);
        } catch(Exception exception) { throw new IllegalStateException("无法保存 AI 用量统计",exception); }
    }

    public record UsageSnapshot(long turnInput,long turnOutput,long sessionInput,long sessionOutput,long sessionCached,
                                double cachePercent,double turnCost,double todayCost,String provider,String model) {
        static UsageSnapshot empty(){return new UsageSnapshot(0,0,0,0,0,0,0,0,"","");}
    }
    private record DailyUsage(String date,long input,long output,long cached,double cost) {
        static DailyUsage empty(){return new DailyUsage(LocalDate.now().toString(),0,0,0,0);}
    }
}
