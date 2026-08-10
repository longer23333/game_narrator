package cn.longer233.gamenarrator.mobile;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * On-device image labeling through ML Kit. The default model ships with the
 * SDK, needs no API key and never leaves the device.
 */
public final class LocalVisionAnalyzer {
    private static final class Holder {
        static final ImageLabeler LABELER = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
    }

    public interface Listener {
        void onResult(List<String> labels);
        void onError(String message);
    }

    private LocalVisionAnalyzer() { }

    public static boolean isAvailable() {
        return true;
    }

    public static void label(Bitmap bitmap, Listener listener) {
        if (bitmap == null) {
            listener.onError("无法读取画面");
            return;
        }
        Holder.LABELER.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(results -> {
                    List<ImageLabel> sorted = new ArrayList<>(results);
                    sorted.sort(Comparator.comparingDouble(ImageLabel::getConfidence).reversed());
                    List<String> labels = new ArrayList<>();
                    int count = Math.min(5, sorted.size());
                    for (int i = 0; i < count; i++) {
                        labels.add(sorted.get(i).getText() + " (" + Math.round(sorted.get(i).getConfidence() * 100) + "%)");
                    }
                    listener.onResult(labels);
                })
                .addOnFailureListener(error -> listener.onError(error.getMessage() == null ? "画面识别失败" : error.getMessage()));
    }

}
