package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;

/**
 * Assembles the timeline dialog action groups and delegates each public entry
 * point to the class that owns that domain.
 */
@UnstableApi
public final class TimelineTaskDialogController {
    private final TimelineTrackActions tracks;
    private final TimelineSubtitleActions subtitles;
    private final TimelineClipActions clips;
    private final TimelineKeyframeActions keyframes;
    private final TimelinePlacementActions placements;
    private final TimelineEffectActions effects;
    private final TimelineNarrationActions narration;

    public TimelineTaskDialogController(Context context, DialogController ui) {
        tracks = new TimelineTrackActions(context, ui);
        subtitles = new TimelineSubtitleActions(context, ui);
        clips = new TimelineClipActions(context, ui);
        keyframes = new TimelineKeyframeActions(context, ui);
        placements = new TimelinePlacementActions(context, ui);
        effects = new TimelineEffectActions(context, ui);
        narration = new TimelineNarrationActions(context, ui);
    }

    public void attachTrackHost(DialogController.TrackHost host) { tracks.attach(host); }
    public void attachSubtitleHost(DialogController.SubtitleHost host) { subtitles.attach(host); }
    public void attachEditingHost(DialogController.EditingHost host) { clips.attach(host); }
    public void attachKeyframeHost(DialogController.KeyframeHost host) { keyframes.attach(host); }
    public void attachPlacementHost(DialogController.PlacementHost host) { placements.attach(host); }
    public void attachTemplateHost(DialogController.TemplateHost host) { effects.attach(host); }
    public void attachNarrationHost(DialogController.NarrationHost host) { narration.attach(host); }

    public void showTrackAssignment() { tracks.showTrackAssignment(); }
    public void showTrackControls() { tracks.showTrackControls(); }
    public void showSubtitleFiles() { subtitles.showSubtitleFiles(); }
    public void editSubtitleCue(int index) { subtitles.editSubtitleCue(index); }
    public void openClipPanel() { clips.openClipPanel(); }
    public void showClipVisualAdjustments() { clips.showClipVisualAdjustments(); }
    public void showKeyframes() { keyframes.showKeyframes(); }
    public void showPositionKeyframes() { keyframes.showPositionKeyframes(); }
    public void showOpacityKeyframes() { keyframes.showOpacityKeyframes(); }
    public void showVolumeKeyframes() { keyframes.showVolumeKeyframes(); }
    public void showKeyframeCurveEditor() { keyframes.showKeyframeCurveEditor(); }
    public void addKeyframeDialog(TimelineClip clip, String property) { keyframes.addKeyframeDialog(clip, property); }
    public void openAssetPlacement() { placements.openAssetPlacement(); }
    public void configurePlacement(MobileAssetStore.PlacementInfo placement) { placements.configurePlacement(placement); }
    public void showEffectTemplates() { effects.showEffectTemplates(); }
    public void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template) { effects.applyEffectTemplate(template); }
    public void saveCurrentEffectTemplate() { effects.saveCurrentEffectTemplate(); }
    public void synthesizeNarration() { narration.synthesizeNarration(); }
}
