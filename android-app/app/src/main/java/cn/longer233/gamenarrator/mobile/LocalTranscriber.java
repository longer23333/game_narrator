package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/**
 * On-device speech-to-text via the system recognizer. Recognition is held in
 * memory only; transcripts are not persisted by this class.
 */
public final class LocalTranscriber {
    public interface Listener {
        void onPartial(String text);
        void onResult(String text);
        void onError(String message);
    }

    private final SpeechRecognizer recognizer;
    private final Listener listener;

    private LocalTranscriber(SpeechRecognizer recognizer, Listener listener) {
        this.recognizer = recognizer;
        this.listener = listener;
    }

    public static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public static LocalTranscriber start(Context context, Listener listener) {
        if (!isAvailable(context)) throw new IllegalStateException("系统语音识别不可用");
        SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        LocalTranscriber transcriber = new LocalTranscriber(recognizer, listener);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                listener.onError(errorMessage(error));
                transcriber.destroy();
            }
            @Override public void onResults(Bundle results) {
                String text = bestText(results);
                if (!text.isBlank()) listener.onResult(text);
                transcriber.destroy();
            }
            @Override public void onPartialResults(Bundle partialResults) {
                String text = bestText(partialResults);
                if (!text.isBlank()) listener.onPartial(text);
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizer.startListening(intent);
        return transcriber;
    }

    public void destroy() {
        try {
            recognizer.destroy();
        } catch (Exception ignored) { }
    }

    private static String bestText(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return matches == null || matches.isEmpty() ? "" : matches.get(0);
    }

    private static String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "录音失败";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "缺少麦克风权限";
            case SpeechRecognizer.ERROR_NETWORK: return "需要网络或离线语音包";
            case SpeechRecognizer.ERROR_NO_MATCH: return "没有识别到语音";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "听写超时";
            default: return "语音识别错误 " + error;
        }
    }
}
