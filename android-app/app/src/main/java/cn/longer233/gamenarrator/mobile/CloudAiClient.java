package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Real cloud AI calls for the configured provider. The API key stays in
 * encrypted settings and is only sent to the provider's endpoint over HTTPS.
 */
public final class CloudAiClient {
    private CloudAiClient() { }

    public static String test(AiCloudSettings settings) throws Exception {
        if (settings == null || !settings.hasApiKey()) {
            throw new IllegalStateException("请先保存 API Key");
        }
        String provider = settings.provider();
        if ("GEMINI".equals(provider)) return testGemini(settings);
        if ("ANTHROPIC".equals(provider)) return testAnthropic(settings);
        return testOpenAiCompatible(settings);
    }

    private static String testOpenAiCompatible(AiCloudSettings settings) throws Exception {
        String base = trimTrailingSlash(settings.baseUrl());
        if (base.isBlank()) throw new IllegalStateException("接口地址为空");
        JSONObject payload = new JSONObject()
                .put("model", settings.textModel())
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user").put("content", "ping")))
                .put("max_tokens", 1);
        String body = exchange(open(base + "/chat/completions"), payload.toString(), "application/json",
                "Bearer " + settings.apiKey());
        return "连接成功：" + concise(body);
    }

    private static String testGemini(AiCloudSettings settings) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + settings.textModel() + ":generateContent?key=" + settings.apiKey();
        JSONObject payload = new JSONObject().put("contents", new JSONArray()
                .put(new JSONObject().put("parts", new JSONArray()
                        .put(new JSONObject().put("text", "ping")))));
        String body = exchange(open(url), payload.toString(), "application/json", null);
        return "连接成功：" + concise(body);
    }

    private static String testAnthropic(AiCloudSettings settings) throws Exception {
        String base = trimTrailingSlash(settings.baseUrl());
        if (base.isBlank()) throw new IllegalStateException("接口地址为空");
        JSONObject payload = new JSONObject()
                .put("model", settings.textModel())
                .put("max_tokens", 1)
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user").put("content", "ping")));
        HttpURLConnection connection = open(base + "/v1/messages");
        connection.setRequestProperty("x-api-key", settings.apiKey());
        connection.setRequestProperty("anthropic-version", "2023-06-01");
        String body = exchange(connection, payload.toString(), "application/json", null);
        return "连接成功：" + concise(body);
    }

    private static String exchange(HttpURLConnection connection, String payload, String contentType,
                                   String authorization) throws Exception {
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Content-Type", contentType);
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        } finally {
            connection.disconnect();
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + "：" + concise(body.toString()));
        }
        return body.toString();
    }

    private static HttpURLConnection open(String url) throws Exception {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        String out = value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String concise(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
