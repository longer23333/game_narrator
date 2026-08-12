package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Greedy GPT-2 text generation over the bundled ONNX decoder. Requires the
 * ONNX file plus vocab.json / merges.txt in the same model directory.
 */
public final class Gpt2OnnxGenerator {
    private static final int MAX_TOKENS = 64;
    private static final int EOS_TOKEN = 50256;

    private Gpt2OnnxGenerator() { }

    public static String generate(Context context, String prompt) throws Exception {
        File dir = MobileModelDirectory.modelsDir(context);
        File vocab = new File(dir, "vocab.json");
        File merges = new File(dir, "merges.txt");
        if (!vocab.isFile() || !merges.isFile()) {
            throw new IllegalStateException("缺少 GPT-2 tokenizer 文件");
        }
        Gpt2Tokenizer tokenizer;
        try (FileInputStream vocabInput = new FileInputStream(vocab);
             FileInputStream mergesInput = new FileInputStream(merges)) {
            tokenizer = new Gpt2Tokenizer(vocabInput, mergesInput);
        }
        List<Integer> ids = new ArrayList<>();
        for (int id : tokenizer.encode(prompt)) ids.add(id);

        OrtSession session = OnnxModelRunner.cachedChecked(context, "text");
        {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            for (int step = 0; step < MAX_TOKENS; step++) {
                long[] inputIds = toLongArray(ids);
                long[] attention = new long[inputIds.length];
                Arrays.fill(attention, 1L);
                Map<String, OnnxTensor> inputs = new HashMap<>();
                Set<String> names = session.getInputNames();
                for (String name : names) {
                    String lower = name.toLowerCase();
                    if (lower.contains("input")) {
                        inputs.put(name, OnnxTensor.createTensor(env, new long[][]{inputIds}));
                    } else if (lower.contains("attention")) {
                        inputs.put(name, OnnxTensor.createTensor(env, new long[][]{attention}));
                    }
                }
                try {
                    OrtSession.Result result = session.run(inputs);
                    try {
                    float[][][] logits = null;
                    for (Map.Entry<String, ai.onnxruntime.OnnxValue> entry : result) {
                        if (entry.getKey().toLowerCase().contains("logit")) {
                            logits = (float[][][]) ((OnnxTensor) entry.getValue()).getValue();
                        }
                    }
                    if (logits == null) throw new IllegalStateException("未找到 logits 输出");
                    float[] last = logits[0][logits[0].length - 1];
                    int next = argmax(last);
                    ids.add(next);
                    if (next == EOS_TOKEN) break;
                    } finally {
                        result.close();
                    }
                } finally {
                    OnnxModelRunner.closeTensors(inputs);
                }
            }
            return tokenizer.decode(toIntArray(ids));
        }
    }

    private static int argmax(float[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    private static long[] toLongArray(List<Integer> values) {
        long[] out = new long[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }
}
