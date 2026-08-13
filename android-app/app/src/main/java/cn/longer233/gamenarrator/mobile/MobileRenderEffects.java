package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import androidx.media3.common.Effect;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.RgbAdjustment;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.FrameDropEffect;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Effects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@UnstableApi
public final class MobileRenderEffects {
    private MobileRenderEffects() { }

    public static Effects forClip(Context context, TimelineClip clip, List<MobileAssetStore.PlacementInfo> placements,List<ProjectRepository.SubtitleCueInfo> subtitleCues,ProjectRepository.ClipAudioConfig audioConfig,ProjectRepository.ClipVisualConfig visualConfig,List<ProjectRepository.KeyframeInfo> scaleKeyframes,List<ProjectRepository.KeyframeInfo> rotationKeyframes,List<ProjectRepository.KeyframeInfo> xKeyframes,List<ProjectRepository.KeyframeInfo> yKeyframes,List<ProjectRepository.KeyframeInfo> opacityKeyframes,List<ProjectRepository.KeyframeInfo> volumeKeyframes,long clipTimelineStartMs,int outputWidth,int outputHeight,float frameRate) {
        List<Effect> videoEffects = new ArrayList<>();
        if(outputWidth>0&&outputHeight>0)videoEffects.add(Presentation.createForWidthAndHeight(outputWidth,outputHeight,Presentation.LAYOUT_SCALE_TO_FIT));
        else if(outputHeight>0)videoEffects.add(Presentation.createForHeight(outputHeight));
        if(frameRate>0)videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(frameRate));
        if(Math.abs(visualConfig.brightness())>.001f)videoEffects.add(new Brightness(visualConfig.brightness()));
        if(Math.abs(visualConfig.contrast())>.001f)videoEffects.add(new Contrast(visualConfig.contrast()));
        if(Math.abs(visualConfig.temperature())>.001f){float shift=visualConfig.temperature()*.003f;videoEffects.add(new RgbAdjustment.Builder().setRedScale(1f+shift).setGreenScale(1f).setBlueScale(1f-shift).build());}
        if(Math.abs(visualConfig.hue())>.001f||Math.abs(visualConfig.saturation())>.001f)videoEffects.add(new HslAdjustment.Builder().adjustHue(visualConfig.hue()).adjustSaturation(visualConfig.saturation()).build());
        Effect lutEffect = CubeLutEffectFactory.fromPath(visualConfig.lutPath());
        if (lutEffect != null) videoEffects.add(lutEffect);
        if(!scaleKeyframes.isEmpty()||!rotationKeyframes.isEmpty()||!xKeyframes.isEmpty()||!yKeyframes.isEmpty())videoEffects.add(new KeyframedTransformEffect(scaleKeyframes,rotationKeyframes,xKeyframes,yKeyframes,visualConfig.scale(),visualConfig.rotation()));else if(Math.abs(visualConfig.scale()-1f)>.001f||Math.abs(visualConfig.rotation())>.001f)videoEffects.add(new ScaleAndRotateTransformation.Builder().setScale(visualConfig.scale(),visualConfig.scale()).setRotationDegrees(visualConfig.rotation()).build());
        if(!opacityKeyframes.isEmpty())videoEffects.add(new KeyframedAlphaEffect(opacityKeyframes));
        addCueEffects(videoEffects, clip.effectCue());
        if(clip.effectCue().contains("[淡入淡出]"))videoEffects.add(new FadeThroughBlackEffect(clip.durationMs(),Math.min(500,clip.durationMs()/4)));
        List<TextureOverlay> overlays = new ArrayList<>();
        List<ProjectRepository.SubtitleCueInfo> timedCues=new ArrayList<>();long clipTimelineEndMs=clipTimelineStartMs+clip.durationMs();
        for(ProjectRepository.SubtitleCueInfo cue:subtitleCues)if(cue.startMs()<clipTimelineEndMs&&cue.endMs()>clipTimelineStartMs)timedCues.add(cue);
        if(!timedCues.isEmpty())overlays.add(timedSubtitle(timedCues,clipTimelineStartMs));
        else if (!clip.subtitle().isBlank()) overlays.add(isDynamicSubtitle(clip.effectCue()) ? dynamicSubtitle(clip.subtitle()) : subtitle(clip.subtitle()));
        for (MobileAssetStore.PlacementInfo placement : placements) {
            if (!placement.clipKey().equals(clip.key()) || !placement.type().startsWith("image/")) continue;
            float x=placement.visualX(),y=placement.visualY(),scale=placement.visualScale();
            StaticOverlaySettings settings = new StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(x,y)
                    .setOverlayFrameAnchor(x>=0?1f:-1f,y>=0?1f:-1f)
                    .setScale(scale,scale)
                    .setAlphaScale(.96f)
                    .build();
            overlays.add(BitmapOverlay.createStaticBitmapOverlay(context, Uri.parse(placement.uri()), settings));
        }
        if (!overlays.isEmpty()) videoEffects.add(new OverlayEffect(overlays));
        boolean processAudio=audioConfig.volume()!=1f||audioConfig.fadeInMs()>0||audioConfig.fadeOutMs()>0||!volumeKeyframes.isEmpty();List<androidx.media3.common.audio.AudioProcessor> audioEffects=processAudio?Collections.singletonList(new GainProcessor(new KeyframedGainProvider(volumeKeyframes,audioConfig.volume(),clip.durationMs(),audioConfig.fadeInMs(),audioConfig.fadeOutMs()))):Collections.emptyList();
        return videoEffects.isEmpty()&&audioEffects.isEmpty()?Effects.EMPTY:new Effects(audioEffects,videoEffects);
    }

    /** The editor player and Transformer export call the same builder to prevent preview/export drift. */
    public static List<Effect> previewVideoEffects(ProjectRepository.ClipVisualConfig visualConfig) {
        List<Effect> effects = new ArrayList<>();
        if(Math.abs(visualConfig.brightness())>.001f)effects.add(new Brightness(visualConfig.brightness()));
        if(Math.abs(visualConfig.contrast())>.001f)effects.add(new Contrast(visualConfig.contrast()));
        if(Math.abs(visualConfig.temperature())>.001f){float shift=visualConfig.temperature()*.003f;effects.add(new RgbAdjustment.Builder().setRedScale(1f+shift).setGreenScale(1f).setBlueScale(1f-shift).build());}
        if(Math.abs(visualConfig.hue())>.001f||Math.abs(visualConfig.saturation())>.001f)effects.add(new HslAdjustment.Builder().adjustHue(visualConfig.hue()).adjustSaturation(visualConfig.saturation()).build());
        Effect lut = CubeLutEffectFactory.fromPath(visualConfig.lutPath());
        if (lut != null) effects.add(lut);
        if(Math.abs(visualConfig.scale()-1f)>.001f||Math.abs(visualConfig.rotation())>.001f)effects.add(new ScaleAndRotateTransformation.Builder().setScale(visualConfig.scale(),visualConfig.scale()).setRotationDegrees(visualConfig.rotation()).build());
        return List.copyOf(effects);
    }

    private static TextOverlay subtitle(String value) {
        SpannableString text = styledText(value);
        return TextOverlay.createStaticTextOverlay(text, subtitleSettings());
    }

    private static TextOverlay dynamicSubtitle(String value) {
        String clean=value.trim();
        StaticOverlaySettings settings=subtitleSettings();
        return new TextOverlay() {
            @Override public SpannableString getText(long presentationTimeUs) {
                int visible=Math.max(1,Math.min(clean.length(),(int)(presentationTimeUs/120_000)+1));
                return styledText(clean.substring(0,visible));
            }
            @Override public OverlaySettings getOverlaySettings(long presentationTimeUs){return settings;}
        };
    }

    private static TextOverlay timedSubtitle(List<ProjectRepository.SubtitleCueInfo> cues,long clipTimelineStartMs){
        StaticOverlaySettings settings=subtitleSettings();
        return new TextOverlay(){
            @Override public SpannableString getText(long presentationTimeUs){long globalMs=clipTimelineStartMs+presentationTimeUs/1000;for(ProjectRepository.SubtitleCueInfo cue:cues)if(globalMs>=cue.startMs()&&globalMs<cue.endMs())return styledText(cue.text());return styledText(" ");}
            @Override public OverlaySettings getOverlaySettings(long presentationTimeUs){return settings;}
        };
    }

    private static SpannableString styledText(String value) {
        SpannableString text = new SpannableString("  " + value.trim() + "  ");
        int end = text.length();
        text.setSpan(new ForegroundColorSpan(Color.WHITE), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new BackgroundColorSpan(Color.argb(220, 17, 17, 17)), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new AbsoluteSizeSpan(TextOverlay.TEXT_SIZE_PIXELS), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private static StaticOverlaySettings subtitleSettings() {
        return new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -.82f)
                .setOverlayFrameAnchor(0f, -1f)
                .setScale(.72f, .72f)
                .build();
    }

    private static boolean isDynamicSubtitle(String cue){String value=cue==null?"":cue.toLowerCase(Locale.CHINA);return value.contains("动态字幕")||value.contains("逐字字幕")||value.contains("karaoke");}

    private static void addCueEffects(List<Effect> effects, String rawCue) {
        String cue = rawCue == null ? "" : rawCue.toLowerCase(Locale.CHINA);
        if (cue.contains("高对比") || cue.contains("contrast")) effects.add(new Contrast(.35f));
        if (cue.contains("暖色") || cue.contains("warm")) effects.add(new HslAdjustment.Builder().adjustHue(8f).adjustSaturation(12f).adjustLightness(3f).build());
        if (cue.contains("冷色") || cue.contains("cool")) effects.add(new HslAdjustment.Builder().adjustHue(-12f).adjustSaturation(8f).build());
        if (cue.contains("黑白") || cue.contains("monochrome")) effects.add(new HslAdjustment.Builder().adjustSaturation(-100f).build());
        if (cue.contains("缩放") || cue.contains("zoom")) effects.add(new ScaleAndRotateTransformation.Builder().setScale(1.08f, 1.08f).build());
        if (cue.contains("旋转") || cue.contains("rotate")) effects.add(new ScaleAndRotateTransformation.Builder().setRotationDegrees(2f).build());
    }
}
