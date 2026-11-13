package com.meridian.approach;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Toast;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int FILE_CHOOSER_REQUEST = 1107;
    private long lastBackPressMs = 0L;

    // 포병(FIRE DIRECTION LAB)과 같은 업데이트 방식:
    // 저장소 최상위 update.json 을 보고, 더 높은 build 면 그 HTML 을 받아 앱 안 저장소에 두고 file:// 로 띄운다.
    private static final String RAW_BASE = "https://raw.githubusercontent.com/Byanka-Rail/MERIDIAN_APPROACH/main/";
    private static final String UPDATE_JSON = RAW_BASE + "update.json";
    private static final String BUNDLED_URL = "file:///android_asset/MERIDIAN_APPROACH.html";
    private static final long MAX_GAME_BYTES = 40L * 1024L * 1024L;
    private static final Pattern TITLE_VER = Pattern.compile("<title>[^<]*?v(\\d+)\\.(\\d+)\\.(\\d+)");
    private SharedPreferences prefs;
    private int bundledBuild = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        applyImmersiveMode();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(6, 17, 27));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            // v0.29.81: a 10 MB page with large textures can lose its renderer under memory
            // pressure. Without this override the whole app is killed; instead we rebuild the
            // Activity so the game reloads to its menu.
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == webView) {
                    try {
                        webView.destroy();
                    } catch (Exception ignored) {
                    }
                    webView = null;
                    Toast.makeText(MainActivity.this, "화면 프로세스가 종료되어 다시 불러옵니다.", Toast.LENGTH_SHORT).show();
                    recreate();
                    return true;
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;
                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // MERIDIAN APPROACH does not require camera/microphone permissions.
                request.deny();
            }
        });

        prefs = getSharedPreferences("ma_update", MODE_PRIVATE);
        bundledBuild = readBundledBuild();
        // 새 APK 에 든 판이 받아 둔 판보다 같거나 높으면 받아 둔 것을 버린다
        if (prefs.getInt("current_build", 0) <= bundledBuild) dropDownloaded();

        if (savedInstanceState == null) {
            webView.loadUrl(gameUrl());
        } else {
            webView.restoreState(savedInstanceState);
        }

        checkForUpdate();
    }

    // ── 업데이트 ─────────────────────────────────────────────
    private File downloadedFile() {
        return new File(new File(getFilesDir(), "game"), "MERIDIAN_APPROACH.html");
    }

    private String gameUrl() {
        File f = downloadedFile();
        if (f.isFile() && prefs.getInt("current_build", 0) > bundledBuild) return Uri.fromFile(f).toString();
        return BUNDLED_URL;
    }

    private void dropDownloaded() {
        File f = downloadedFile();
        if (f.exists()) f.delete();
        prefs.edit().remove("current_build").remove("current_version").apply();
    }

    private int readBundledBuild() {
        try (InputStream in = getAssets().open("MERIDIAN_APPROACH.html")) {
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            return buildFromHtmlHead(new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int buildFromHtmlHead(String head) {
        Matcher m = TITLE_VER.matcher(head);
        if (!m.find()) return 0;
        return Integer.parseInt(m.group(1)) * 1000000 + Integer.parseInt(m.group(2)) * 1000 + Integer.parseInt(m.group(3));
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject(new String(readUrl(UPDATE_JSON + "?_=" + System.currentTimeMillis(), 64 * 1024), StandardCharsets.UTF_8));
                int build = o.optInt("build", 0);
                String file = o.optString("file", "");
                String version = o.optString("version", "");
                String sha = o.optString("sha256", "").trim();
                int current = Math.max(bundledBuild, prefs.getInt("current_build", 0));
                if (build <= current || file.isEmpty()) return;

                String url = file.startsWith("https://") ? file : RAW_BASE + URLEncoder.encode(file, "UTF-8").replace("+", "%20");
                byte[] bytes = readUrl(url + "?_=" + System.currentTimeMillis(), MAX_GAME_BYTES);
                if (!sha.isEmpty() && !sha.equalsIgnoreCase(sha256(bytes))) throw new Exception("SHA-256 검증 실패");
                String head = new String(bytes, 0, Math.min(bytes.length, 8192), StandardCharsets.UTF_8);
                if (!head.toLowerCase().contains("<html")) throw new Exception("HTML 이 아닙니다");

                File dir = downloadedFile().getParentFile();
                if (dir != null && !dir.isDirectory()) dir.mkdirs();
                File tmp = new File(dir, "MERIDIAN_APPROACH.html.tmp");
                try (FileOutputStream os = new FileOutputStream(tmp)) { os.write(bytes); }
                if (!tmp.renameTo(downloadedFile())) throw new Exception("파일 교체 실패");
                prefs.edit().putInt("current_build", build).putString("current_version", version).apply();

                runOnUiThread(() -> {
                    if (isFinishing() || webView == null) return;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("MERIDIAN APPROACH 업데이트")
                            .setMessage("v" + version + " 을 받았습니다. 지금 다시 불러올까요?\n(나중에 앱을 다시 켜도 적용됩니다)")
                            .setPositiveButton("지금", (d, w) -> { if (webView != null) webView.loadUrl(gameUrl()); })
                            .setNegativeButton("나중에", null)
                            .show();
                });
            } catch (Exception e) {
                // 네트워크 실패나 검증 실패는 게임 실행을 막지 않는다
            }
        }).start();
    }

    private static byte[] readUrl(String url, long max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[65536];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > max) throw new Exception("파일이 너무 큽니다");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        applyImmersiveMode();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // v0.29.81: the game is a single page, so a stray back press used to quit mid-flight.
        // Require a second press within two seconds.
        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 2000L) {
            super.onBackPressed();
        } else {
            lastBackPressMs = now;
            Toast.makeText(this, "한 번 더 누르면 MERIDIAN APPROACH 를 종료합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}
