package cn.longer233.gamenarrator.mobile;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Native Bilibili QR login using the official passport endpoints. The QR code
 * can be scanned by any logged-in Bilibili client; on success the returned
 * cookies are stored in-memory only and reused for platform imports.
 */
public final class BilibiliQrLogin {
    private static final String GENERATE_URL =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate";
    private static final String POLL_URL =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=";

    private final Context context;
    private final PlatformLoginSession session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Dialog dialog;
    private boolean polling;
    private String qrcodeKey;
    private Runnable pollTask;

    public BilibiliQrLogin(Context context, PlatformLoginSession session) {
        this.context = context;
        this.session = session;
    }

    public void start(final LoginListener listener) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = open(GENERATE_URL);
                JSONObject data = new JSONObject(read(connection)).getJSONObject("data");
                final String url = data.getString("url");
                qrcodeKey = data.getString("qrcode_key");
                handler.post(() -> showDialog(url, listener));
            } catch (Exception error) {
                handler.post(() -> {
                    if (listener != null) {
                        listener.onError(error.getMessage() == null ? "生成二维码失败" : error.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     * Local confirmation without showing a QR code: generate a session key,
     * open the official confirm page inside the installed Bilibili app and
     * poll silently until the user confirms.
     */
    public void startDeviceConfirm(final LoginListener listener) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = open(GENERATE_URL);
                JSONObject data = new JSONObject(read(connection)).getJSONObject("data");
                final String url = data.getString("url");
                qrcodeKey = data.getString("qrcode_key");
                handler.post(() -> {
                    if (!openInBilibiliApp(url)) {
                        if (listener != null) listener.onError("未找到 Bilibili App，请使用扫码登录或 cookies.txt");
                        return;
                    }
                    startPolling(listener);
                    handler.postDelayed(() -> {
                        if (polling) {
                            stopPolling();
                            if (listener != null) listener.onError("确认超时，请重试");
                        }
                    }, 90_000);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (listener != null) {
                        listener.onError(error.getMessage() == null ? "生成登录会话失败" : error.getMessage());
                    }
                });
            }
        }).start();
    }

    private boolean openInBilibiliApp(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage("tv.danmaku.bili");
            if (context.getPackageManager().resolveActivity(intent, 0) != null) {
                context.startActivity(intent);
                return true;
            }
            intent.setPackage(null);
            context.startActivity(intent);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void showDialog(String url, final LoginListener listener) {
        dialog = new Dialog(context);
        dialog.setTitle("Bilibili 扫码登录");
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(32, 24, 32, 24);

        Bitmap qr = renderQr(url, 480);
        ImageView image = new ImageView(context);
        image.setImageBitmap(qr);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(480, 480);
        imageParams.setMargins(0, 16, 0, 8);
        panel.addView(image, imageParams);

        final TextView status = new TextView(context);
        status.setText("用另一台设备的 B 站 App 扫码即可完成登录；单台设备请改用 cookies.txt 导入。");
        status.setTextSize(13);
        status.setTextColor(Color.rgb(70, 80, 92));
        status.setTypeface(Typeface.DEFAULT);
        panel.addView(status);

        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> stopPolling());
        dialog.show();
        startPolling(listener);
    }

    private void startPolling(final LoginListener listener) {
        stopPolling();
        polling = true;
        pollTask = new Runnable() {
            @Override public void run() {
                if (!polling) return;
                new Thread(() -> {
                    try {
                        HttpURLConnection connection = open(POLL_URL + qrcodeKey);
                        String body = read(connection);
                        JSONObject payload = new JSONObject(body).getJSONObject("data");
                        int code = payload.optInt("code", -1);
                        final String message = payload.optString("message", "");
                        if (code == 0) {
                            String cookies = extractCookies(connection);
                            handler.post(() -> {
                                if (!polling) return;
                                stopPolling();
                                if (dialog != null) dialog.dismiss();
                                if (cookies == null || cookies.isBlank()) {
                                    if (listener != null) listener.onError("登录成功但未取到会话，请重试");
                                    return;
                                }
                                session.setNativeCookies(cookies);
                                if (listener != null) listener.onSuccess(cookies);
                            });
                            return;
                        }
                        if (code == 86038) {
                            handler.post(() -> {
                                if (!polling) return;
                                stopPolling();
                                if (listener != null) listener.onError("二维码已过期，请重新生成");
                            });
                            return;
                        }
                        handler.postDelayed(() -> {
                            if (polling) {
                                pollTask = this;
                                handler.postDelayed(this, 2000);
                            }
                        }, 0);
                    } catch (Exception error) {
                        handler.postDelayed(() -> {
                            if (polling) {
                                handler.postDelayed(this, 2000);
                            }
                        }, 0);
                    }
                }).start();
            }
        };
        handler.postDelayed(pollTask, 2000);
    }

    private void stopPolling() {
        polling = false;
        if (pollTask != null) {
            handler.removeCallbacks(pollTask);
            pollTask = null;
        }
    }

    private static String extractCookies(HttpURLConnection connection) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        List<String> setCookie = headers == null ? null : headers.get("Set-Cookie");
        return parseSetCookie(setCookie);
    }

    static String parseSetCookie(List<String> setCookie) {
        if (setCookie == null || setCookie.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String value : setCookie) {
            if (value == null) continue;
            int end = value.indexOf(';');
            String pair = end > 0 ? value.substring(0, end) : value;
            if (!pair.contains("=")) continue;
            if (out.length() > 0) out.append("; ");
            out.append(pair);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static Bitmap renderQr(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception error) {
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 GameNarrator-Android/" + BuildConfig.VERSION_NAME);
        return connection;
    }

    private static String read(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }

    public interface LoginListener {
        void onSuccess(String cookies);
        void onError(String message);
    }
}
