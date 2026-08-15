package cn.longer233.gamenarrator.task.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VideoTaskTest {

    @Test
    void newTaskCreatesTheCompleteNineStageWorkflow() {
        VideoTask task = new VideoTask(
                "Boss 战高光",
                "ACTION",
                CommentaryStyle.ANIME_THEATER,
                90,
                "突出极限闪避与反杀",
                "storage/demo.mp4"
        );

        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(task.getStages()).hasSize(9);
        assertThat(task.getStages().getFirst().getStageType())
                .isEqualTo(ProcessingStageType.VIDEO_INGESTION);
        assertThat(task.getStages().getLast().getStageType())
                .isEqualTo(ProcessingStageType.RENDERING);
    }

    @Test
    void retryKeepsCompletedStagesAndResetsOnlyTheFailedStage() {
        VideoTask task = new VideoTask(
                "Retry demo",
                "ACTION",
                CommentaryStyle.ANIME_THEATER,
                90,
                "Retry from the failed stage",
                "storage/demo.mp4"
        );
        task.startIngestion();
        task.completeIngestion(60, 1920, 1080, 30, "h264", "aac");
        task.startSceneDetection();
        task.failSceneDetection("ffmpeg failed");

        assertThat(task.retryStage()).isEqualTo(ProcessingStageType.SCENE_DETECTION);
        task.prepareRetry();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(task.getFailureReason()).isNull();
        assertThat(task.getStages().get(0).getStatus()).isEqualTo(StageStatus.COMPLETED);
        assertThat(task.getStages().get(1).getStatus()).isEqualTo(StageStatus.PENDING);
        assertThat(task.getStages().get(1).getErrorMessage()).isNull();
    }

    @Test
    void retryRejectsTasksThatHaveNotFailed() {
        VideoTask task = new VideoTask(
                "Ready demo",
                "ACTION",
                CommentaryStyle.ANIME_THEATER,
                90,
                "No retry needed",
                "storage/demo.mp4"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(task::prepareRetry)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a failed or cancelled task can be retried");
    }

    @Test
    void cancelledTaskCanResumeFromItsInterruptedStage() {
        VideoTask task = new VideoTask("Cancel", "ACTION", CommentaryStyle.ANIME_THEATER,
                90, "brief", "storage/demo.mp4");
        task.startSceneDetection();

        task.cancel("用户取消了任务");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(task.getStages().get(1).getStatus()).isEqualTo(StageStatus.PENDING);
        task.prepareRetry();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(task.getStages().get(1).getErrorMessage()).isNull();
    }

    @Test
    void longToolErrorsAreTruncatedSoFailureStateCanAlwaysBePersisted() {
        VideoTask task = new VideoTask("Failure", "ACTION", CommentaryStyle.ANIME_THEATER,
                90, "brief", "storage/demo.mp4");
        task.startSceneDetection();
        task.failSceneDetection("FFmpeg error ".repeat(200));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getFailureReason()).hasSizeLessThanOrEqualTo(900).endsWith("...");
        assertThat(task.getStages().get(1).getErrorMessage()).hasSizeLessThanOrEqualTo(900);
    }

    @Test
    void detailedProgressKeepsExactWorkUnitAndClearsItAtTerminalState() {
        VideoTask task = new VideoTask("Progress", "ACTION", CommentaryStyle.ANIME_THEATER,
                90, "brief", "storage/demo.mp4");
        task.startVideoUnderstanding();

        task.updateStageProgress(ProcessingStageType.VIDEO_UNDERSTANDING,
                42, "FRAME", 7, 18, "事件窗口二次检测");
        ProcessingStage stage = task.getStages().stream()
                .filter(item -> item.getStageType() == ProcessingStageType.VIDEO_UNDERSTANDING)
                .findFirst().orElseThrow();

        assertThat(stage.getProgress()).isEqualTo(42);
        assertThat(stage.getSubprogressUnit()).isEqualTo("FRAME");
        assertThat(stage.getSubprogressCurrent()).isEqualTo(7);
        assertThat(stage.getSubprogressTotal()).isEqualTo(18);
        assertThat(stage.getSubprogressDetail()).isEqualTo("事件窗口二次检测");

        task.completeVideoUnderstanding("summary", "analysis.json", 18);
        assertThat(stage.getSubprogressUnit()).isNull();
        assertThat(stage.getSubprogressCurrent()).isNull();
        assertThat(stage.getSubprogressDetail()).isNull();
    }

    @Test
    void renameTrimsAndValidatesTheDisplayName() {
        VideoTask task = new VideoTask("Old", "ACTION", CommentaryStyle.ANIME_THEATER,
                90, "brief", "storage/demo.mp4");

        task.rename("  New name  ");

        assertThat(task.getName()).isEqualTo("New name");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> task.rename("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storyboardReviewPausesAfterScriptUntilExplicitApproval() {
        VideoTask task = new VideoTask("Review", "ACTION", CommentaryStyle.ANIME_THEATER,
                90, "brief", "storage/demo.mp4", true);
        task.startScriptGeneration();
        task.completeScriptGeneration("title", "synopsis", "narration", "script.json", 1);

        task.awaitStoryboardReview();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.WAITING_REVIEW);
        assertThat(task.isStoryboardApproved()).isFalse();
        task.approveStoryboard();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(task.isStoryboardApproved()).isTrue();
    }

    @Test
    void confirmedEventChangeInvalidatesScriptVoiceTimelineAndRender() {
        VideoTask task = new VideoTask("Event edit", "ACTION", CommentaryStyle.ANIME_THEATER,
                90, "brief", "storage/demo.mp4");
        task.startScriptGeneration();
        task.completeScriptGeneration("title", "synopsis", "narration", "script.json", 1);
        task.startVoiceGeneration();
        task.completeVoiceGeneration("voice.json", 1);
        task.startTimelinePlanning();
        task.completeTimelinePlanning("timeline.json", 20, 0);
        task.startRendering();
        task.completeRendering("output.mp4", "subtitle.ass", 100);

        task.invalidateAfterConfirmedEventChange();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(task.isAwaitingScriptRegeneration()).isTrue();
        assertThat(task.getStages().stream()
                .filter(stage -> stage.getStageType().ordinal() >= ProcessingStageType.SCRIPT_GENERATION.ordinal()))
                .allMatch(stage -> stage.getStatus() == StageStatus.PENDING);
    }
}
