package cn.longer233.gamenarrator.mobile;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import java.util.List;

public final class CompanionPairingActivity extends Activity {
    private DecoratedBarcodeView scanner;
    private boolean handled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        scanner = new DecoratedBarcodeView(this);
        scanner.setStatusText("扫描电脑端显示的 GameNarrator 配对二维码");
        setContentView(scanner);
        scanner.decodeContinuous(new BarcodeCallback() {
            @Override public void barcodeResult(BarcodeResult result) { accept(result == null ? null : result.getText()); }
            @Override public void possibleResultPoints(List<ResultPoint> points) { }
        });
    }

    private void accept(String value) {
        if (handled || value == null) return;
        try {
            Uri uri = Uri.parse(value);
            if (!"gamenarrator".equals(uri.getScheme()) || !"companion".equals(uri.getHost())) throw new IllegalArgumentException();
            String address = uri.getQueryParameter("address"), token = uri.getQueryParameter("token");
            ComputerCompanionClient.validateAddress(address);
            if (token == null || token.length() < 24) throw new IllegalArgumentException();
            handled = true;
            new ComputerCompanionSettings(this).save(address, token);
            Toast.makeText(this, "配对成功，可以用电脑解析视频了", Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception error) {
            Toast.makeText(this, "这不是有效的 GameNarrator 配对二维码", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onResume() { super.onResume(); scanner.resume(); }
    @Override protected void onPause() { scanner.pause(); super.onPause(); }
}
