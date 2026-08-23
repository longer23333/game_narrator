package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.DefaultGainProvider;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.work.WorkManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@UnstableApi
public final class ExportController {
    public interface Listener {
        void onExportStarted(String dialogTitle, String label, long jobId, File output);
        void onProgress(int percent);
        void onCompleted(File output);
        void onError(String message);
        void onCancelled();
        void onStatus(String message);
        void showError(String title, String message);
        void unavailable(String message);
        void log(String source, String message);
    }

    public static final class Preset {
        final String label;
        final String mimeType;
        final String ffmpegKind;
        final String ffmpegFilter;
        final int bitrate;
        final int width;
        final int height;
        final float frameRate;

        public Preset(String label, int bitrate, int width, int height, float frameRate, String mimeType) {
            this(label, bitrate, width, height, frameRate, mimeType, null);
        }

        public Preset(String label, int bitrate, int width, int height, float frameRate, String mimeType,
                      String ffmpegKind) {
            this(label, bitrate, width, height, frameRate, mimeType, ffmpegKind, null);
        }

        public Preset(String label, int bitrate, int width, int height, float frameRate, String mimeType,
                      String ffmpegKind, String ffmpegFilter) {
            this.label = label;
            this.bitrate = bitrate;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.mimeType = mimeType;
            this.ffmpegKind = ffmpegKind;
            this.ffmpegFilter = ffmpegFilter;
        }
    }

    private final Context context;
    private final List<TimelineClip> clips;
    private final MobileProjectStore projectStore;
    private final Handler handler;
    private final Listener listener;
    private final ExportManager manager;
    private long jobId = -1;
    private int lastProgress = -1;
    private boolean workerMode;
    private long workerJobId = -1;
    private long workerProjectId = -1;
    private File workerOutput;
    private boolean workerPolling;
    private String pendingFfmpegKind;
    private File finalOutput;
    private String pendingFfmpegFilter;

    public ExportController(Context context, List<TimelineClip> clips, MobileProjectStore projectStore,
                            Handler handler, Listener listener) {
        this.context = context;
        this.clips = clips;
        this.projectStore = projectStore;
        this.handler = handler;
        this.listener = listener;
        this.manager = new ExportManager(handler, new ExportManager.Listener() {
            @Override public void onStateChanged(ExportStateMachine.State state) { }
            @Override public void onProgress(int percent) {
                listener.onProgress(percent);
                if (percent != lastProgress) {
                    lastProgress = percent;
                    projectStore.updateExport(jobId, "PROCESSING", percent, "");
                }
            }
            @Override public void onCompleted(File file) {
                if (pendingFfmpegKind != null) {
                    File intermediate = file;
                    File finalFile = finalOutput;
                    String kind = pendingFfmpegKind;
                    String filter = pendingFfmpegFilter;
                    pendingFfmpegKind = null;
                    pendingFfmpegFilter = null;
                    finalOutput = null;
                    projectStore.updateExport(jobId, "PROCESSING", 95, "FFmpeg 转码中");
                    listener.onProgress(95);
                    new Thread(() -> {
                        try {
                            if (filter != null) {
                                FfmpegRunner.transcodeWithFilter(context, intermediate, finalFile, filter);
                            } else if ("PRO_RES".equals(kind)) {
                                FfmpegRunner.transcodeToProRes(context, intermediate, finalFile);
                            } else {
                                FfmpegRunner.transcodeToMov(context, intermediate, finalFile);
                            }
                            projectStore.updateExport(jobId, "COMPLETED", 100, "");
                            projectStore.updateStatus("COMPLETED");
                            if (intermediate.isFile()) intermediate.delete();
                            handler.post(() -> listener.onCompleted(finalFile));
                        } catch (Exception error) {
                            String message = error.getMessage() == null ? "FFmpeg 转码失败" : error.getMessage();
                            projectStore.updateExport(jobId, "FAILED", 0, message);
                            projectStore.updateStatus("FAILED");
                            handler.post(() -> listener.onError(message));
                        }
                    }).start();
                    return;
                }
                projectStore.updateExport(jobId, "COMPLETED", 100, "");
                projectStore.updateStatus("COMPLETED");
                listener.onCompleted(file);
            }
            @Override public void onError(String message) {
                projectStore.updateExport(jobId, "FAILED", 0, message);
                projectStore.updateStatus("FAILED");
                listener.onError(message);
            }
            @Override public void onCancelled(File file) {
                listener.onCancelled();
            }
        });
    }

    public boolean isActive() {
        return workerMode || manager.isActive();
    }

    public long activeJobId() { return workerMode ? workerJobId : manager.jobId(); }
    public File activeOutput() { return workerMode ? workerOutput : manager.output(); }
    public ExportStateMachine.State state() { return manager.state(); }

    public void cancel() {
        if (workerMode) {
            long cancelledJob = workerJobId;
            listener.log("export", "取消后台导出任务 " + cancelledJob);
            projectStore.updateExport(cancelledJob, "CANCELLED", 0, "用户取消");
            projectStore.updateStatus("CANCELLED");
            WorkManager.getInstance(context).cancelUniqueWork(ExportScheduler.uniqueName(workerProjectId));
            stopWorkerPolling();
            workerMode = false;
            listener.onCancelled();
            return;
        }
        if (!manager.isActive()) return;
        long cancelledJob = manager.jobId();
        listener.log("export", "取消导出任务 " + cancelledJob);
        projectStore.updateExport(cancelledJob, "CANCELLED", 0, "用户取消");
        projectStore.updateStatus("CANCELLED");
        manager.cancel();
        if (manager.output() != null && manager.output().isFile() && !manager.output().delete()) {
            listener.onStatus("导出已取消；半成品暂时无法删除。");
        } else {
            listener.onStatus("导出已取消。");
        }
    }

    public void cancelForegroundOnly() {
        if (!workerMode) cancel();
    }

    public void startProjectExport(Preset preset) {
        if (isActive()) {
            listener.unavailable("已有导出任务正在运行。");
            return;
        }
        if (clips.isEmpty()) {
            listener.unavailable("请先导入视频。");
            return;
        }
        listener.log("export", "开始导出：" + preset.label);
        if (PipelineStages.index(projectStore.pipelineStage()) < PipelineStages.index(PipelineStages.RENDER)) {
            projectStore.setPipelineStage(PipelineStages.RENDER);
        }
        Composition composition = buildProjectComposition(context, projectStore, preset, clips);
        startCommon(composition, preset, "正在导出项目");
    }

    public void startBackgroundProjectExport(Preset preset) {
        if (isActive()) {
            listener.unavailable("已有导出任务正在运行。");
            return;
        }
        if (clips.isEmpty()) {
            listener.unavailable("请先导入视频。");
            return;
        }
        listener.log("export", "开始后台导出：" + preset.label);
        if (PipelineStages.index(projectStore.pipelineStage()) < PipelineStages.index(PipelineStages.RENDER)) {
            projectStore.setPipelineStage(PipelineStages.RENDER);
        }
        File movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (movies == null) {
            listener.unavailable("设备没有可用的影片目录。");
            return;
        }
        File result = new File(movies, "GameNarrator-" + System.currentTimeMillis() + ".mp4");
        projectStore.updateStatus("PROCESSING");
        long startedJob = projectStore.startExport(result.getAbsolutePath(), preset.label);
        workerMode = true;
        workerJobId = startedJob;
        workerProjectId = projectStore.activeProjectId();
        workerOutput = result;
        lastProgress = -1;
        listener.onExportStarted("正在后台导出", preset.label, startedJob, result);
        try {
            ExportScheduler.enqueue(context, workerProjectId, startedJob, result, preset);
        } catch (Exception error) {
            String message = error == null || error.getMessage() == null ? "未知导出错误" : error.getMessage();
            projectStore.updateExport(startedJob, "FAILED", 0, message);
            projectStore.updateStatus("FAILED");
            workerMode = false;
            listener.onError(message);
            return;
        }
        startWorkerPolling();
    }

    static Composition buildProjectComposition(Context context, MobileProjectStore projectStore,
                                               Preset preset, List<TimelineClip> clips) {
        List<Long> mainVideoStarts = ExportTimelinePlan.mainVideoStartMs(clips);
        List<EditedMediaItem> items = new ArrayList<>();
        java.util.Map<Integer, Integer> clipToItem = new java.util.HashMap<>();
        List<MobileAssetStore.PlacementInfo> placements = projectStore.listPlacements();
        List<ProjectRepository.SubtitleCueInfo> subtitleCues = projectStore.listSubtitleCues();
        ProjectRepository.TrackState originalTrack = projectStore.trackState("ORIGINAL");
        ProjectRepository.TrackState narrationTrackState = projectStore.trackState("NARRATION");
        ProjectRepository.TrackState musicTrack = projectStore.trackState("MUSIC");
        boolean anySolo = originalTrack.solo() || narrationTrackState.solo() || musicTrack.solo();
        for (int clipIndex = 0; clipIndex < clips.size(); clipIndex++) {
            TimelineClip clip = clips.get(clipIndex);
            if (!"V1".equals(clip.track())) continue;
            long subtitleTimelineStartMs = mainVideoStarts.get(items.size());
            MediaItem media = mediaFor(clip);
            clipToItem.put(clipIndex, items.size());
            items.add(new EditedMediaItem.Builder(media)
                    .setRemoveAudio(clip.muted() || originalTrack.muted() || (anySolo && !originalTrack.solo()))
                    .setEffects(MobileRenderEffects.forClip(context, clip, placements, subtitleCues,
                            projectStore.clipAudioConfig(clip.key()), projectStore.clipVisualConfig(clip.key()),
                            projectStore.listKeyframes(clip.key(), "scale"),
                            projectStore.listKeyframes(clip.key(), "rotation"),
                            projectStore.listKeyframes(clip.key(), "x"),
                            projectStore.listKeyframes(clip.key(), "y"),
                            projectStore.listKeyframes(clip.key(), "opacity"),
                            projectStore.listKeyframes(clip.key(), "volume"),
                            subtitleTimelineStartMs, preset.width, preset.height, preset.frameRate))
                    .build());
        }
        if (items.isEmpty()) {
            long subtitleTimelineStartMs = 0;
            for (int clipIndex = 0; clipIndex < clips.size(); clipIndex++) {
                TimelineClip clip = clips.get(clipIndex);
                MediaItem media = mediaFor(clip);
                clipToItem.put(clipIndex, items.size());
                items.add(new EditedMediaItem.Builder(media)
                        .setRemoveAudio(clip.muted() || originalTrack.muted() || (anySolo && !originalTrack.solo()))
                        .setEffects(MobileRenderEffects.forClip(context, clip, placements, subtitleCues,
                                projectStore.clipAudioConfig(clip.key()), projectStore.clipVisualConfig(clip.key()),
                                projectStore.listKeyframes(clip.key(), "scale"),
                                projectStore.listKeyframes(clip.key(), "rotation"),
                                projectStore.listKeyframes(clip.key(), "x"),
                                projectStore.listKeyframes(clip.key(), "y"),
                                projectStore.listKeyframes(clip.key(), "opacity"),
                                projectStore.listKeyframes(clip.key(), "volume"),
                                subtitleTimelineStartMs, preset.width, preset.height, preset.frameRate))
                        .build());
                subtitleTimelineStartMs += clip.durationMs();
            }
        }
        List<EditedMediaItemSequence> sequences = new ArrayList<>();
        sequences.add(new EditedMediaItemSequence.Builder(Set.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
                .addItems(items).build());
        long clipStartMs = 0;
        int videoOverlayCount = 0;
        List<MobileAssetStore.PlacementInfo> videoOverlays = new ArrayList<>();
        for (TimelineClip clip : clips) {
            for (MobileAssetStore.PlacementInfo placement : placements) {
                if (!placement.clipKey().equals(clip.key())) continue;
                Uri assetUri = Uri.parse(placement.uri());
                long sourceDurationMs = readDurationSilently(context, assetUri);
                long mixDurationMs = sourceDurationMs <= 0 ? clip.durationMs() : Math.min(sourceDurationMs, clip.durationMs());
                MediaItem assetMedia = new MediaItem.Builder().setUri(assetUri).setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(mixDurationMs).build()).build();
                if (placement.type().startsWith("video/")) {
                    EditedMediaItem videoItem = new EditedMediaItem.Builder(assetMedia).setRemoveAudio(true).build();
                    EditedMediaItemSequence.Builder videoSequence = new EditedMediaItemSequence.Builder(
                            Collections.singleton(C.TRACK_TYPE_VIDEO));
                    if (clipStartMs > 0) videoSequence.addGap(clipStartMs * 1000);
                    videoSequence.addItem(videoItem);
                    sequences.add(videoSequence.build());
                    videoOverlays.add(placement);
                    videoOverlayCount++;
                    continue;
                }
                if (!placement.type().startsWith("audio/")) continue;
                ProjectRepository.TrackState placementTrack = "NARRATION".equals(placement.role())
                        ? narrationTrackState : musicTrack;
                if (placementTrack.muted() || (anySolo && !placementTrack.solo())) continue;
                long durationUs = mixDurationMs * 1000;
                DefaultGainProvider.Builder gain = new DefaultGainProvider.Builder(placement.volume());
                long fadeInUs = Math.min(durationUs, placement.fadeInMs() * 1000);
                long fadeOutUs = Math.min(durationUs, placement.fadeOutMs() * 1000);
                if (fadeInUs > 0) gain.addFadeAt(0, fadeInUs, DefaultGainProvider.FADE_IN_EQUAL_POWER);
                if (fadeOutUs > 0) gain.addFadeAt(Math.max(0, durationUs - fadeOutUs), fadeOutUs, DefaultGainProvider.FADE_OUT_EQUAL_POWER);
                Effects audioEffects = new Effects(Collections.singletonList(new GainProcessor(gain.build())),
                        Collections.emptyList());
                EditedMediaItem audioItem = new EditedMediaItem.Builder(assetMedia).setRemoveVideo(true)
                        .setEffects(audioEffects).build();
                EditedMediaItemSequence.Builder audioSequence = new EditedMediaItemSequence.Builder(
                        Collections.singleton(C.TRACK_TYPE_AUDIO));
                if (clipStartMs > 0) audioSequence.addGap(clipStartMs * 1000);
                audioSequence.addItem(audioItem);
                sequences.add(audioSequence.build());
            }
            clipStartMs += clip.durationMs();
        }
        List<Integer> v2Inputs = new ArrayList<>();
        long v2TimelineMs = 0;
        for (TimelineClip clip : clips) {
            long clipEnd = v2TimelineMs + clip.durationMs();
            if ("V2".equals(clip.track())) {
                MediaItem v2Media = mediaFor(clip);
                EditedMediaItem v2Item = new EditedMediaItem.Builder(v2Media).setRemoveAudio(true)
                        .setEffects(MobileRenderEffects.forClip(context, clip, placements, subtitleCues,
                                projectStore.clipAudioConfig(clip.key()), projectStore.clipVisualConfig(clip.key()),
                                projectStore.listKeyframes(clip.key(), "scale"),
                                projectStore.listKeyframes(clip.key(), "rotation"),
                                projectStore.listKeyframes(clip.key(), "x"),
                                projectStore.listKeyframes(clip.key(), "y"),
                                projectStore.listKeyframes(clip.key(), "opacity"),
                                projectStore.listKeyframes(clip.key(), "volume"),
                                0, preset.width, preset.height, preset.frameRate))
                        .build();
                EditedMediaItemSequence.Builder v2Sequence = new EditedMediaItemSequence.Builder(
                        Collections.singleton(C.TRACK_TYPE_VIDEO));
                if (v2TimelineMs > 0) v2Sequence.addGap(v2TimelineMs * 1000);
                v2Sequence.addItem(v2Item);
                sequences.add(v2Sequence.build());
                v2Inputs.add(videoOverlays.size() + 1 + v2Inputs.size());
            }
            v2TimelineMs = clipEnd;
        }
        long audioTimelineMs = 0;
        for (TimelineClip clip : clips) {
            if (!"A1".equals(clip.track())) {
                audioTimelineMs += clip.durationMs();
                continue;
            }
            if (clip.muted() || originalTrack.muted() || (anySolo && !originalTrack.solo())) {
                audioTimelineMs += clip.durationMs();
                continue;
            }
            MediaItem audioMedia = mediaFor(clip);
            long durationUs = clip.durationMs() * 1000;
            ProjectRepository.ClipAudioConfig audio = projectStore.clipAudioConfig(clip.key());
            DefaultGainProvider.Builder gain = new DefaultGainProvider.Builder(audio.volume());
            long fadeInUs = Math.min(durationUs, audio.fadeInMs() * 1000);
            long fadeOutUs = Math.min(durationUs, audio.fadeOutMs() * 1000);
            if (fadeInUs > 0) gain.addFadeAt(0, fadeInUs, DefaultGainProvider.FADE_IN_EQUAL_POWER);
            if (fadeOutUs > 0) gain.addFadeAt(Math.max(0, durationUs - fadeOutUs), fadeOutUs, DefaultGainProvider.FADE_OUT_EQUAL_POWER);
            Effects audioEffects = new Effects(Collections.singletonList(new GainProcessor(gain.build())),
                    Collections.emptyList());
            EditedMediaItem audioItem = new EditedMediaItem.Builder(audioMedia).setRemoveVideo(true)
                    .setEffects(audioEffects).build();
            EditedMediaItemSequence.Builder audioSequence = new EditedMediaItemSequence.Builder(
                    Collections.singleton(C.TRACK_TYPE_AUDIO));
            if (audioTimelineMs > 0) audioSequence.addGap(audioTimelineMs * 1000);
            audioSequence.addItem(audioItem);
            sequences.add(audioSequence.build());
            audioTimelineMs += clip.durationMs();
        }
        List<Integer> crossfadeInputs = new ArrayList<>();
        List<Long> crossfadeStarts = new ArrayList<>();
        List<Long> crossfadeDurations = new ArrayList<>();
        int nextCrossfadeInput = videoOverlays.size() + 1 + v2Inputs.size();
        long crossTimelineMs = 0;
        for (int i = 0; i < clips.size(); i++) {
            long clipEndMs = crossTimelineMs + clips.get(i).durationMs();
            if (i + 1 < clips.size() && EffectCueUtil.hasCrossfade(clips.get(i).effectCue())) {
                long overlap = Math.max(50, Math.min(500, Math.min(clips.get(i).durationMs(), clips.get(i + 1).durationMs()) / 4));
                long gapMs = Math.max(0, clipEndMs - overlap);
                EditedMediaItemSequence.Builder crossSequence = new EditedMediaItemSequence.Builder(
                        Collections.singleton(C.TRACK_TYPE_VIDEO));
                if (gapMs > 0) crossSequence.addGap(gapMs * 1000);
                Integer baseIndex = clipToItem.get(i + 1);
                if (baseIndex == null || baseIndex < 0 || baseIndex >= items.size()) continue;
                crossSequence.addItem(items.get(baseIndex));
                sequences.add(crossSequence.build());
                crossfadeInputs.add(nextCrossfadeInput++);
                crossfadeStarts.add(gapMs * 1000);
                crossfadeDurations.add(overlap * 1000);
            }
            crossTimelineMs = clipEndMs;
        }
        Composition.Builder compositionBuilder = new Composition.Builder(sequences);
        if (videoOverlayCount > 0 || !crossfadeInputs.isEmpty() || !v2Inputs.isEmpty()) {
            compositionBuilder.setVideoCompositorSettings(crossfadeInputs.isEmpty() && v2Inputs.isEmpty()
                    ? new MobileVideoCompositorSettings(videoOverlays)
                    : new CrossfadeVideoCompositorSettings(videoOverlays, crossfadeInputs, crossfadeStarts, crossfadeDurations));
        }
        Composition composition = compositionBuilder.build();
        return composition;
    }

    public void startClipExport(TimelineClip clip, Preset preset) {
        if (isActive()) {
            listener.unavailable("已有导出任务正在运行。");
            return;
        }
        if (clip == null) return;
        listener.log("export", "开始导出镜头片段：" + preset.label);
        if (PipelineStages.index(projectStore.pipelineStage()) < PipelineStages.index(PipelineStages.RENDER)) {
            projectStore.setPipelineStage(PipelineStages.RENDER);
        }
        List<MobileAssetStore.PlacementInfo> placements = new ArrayList<>();
        for (MobileAssetStore.PlacementInfo placement : projectStore.listPlacements()) {
            if (placement.clipKey().equals(clip.key())) placements.add(placement);
        }
        List<ProjectRepository.SubtitleCueInfo> subtitleCues = projectStore.listSubtitleCues();
        ProjectRepository.TrackState originalTrack = projectStore.trackState("ORIGINAL");
        ProjectRepository.TrackState narrationTrackState = projectStore.trackState("NARRATION");
        ProjectRepository.TrackState musicTrack = projectStore.trackState("MUSIC");
        boolean anySolo = originalTrack.solo() || narrationTrackState.solo() || musicTrack.solo();
        MediaItem media = mediaFor(clip);
        List<EditedMediaItem> items = new ArrayList<>();
        items.add(new EditedMediaItem.Builder(media)
                .setRemoveAudio(clip.muted() || originalTrack.muted() || (anySolo && !originalTrack.solo()))
                .setEffects(MobileRenderEffects.forClip(context, clip, placements, subtitleCues,
                        projectStore.clipAudioConfig(clip.key()), projectStore.clipVisualConfig(clip.key()),
                        projectStore.listKeyframes(clip.key(), "scale"),
                        projectStore.listKeyframes(clip.key(), "rotation"),
                        projectStore.listKeyframes(clip.key(), "x"),
                        projectStore.listKeyframes(clip.key(), "y"),
                        projectStore.listKeyframes(clip.key(), "opacity"),
                        projectStore.listKeyframes(clip.key(), "volume"),
                        0, preset.width, preset.height, preset.frameRate))
                .build());
        List<EditedMediaItemSequence> sequences = new ArrayList<>();
        sequences.add(new EditedMediaItemSequence.Builder(Set.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
                .addItems(items).build());
        int videoOverlayCount = 0;
        List<MobileAssetStore.PlacementInfo> videoOverlays = new ArrayList<>();
        for (MobileAssetStore.PlacementInfo placement : placements) {
            Uri assetUri = Uri.parse(placement.uri());
            long sourceDurationMs = readDurationSilently(context, assetUri);
            long mixDurationMs = sourceDurationMs <= 0 ? clip.durationMs() : Math.min(sourceDurationMs, clip.durationMs());
            MediaItem assetMedia = new MediaItem.Builder().setUri(assetUri).setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(mixDurationMs).build()).build();
            if (placement.type().startsWith("video/")) {
                EditedMediaItem videoItem = new EditedMediaItem.Builder(assetMedia).setRemoveAudio(true).build();
                sequences.add(new EditedMediaItemSequence.Builder(Collections.singleton(C.TRACK_TYPE_VIDEO))
                        .addItem(videoItem).build());
                videoOverlays.add(placement);
                videoOverlayCount++;
                continue;
            }
            if (!placement.type().startsWith("audio/")) continue;
            ProjectRepository.TrackState placementTrack = "NARRATION".equals(placement.role())
                    ? narrationTrackState : musicTrack;
            if (placementTrack.muted() || (anySolo && !placementTrack.solo())) continue;
            long durationUs = mixDurationMs * 1000;
            DefaultGainProvider.Builder gain = new DefaultGainProvider.Builder(placement.volume());
            long fadeInUs = Math.min(durationUs, placement.fadeInMs() * 1000);
            long fadeOutUs = Math.min(durationUs, placement.fadeOutMs() * 1000);
            if (fadeInUs > 0) gain.addFadeAt(0, fadeInUs, DefaultGainProvider.FADE_IN_EQUAL_POWER);
            if (fadeOutUs > 0) gain.addFadeAt(Math.max(0, durationUs - fadeOutUs), fadeOutUs, DefaultGainProvider.FADE_OUT_EQUAL_POWER);
            Effects audioEffects = new Effects(Collections.singletonList(new GainProcessor(gain.build())),
                    Collections.emptyList());
            EditedMediaItem audioItem = new EditedMediaItem.Builder(assetMedia).setRemoveVideo(true)
                    .setEffects(audioEffects).build();
            sequences.add(new EditedMediaItemSequence.Builder(Collections.singleton(C.TRACK_TYPE_AUDIO))
                    .addItem(audioItem).build());
        }
        Composition.Builder compositionBuilder = new Composition.Builder(sequences);
        if (videoOverlayCount > 0) compositionBuilder.setVideoCompositorSettings(new MobileVideoCompositorSettings(videoOverlays));
        Composition composition = compositionBuilder.build();
        startCommon(composition, preset, "正在导出当前镜头");
    }

    private void startCommon(Composition composition, Preset preset, String dialogTitle) {
        File movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (movies == null) {
            listener.unavailable("设备没有可用的影片目录。");
            return;
        }
        File result;
        File intermediate = null;
        if (preset.ffmpegKind != null) {
            String ext = preset.ffmpegFilter != null ? "-curves.mp4"
                    : "PRO_RES".equals(preset.ffmpegKind) ? "-prores.mov" : ".mov";
            result = new File(movies, "GameNarrator-" + System.currentTimeMillis() + ext);
            intermediate = new File(movies, "GameNarrator-" + System.currentTimeMillis() + "-intermediate.mp4");
        } else {
            result = new File(movies, "GameNarrator-" + System.currentTimeMillis() + ".mp4");
        }
        projectStore.updateStatus("PROCESSING");
        long startedJob = projectStore.startExport(result.getAbsolutePath(), preset.label);
        this.jobId = startedJob;
        this.lastProgress = -1;
        this.pendingFfmpegKind = preset.ffmpegKind;
        this.pendingFfmpegFilter = preset.ffmpegFilter;
        this.finalOutput = preset.ffmpegKind != null ? result : null;
        listener.onExportStarted(dialogTitle, preset.label, startedJob, result);
        manager.reset();
        try {
            DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(preset.bitrate).build())
                    .setEnableFallback(true).build();
            Transformer built = new Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setVideoMimeType(preset.mimeType)
                    .setEncoderFactory(encoderFactory)
                    .build();
            manager.start(built, composition, intermediate != null ? intermediate : result, startedJob);
        } catch (Exception error) {
            String message = error == null || error.getMessage() == null ? "未知导出错误" : error.getMessage();
            listener.log("export", "导出启动失败：" + message);
            projectStore.updateExport(startedJob, "FAILED", 0, message);
            projectStore.updateStatus("FAILED");
            if (manager.state() == ExportStateMachine.State.IDLE) {
                listener.onError(message);
            } else {
                manager.abort(message);
            }
            if (result.isFile() && !result.delete()) {
                listener.onStatus("导出失败；半成品暂时无法删除。");
            }
        }
    }

    private void startWorkerPolling() {
        workerPolling = true;
        handler.post(new Runnable() {
            @Override public void run() {
                if (!workerPolling) return;
                MobileExportStore.ExportJobInfo job = findJob(workerJobId);
                if (job == null) {
                    handler.postDelayed(this, 500);
                    return;
                }
                if ("PROCESSING".equals(job.status())) {
                    if (job.progress() != lastProgress) {
                        lastProgress = job.progress();
                        listener.onProgress(job.progress());
                    }
                    handler.postDelayed(this, 500);
                    return;
                }
                workerMode = false;
                workerPolling = false;
                if ("COMPLETED".equals(job.status())) {
                    listener.onProgress(100);
                    listener.onCompleted(new File(job.outputPath()));
                } else if ("CANCELLED".equals(job.status())) {
                    listener.onCancelled();
                } else {
                    listener.onError(job.error().isBlank() ? "导出失败" : job.error());
                }
            }
        });
    }

    private void stopWorkerPolling() {
        workerPolling = false;
    }

    private MobileExportStore.ExportJobInfo findJob(long id) {
        for (MobileExportStore.ExportJobInfo job : projectStore.listExports()) {
            if (job.id() == id) return job;
        }
        return null;
    }

    private static MediaItem mediaFor(TimelineClip clip) {
        return new MediaItem.Builder().setUri(clip.uri()).setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs())
                        .setEndPositionMs(clip.endMs()).build()).build();
    }

    private static long readDurationSilently(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        } finally {
            try { retriever.release(); } catch (java.io.IOException ignored) { }
        }
    }
}
