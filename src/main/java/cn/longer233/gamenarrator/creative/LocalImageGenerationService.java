package cn.longer233.gamenarrator.creative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import cn.longer233.gamenarrator.identity.CurrentUserContext;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LocalImageGenerationService {
    private final ObjectMapper mapper; private final JdbcTemplate jdbc; private final CurrentUserContext user; private final Path root;
    private final String endpoint; private final String model; private final int defaultSteps; private final ComfyUiImageClient comfy;
    public LocalImageGenerationService(ObjectMapper mapper, JdbcTemplate jdbc, CurrentUserContext user,
            @Value("${game-narrator.image-generation.storage-root:${game-narrator.storage-root}}") String root,
            @Value("${game-narrator.image-generation.endpoint:}") String endpoint,
            @Value("${game-narrator.image-generation.model:sd15-turbo}") String model,
            @Value("${game-narrator.image-generation.default-steps:8}") int defaultSteps) {
        this.mapper=mapper; this.jdbc=jdbc; this.user=user; this.root=Path.of(root).toAbsolutePath().normalize(); this.endpoint=endpoint; this.model=model; this.defaultSteps=defaultSteps; this.comfy=endpoint.contains(":8188")?new ComfyUiImageClient(mapper,endpoint):null;
    }
    public GeneratedImageView generate(GeneratedImageRequest r) {
        if (endpoint==null || endpoint.isBlank()) return new GeneratedImageView(null,r.kind(),model, r.seed()==null?-1:r.seed(),false,"未配置本地图片生成引擎（兼容AUTOMATIC1111 / SD WebUI API）");
        try {
            long seed=r.seed()==null?new Random().nextLong(1_000_000_000L):r.seed();
            Map<String,Object> body=new LinkedHashMap<>(); body.put("prompt",r.prompt()); body.put("negative_prompt",r.negativePrompt()==null?"":r.negativePrompt()); body.put("width",r.width()==null?768:r.width()); body.put("height",r.height()==null?768:r.height()); body.put("steps",r.steps()==null?defaultSteps:r.steps()); body.put("seed",seed); body.put("override_settings",Map.of("sd_model_checkpoint",model));
            List<Map<String,Object>> controls=new ArrayList<>(); addControl(controls,r.characterReferenceAssetId(),"reference_only"); addControl(controls,r.styleReferenceAssetId(),"style_transfer");
            if(!controls.isEmpty()) body.put("alwayson_scripts",Map.of("controlnet",Map.of("args",controls)));
            byte[] bytes;
            if (comfy != null) bytes = comfy.generate(model, r.prompt(), Objects.toString(r.negativePrompt(),""), r.width()==null?768:r.width(), r.height()==null?768:r.height(), r.steps()==null?defaultSteps:r.steps(), seed);
            else { HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint.replaceAll("/$","")+"/sdapi/v1/txt2img")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(); HttpResponse<String> resp=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString()); if(resp.statusCode()/100!=2) throw new IllegalStateException("图片引擎HTTP "+resp.statusCode()); JsonNode image=mapper.readTree(resp.body()).path("images").path(0); if(image.isMissingNode()) throw new IllegalStateException("图片引擎未返回图像"); bytes=Base64.getDecoder().decode(image.asText().replaceFirst("^data:image/[^;]+;base64,","")); }
            UUID id=UUID.randomUUID(); Path dir=root.resolve("library").resolve(id.toString()).normalize(); if(!dir.startsWith(root)) throw new IllegalStateException("存储目录无效"); Files.createDirectories(dir); Path file=dir.resolve("generated-"+r.kind().toLowerCase(Locale.ROOT)+".png").normalize(); Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
            String metadata=mapper.writeValueAsString(Map.of("kind",r.kind(),"prompt",r.prompt(),"negativePrompt",Objects.toString(r.negativePrompt(),""),"model",model,"seed",seed,"characterReferenceAssetId",Objects.toString(r.characterReferenceAssetId(),""),"styleReferenceAssetId",Objects.toString(r.styleReferenceAssetId(),""),"sourceUrl",Objects.toString(r.sourceUrl(),"")));
            jdbc.update("INSERT INTO external_asset(id,provider,external_id,asset_type,title,creator,landing_url,license_code,attribution,local_path,import_status,metadata_json,discovered_at,downloaded_at) VALUES(?,'LOCAL_AI_GENERATED',?,?,?,?,?,'AI_GENERATED',?,?, 'DOWNLOADED',?,?,?)",id,id.toString(),"IMAGE","AI "+r.kind(),"本地图片生成模型","/api/assets/"+id+"/preview",r.sourceAttribution()==null?"AI生成；请核对模型和参考素材许可":r.sourceAttribution(),file.toString(),metadata,OffsetDateTime.now(),OffsetDateTime.now());
            jdbc.update("MERGE INTO user_external_asset(user_id,asset_id,favorite,archived,added_at) KEY(user_id,asset_id) VALUES(?,?,FALSE,FALSE,?)",user.userId(),id,OffsetDateTime.now()); return new GeneratedImageView(id,r.kind(),model,seed,true,"生成完成");
        } catch(Exception e){ throw new IllegalStateException("本地图片生成失败："+e.getMessage(),e); }
    }
    private void addControl(List<Map<String,Object>> controls, UUID assetId, String module) {
        if(assetId==null) return;
        String path=jdbc.queryForObject("SELECT local_path FROM external_asset WHERE id=?",String.class,assetId);
        if(path==null || path.isBlank()) throw new IllegalArgumentException("参考素材尚未下载："+assetId);
        Path file=Path.of(path).toAbsolutePath().normalize(); if(!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("参考素材路径不安全："+assetId);
        try { controls.add(Map.of("enabled",true,"module",module,"model","control_v11p_sd15_ip2p","weight",0.72,"input_image",Base64.getEncoder().encodeToString(Files.readAllBytes(file)),"resize_mode","Crop and Resize","lowvram",true)); } catch(Exception e){ throw new IllegalStateException("读取参考素材失败",e); }
    }
    public Map<String,Object> capabilities() {
        if(endpoint==null || endpoint.isBlank()) return Map.of("configured",false,"engine","AUTOMATIC1111","recommendedModel","sd15-turbo","vramTargetMb",7000,"message","未配置图片生成端点");
        try { HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint.replaceAll("/$","")+"/sdapi/v1/sd-models")).timeout(java.time.Duration.ofSeconds(4)).GET().build(); HttpResponse<String> resp=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString()); return Map.of("configured",true,"reachable",resp.statusCode()/100==2,"engine","AUTOMATIC1111","recommendedModel",model,"vramTargetMb",7000,"httpStatus",resp.statusCode()); } catch(Exception e){ return Map.of("configured",true,"reachable",false,"engine","AUTOMATIC1111","recommendedModel",model,"vramTargetMb",7000,"message",e.getMessage()); }
    }
    public List<GeneratedImageView> memeCandidates(MemeCandidateRequest r) {
        String caption = r.caption()==null || r.caption().isBlank() ? r.context() : r.caption();
        List<String> styles = List.of("reaction meme, expressive face, bold composition", "surreal game meme, dramatic reaction", "minimal image macro, clear subject and empty caption space");
        List<GeneratedImageView> result = new ArrayList<>();
        for (int i=0;i<styles.size();i++) result.add(generate(new GeneratedImageRequest("MEME_CANDIDATE", styles.get(i)+", context: "+r.context()+", caption: "+caption, "blurry, unreadable text, watermark", null, null, 768, 768, defaultSteps, (long)i, r.sourceUrl(), r.sourceAttribution()==null?"Meme候选基于用户提供语境；来源需人工复核":r.sourceAttribution())));
        return result;
    }
}
