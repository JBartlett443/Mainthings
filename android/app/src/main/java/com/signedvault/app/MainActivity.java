package com.signedvault.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * SignedVault wraps the bundled web app (assets/index.html) in a WebView served from a
 * private https origin, so IndexedDB, the camera and clipboard behave as in a browser.
 * It bridges the pieces a WebView lacks: file pickers with camera capture, saving exports
 * to Downloads, printing, and opening box QR links.
 */
public class MainActivity extends Activity {
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + ASSET_HOST + "/assets/index.html";
    private static final int REQ_FILE = 1;
    private static final int REQ_CAMERA_PERMISSION = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private Runnable afterCameraGranted;
    private Runnable afterCameraDenied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (ASSET_HOST.equals(u.getHost())) return false;
                // a link to the public copy of this app (e.g. a scanned label) stays in the app
                String box = boxFrom(u);
                if (box != null) { openBox(box); return true; }
                openExternal(u);
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                String[] types = params.getAcceptTypes();
                boolean image = types != null && types.length > 0 && types[0] != null && types[0].startsWith("image");
                if (image && params.isCaptureEnabled()) {
                    withCamera(MainActivity.this::launchCamera, () -> finishFileChooser(null));
                } else {
                    launchPicker(image ? "image/*" : "*/*");
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        withCamera(() -> request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}), request::deny);
                    } else {
                        request.deny();
                    }
                });
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            String box = boxFrom(getIntent().getData());
            web.loadUrl(box != null ? HOME + "#" + box : HOME);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String box = boxFrom(intent.getData());
        if (box != null) openBox(box);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Box code from https://jbartlett443.github.io/Mainthings/#BOX-01, ?box=BOX-01 or signedvault://BOX-01. */
    private static String boxFrom(Uri u) {
        if (u == null) return null;
        String code = null;
        if ("signedvault".equals(u.getScheme())) {
            code = u.getHost();
        } else if ("jbartlett443.github.io".equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/Mainthings")) {
            code = u.getFragment();
            if ((code == null || code.isEmpty()) && u.isHierarchical()) code = u.getQueryParameter("box");
        }
        return code != null && code.matches("[A-Za-z0-9-]{1,40}") ? code.toUpperCase() : null;
    }

    private void openBox(String box) {
        web.evaluateJavascript("location.hash=" + JSONObject.quote(box), null);
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open " + u, Toast.LENGTH_LONG).show();
        }
    }

    private void withCamera(Runnable granted, Runnable denied) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            granted.run();
            return;
        }
        if (afterCameraDenied != null) afterCameraDenied.run();
        afterCameraGranted = granted;
        afterCameraDenied = denied;
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_CAMERA_PERMISSION) return;
        Runnable granted = afterCameraGranted, denied = afterCameraDenied;
        afterCameraGranted = null;
        afterCameraDenied = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (granted != null) granted.run();
        } else {
            Toast.makeText(this, "SignedVault needs the camera for this. You can allow it in Settings → Apps.", Toast.LENGTH_LONG).show();
            if (denied != null) denied.run();
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no camera cache");
            File photo = new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", photo);
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(cam, REQ_FILE);
        } catch (Exception e) {
            cameraUri = null;
            Toast.makeText(this, "The camera couldn't open, choosing from your photos instead.", Toast.LENGTH_LONG).show();
            launchPicker("image/*");
        }
    }

    private void launchPicker(String type) {
        cameraUri = null;
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType(type);
        try {
            startActivityForResult(Intent.createChooser(pick, "Choose a file"), REQ_FILE);
        } catch (ActivityNotFoundException e) {
            finishFileChooser(null);
        }
    }

    private void finishFileChooser(Uri[] result) {
        if (fileCallback != null) fileCallback.onReceiveValue(result);
        fileCallback = null;
        cameraUri = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (cameraUri != null) result = new Uri[]{cameraUri};
            else if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
        }
        finishFileChooser(result);
    }

    /** Called from the page as window.AndroidBridge. */
    private class Bridge {
        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        /** Saves an export (backup, CSV, PDF) to Downloads/SignedVault. Returns "ok" or an error message. */
        @JavascriptInterface
        public String saveFile(String filename, String base64, String mime) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String name = filename.replaceAll("[^A-Za-z0-9._ -]", "_");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SignedVault");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) return "Downloads isn't available";
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) return "Downloads isn't writable";
                        out.write(bytes);
                    }
                } else {
                    // older Android: keep a copy in the app's folder and hand it to the share sheet
                    File dir = new File(getExternalFilesDir(null), "exports");
                    if (!dir.exists() && !dir.mkdirs()) return "couldn't create the export folder";
                    File f = new File(dir, name);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(bytes);
                    }
                    final Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    final Context ctx = MainActivity.this;
                    runOnUiThread(() -> {
                        Intent send = new Intent(Intent.ACTION_SEND).setType(mime)
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ctx.startActivity(Intent.createChooser(send, "Save or share " + name));
                    });
                }
                return "ok";
            } catch (Exception e) {
                return e.getMessage() != null ? e.getMessage() : e.toString();
            }
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm != null) {
                    pm.print("SignedVault", web.createPrintDocumentAdapter("SignedVault"),
                            new PrintAttributes.Builder().build());
                }
            });
        }
    }
}
