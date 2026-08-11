package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.ai.ModelAdapter;
import java.nio.file.Path;

public interface Transcriber extends ModelAdapter {
    TranscriptionResult transcribe(Path audioPath);
}
