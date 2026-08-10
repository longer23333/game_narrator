package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Byte-level BPE tokenizer for GPT-2 ONNX models. Reads vocab.json and
 * merges.txt from the same model directory as the ONNX file.
 */
public final class Gpt2Tokenizer {
    private static final Pattern WORD_PATTERN = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");

    private final Map<String, Integer> encoder = new HashMap<>();
    private final String[] decoder;
    private final Map<String, Integer> merges = new HashMap<>();
    private final Map<Integer, Integer> byteEncoder = new HashMap<>();
    private final Map<Integer, Integer> byteDecoder = new HashMap<>();

    public Gpt2Tokenizer(InputStream vocabJson, InputStream mergesTxt) throws IOException {
        buildByteMaps();
        String vocabText = readAll(vocabJson);
        try {
            JSONObject vocab = new JSONObject(vocabText);
            int size = vocab.length();
            decoder = new String[size];
            Iterator<String> keys = vocab.keys();
            while (keys.hasNext()) {
                String token = keys.next();
                int id = vocab.getInt(token);
                encoder.put(token, id);
                decoder[id] = token;
            }
        } catch (JSONException error) {
            throw new IOException("无法解析 vocab.json", error);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(mergesTxt, StandardCharsets.UTF_8));
        String line;
        int rank = 0;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) continue;
            String[] parts = line.split(" ");
            if (parts.length == 2) merges.put(parts[0] + " " + parts[1], rank++);
        }
    }

    public int[] encode(String text) {
        List<Integer> ids = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String encoded = byteEncode(matcher.group());
            for (String symbol : bpe(encoded)) {
                Integer id = encoder.get(symbol);
                if (id != null) ids.add(id);
            }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) out[i] = ids.get(i);
        return out;
    }

    public String decode(int[] ids) {
        StringBuilder builder = new StringBuilder();
        for (int id : ids) {
            if (id >= 0 && id < decoder.length) builder.append(decoder[id]);
        }
        String encoded = builder.toString();
        byte[] bytes = new byte[encoded.length()];
        for (int i = 0; i < encoded.length(); i++) {
            Integer value = byteDecoder.get((int) encoded.charAt(i));
            bytes[i] = value == null ? (byte) encoded.charAt(i) : value.byteValue();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<String> bpe(String token) {
        List<String> word = new ArrayList<>();
        for (int i = 0; i < token.length(); i++) word.add(token.substring(i, i + 1));
        while (word.size() > 1) {
            int bestRank = Integer.MAX_VALUE;
            String bestPair = null;
            for (int i = 0; i < word.size() - 1; i++) {
                String pair = word.get(i) + " " + word.get(i + 1);
                Integer rank = merges.get(pair);
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestPair = pair;
                }
            }
            if (bestPair == null) break;
            String merged = bestPair.replace(" ", "");
            List<String> next = new ArrayList<>();
            for (int i = 0; i < word.size(); i++) {
                if (i < word.size() - 1 && (word.get(i) + " " + word.get(i + 1)).equals(bestPair)) {
                    next.add(merged);
                    i++;
                } else {
                    next.add(word.get(i));
                }
            }
            word = next;
        }
        return word;
    }

    private String byteEncode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int value = b & 0xFF;
            Integer mapped = byteEncoder.get(value);
            builder.append((char) (mapped == null ? value : mapped));
        }
        return builder.toString();
    }

    private void buildByteMaps() {
        List<Integer> bytes = new ArrayList<>();
        List<Integer> chars = new ArrayList<>();
        for (int i = '!'; i <= '~'; i++) { bytes.add(i); chars.add(i); }
        for (int i = 0xA1; i <= 0xAC; i++) { bytes.add(i); chars.add(i); }
        for (int i = 0xAE; i <= 0xFF; i++) { bytes.add(i); chars.add(i); }
        Set<Integer> existing = new HashSet<>(bytes);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if (!existing.contains(b)) {
                bytes.add(b);
                chars.add(256 + n);
                n++;
            }
        }
        for (int i = 0; i < bytes.size(); i++) {
            byteEncoder.put(bytes.get(i), chars.get(i));
            byteDecoder.put(chars.get(i), bytes.get(i));
        }
    }

    private static String readAll(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        return builder.toString();
    }
}
