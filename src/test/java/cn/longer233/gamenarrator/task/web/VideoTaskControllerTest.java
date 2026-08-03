package cn.longer233.gamenarrator.task.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.longer233.gamenarrator.pipeline.VideoTaskEngine;
import cn.longer233.gamenarrator.pipeline.PipelineRunTracker;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:controller-test",
        "game-narrator.storage-root=./target/test-storage",
        "game-narrator.media-import.yt-dlp=./mvnw.cmd"
})
@AutoConfigureMockMvc
class VideoTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PipelineRunTracker pipelineRunTracker;

    @MockBean
    private VideoTaskEngine videoTaskEngine;

    @Test
    void emptyAssetCatalogCanBeListed() throws Exception {
        mockMvc.perform(get("/api/assets").param("query", "悬疑"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void publicAssetDiscoveryRejectsInvalidPageBeforeCallingProviders() throws Exception {
        mockMvc.perform(post("/api/assets/discover")
                        .contentType("application/json")
                        .content("""
                                {"query":"battle","assetType":"VIDEO","pageSize":12,"page":0,
                                 "commercialUse":true,"allowModification":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicAssetDiscoveryRejectsUnknownProviderBeforeCallingProviders() throws Exception {
        mockMvc.perform(post("/api/assets/discover")
                        .contentType("application/json")
                        .content("""
                                {"query":"battle","assetType":"VIDEO","pageSize":12,"page":1,
                                 "commercialUse":true,"allowModification":true,"provider":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicAssetDiscoveryRejectsUnknownSortBeforeCallingProviders() throws Exception {
        mockMvc.perform(post("/api/assets/discover")
                        .contentType("application/json")
                        .content("""
                                {"query":"battle","assetType":"VIDEO","pageSize":12,"page":1,
                                 "commercialUse":true,"allowModification":true,"provider":"BILIBILI",
                                 "sort":"RANDOM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mediaImporterReportsInstalledRuntime() throws Exception {
        mockMvc.perform(get("/api/media-import/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void browserStyleMultipartFormCreatesTask() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "boss-fight.mp4",
                "video/mp4",
                "fake-video-for-controller-test".getBytes()
        );

        var result = mockMvc.perform(multipart("/api/tasks")
                        .file(video)
                        .characterEncoding("UTF-8")
                        .param("name", "Boss 战高光")
                        .param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90")
                        .param("taskBrief", "突出闪避、反击和阶段转换"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.name").value("Boss 战高光"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.stages.length()").value(9))
                .andReturn();

        String taskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video_project WHERE id=?",
                Integer.class, UUID.fromString(taskId)));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_revision revision
                JOIN video_project project ON project.current_revision_id=revision.id
                WHERE project.id=? AND revision.project_id=project.id
                """, Integer.class, UUID.fromString(taskId)));
        mockMvc.perform(get("/api/tasks/{id}/source", taskId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=source-" + taskId))
                .andExpect(content().bytes("fake-video-for-controller-test".getBytes()));
    }

    @Test
    void invalidVideoReturnsReadableErrorAndTraceId() throws Exception {
        MockMultipartFile invalidVideo = new MockMultipartFile(
                "video",
                "notes.txt",
                "text/plain",
                "not-a-video".getBytes()
        );

        mockMvc.perform(multipart("/api/tasks")
                        .file(invalidVideo)
                        .characterEncoding("UTF-8")
                        .param("name", "错误样例")
                        .param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90")
                        .param("taskBrief", "验证错误输出"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("仅支持 mp4、mov、mkv、webm 视频"))
                .andExpect(jsonPath("$.suggestion").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void existingTaskCanBeDeleted() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "delete-me.mp4", "video/mp4",
                "temporary-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "待删除任务").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "删除测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());
        Path taskDirectory = Path.of("target/test-storage/tasks").resolve(id.toString());
        Files.createDirectories(taskDirectory.resolve("render-work"));
        Files.writeString(taskDirectory.resolve("render-work/temporary.mp4"), "temporary");

        mockMvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video_tasks WHERE id=?",
                Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM processing_stages WHERE task_id=?",
                Integer.class, id));
        assertEquals(false, Files.exists(taskDirectory));
    }

    @Test
    void taskRenameAlsoUpdatesItsProjectName() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "rename.mp4", "video/mp4",
                "temporary-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "旧任务名").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "重命名测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());

        mockMvc.perform(patch("/api/tasks/{id}/name", id)
                        .contentType("application/json").content("{\"name\":\" 新任务名 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新任务名"));

        assertEquals("新任务名", jdbc.queryForObject("SELECT name FROM video_project WHERE id=?",
                String.class, id));
    }

    @Test
    void pipelineStagesAreMirroredIntoVersionedRunTables() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "tracked.mp4", "video/mp4",
                "tracked-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "运行留痕").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "阶段记录测试"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());

        pipelineRunTracker.running(id, "VIDEO_INGESTION");
        pipelineRunTracker.completed(id, "VIDEO_INGESTION", java.util.Map.of("durationSeconds", 12));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM generation_run WHERE project_id=?",
                Integer.class, id));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM stage_run WHERE generation_run_id=(SELECT latest_run_id FROM video_project WHERE id=?)",
                String.class, id));
    }

    @Test
    void taskStatusConstraintAllowsStoryboardReviewWaitingState() throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "review.mp4", "video/mp4",
                "review-video".getBytes());
        var created = mockMvc.perform(multipart("/api/tasks").file(video)
                        .param("name", "Storyboard review").param("gameCategory", "ACTION")
                        .param("commentaryStyle", "ANIME_THEATER")
                        .param("targetDurationSeconds", "90").param("taskBrief", "Review generated storyboard"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText());

        assertEquals(1, jdbc.update("UPDATE video_tasks SET status='WAITING_REVIEW' WHERE id=?", id));
        assertEquals("WAITING_REVIEW", jdbc.queryForObject(
                "SELECT status FROM video_tasks WHERE id=?", String.class, id));
    }

}
