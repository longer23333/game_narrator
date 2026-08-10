package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import cn.longer233.gamenarrator.identity.CurrentUserContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

@Service
public class AiUsageService {
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
        double cost=((input-cached)*Math.max(0,inputPrice)+output*Math.max(0,outputPrice)+
                cached*Math.max(0,cachedPrice))/1_000_000d;
        sessionInput+=input; sessionOutput+=output; sessionCached+=cached; sessionCost+=cost;
        DailyUsage daily=readDaily();
        if(!LocalDate.now().toString().equals(daily.date())) daily=DailyUsage.empty();
        daily=new DailyUsage(LocalDate.now().toString(),daily.input()+input,daily.output()+output,
                daily.cached()+cached,daily.cost()+cost);
        writeDaily(daily);
        recordDatabase(provider, model, input, output, cached, cost);
        last=new UsageSnapshot(input,output,sessionInput,sessionOutput,sessionCached,cachePercent(sessionInput,sessionCached),
                cost,daily.cost(),provider,model);
    }

    private void recordDatabase(String provider, String model, long input, long output, long cached, double cost) {
        if (jdbc == null || currentUser == null) return;
        String safeProvider = provider == null || provider.isBlank() ? "UNKNOWN" : provider;
        String safeModel = model == null || model.isBlank() ? "UNKNOWN" : model;
        int changed = jdbc.update("UPDATE user_ai_usage_daily SET input_tokens=input_tokens+?,output_tokens=output_tokens+?,cached_tokens=cached_tokens+?,estimated_cost=estimated_cost+?,request_count=request_count+1 WHERE user_id=? AND usage_date=? AND provider=? AND model_name=?",
                input, output, cached, java.math.BigDecimal.valueOf(cost), currentUser.userId(), LocalDate.now(), safeProvider, safeModel);
        if (changed == 0) jdbc.update("INSERT INTO user_ai_usage_daily(user_id,usage_date,provider,model_name,input_tokens,output_tokens,cached_tokens,estimated_cost,request_count) VALUES(?,?,?,?,?,?,?,?,1)",
                currentUser.userId(), LocalDate.now(), safeProvider, safeModel, input, output, cached, java.math.BigDecimal.valueOf(cost));
    }

    public synchronized UsageSnapshot snapshot() {
        DailyUsage daily=readDaily();
        double today=LocalDate.now().toString().equals(daily.date())?daily.cost():0;
        return new UsageSnapshot(last.turnInput(),last.turnOutput(),sessionInput,sessionOutput,sessionCached,
                cachePercent(sessionInput,sessionCached),last.turnCost(),today,last.provider(),last.model());
    }

    private double cachePercent(long input,long cached) { return input==0?0:cached*100d/input; }
    private DailyUsage readDaily() {
        try { if(Files.isRegularFile(file)) return mapper.readValue(file.toFile(),DailyUsage.class); }
        catch(Exception ignored) { }
        return DailyUsage.empty();
    }
    private void writeDaily(DailyUsage value) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary=Files.createTempFile(file.getParent(),"ai-usage-",".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),value);
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(Exception unsupported) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
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
