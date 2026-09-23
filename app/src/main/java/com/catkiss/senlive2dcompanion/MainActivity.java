package com.catkiss.senlive2dcompanion;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 菜菜女仆主模型 + Sen 呆毛、耳鳍、尾巴配件实验室。 */
public class MainActivity extends AppCompatActivity implements SenCompanionView.Listener {
    private static final String PREFS = "caicai_maid_accessory_lab";
    private static final String CALIBRATION_KEY = "accessory_calibration_v2_native_ears";
    private static final String VERSION = "v0.1.11 · 女仆三角载体挂件绑定";
    private static final CompositeOverlayGroup[] SELECTABLE_ACCESSORY_GROUPS = {
            CompositeOverlayGroup.TAIL,
            CompositeOverlayGroup.AHOGE,
            CompositeOverlayGroup.EAR_FINS
    };
    private static final long MAX_EXTRACTED_BYTES = 1_500_000_000L;
    private static final int MAX_ZIP_ENTRIES = 8_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private File modelRoot;
    private File importRoot;
    private SenCompanionView companionView;
    private TextView statusText;
    private TextView summaryText;
    private TextView calibrationText;
    private FrameLayout loadingOverlay;
    private TextView loadingText;
    private OverlayCalibration calibration;
    private CompositeOverlayGroup selectedGroup = CompositeOverlayGroup.EAR_FINS;
    private CompositeTestMotion selectedMotion = CompositeTestMotion.LIVE;
    private String pendingExportReport;
    private boolean staticMode;
    private boolean stageAdjustmentEnabled;
    private boolean whiteSocks;
    private float stageScale = 1f;
    private float stageX;
    private float stageY;
    private float lastTouchX;
    private float lastTouchY;
    private ScaleGestureDetector scaleGestureDetector;

    private final ActivityResultLauncher<String[]> packagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importPackage);
    private final ActivityResultLauncher<String> reportCreator = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::writeReport);

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        modelRoot = new File(getFilesDir(), "caicai-accessory-package");
        importRoot = new File(getFilesDir(), "caicai-accessory-import-temp");
        calibration = OverlayCalibration.fromJson(
                prefs.getString(CALIBRATION_KEY, ""));
        selectedGroup = CompositeOverlayGroup.fromId(
                prefs.getString("calibration_group", CompositeOverlayGroup.EAR_FINS.id));
        // GLOBAL is a shared coordinate base, not an accessory. Older builds accidentally exposed
        // it as a fourth "ahoge + ear fins" selection in the previous/next cycle.
        if (selectedGroup == CompositeOverlayGroup.GLOBAL) {
            selectedGroup = CompositeOverlayGroup.EAR_FINS;
        }
        staticMode = prefs.getBoolean("static_mode", false);
        buildUi();
        loadModels();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(20, 16, 29));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, match());

        LinearLayout toolbar = row();
        toolbar.setPadding(dp(6), dp(4), dp(6), dp(4));
        toolbar.setBackgroundColor(Color.rgb(33, 25, 47));
        Button importButton = compactButton("导入鲸鱼女仆包");
        importButton.setOnClickListener(v -> packagePicker.launch(zipMimeTypes()));
        toolbar.addView(importButton);
        Button reload = compactButton("重载");
        reload.setOnClickListener(v -> loadModels());
        toolbar.addView(reload);
        statusText = text(VERSION, 10, Color.rgb(235, 224, 246));
        statusText.setSingleLine(true);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusParams.setMarginStart(dp(5));
        toolbar.addView(statusText, statusParams);
        page.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        FrameLayout stage = new FrameLayout(this);
        companionView = new SenCompanionView(this);
        companionView.setListener(this);
        installStageGestures();
        stage.addView(companionView, match());
        page.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.15f));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(30, 23, 43));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(7), dp(10), dp(14));
        scroll.addView(panel);
        panel.addView(text("菜菜女仆 · Sen 三配件实验室", 16, Color.WHITE));
        summaryText = text("", 10, Color.rgb(203, 188, 218));
        panel.addView(summaryText);

        panel.addView(section("配件位置（数值自动保存）"));
        LinearLayout groupRow = row();
        groupRow.addView(actionButton("上一件", () -> stepGroup(-1)), weighted());
        groupRow.addView(actionButton("下一件", () -> stepGroup(1)), weighted());
        groupRow.addView(actionButton("显示/隐藏", this::toggleGroup), weighted());
        panel.addView(groupRow);
        LinearLayout scaleRow = row();
        scaleRow.addView(actionButton("缩小", () -> adjust(-.02f, 0, 0)), weighted());
        scaleRow.addView(actionButton("放大", () -> adjust(.02f, 0, 0)), weighted());
        scaleRow.addView(actionButton("上移", () -> adjust(0, 0, .01f)), weighted());
        scaleRow.addView(actionButton("下移", () -> adjust(0, 0, -.01f)), weighted());
        panel.addView(scaleRow);
        LinearLayout moveRow = row();
        moveRow.addView(actionButton("左移", () -> adjust(0, -.01f, 0)), weighted());
        moveRow.addView(actionButton("右移", () -> adjust(0, .01f, 0)), weighted());
        moveRow.addView(actionButton("重置当前", this::resetGroup),
                new LinearLayout.LayoutParams(0, dp(42), 2f));
        panel.addView(moveRow);

        panel.addView(section("耳鳍原生双耳调节"));
        LinearLayout earRow2 = row();
        earRow2.addView(actionButton("整体左转", () -> adjustEarPairRotation(1f)), weighted());
        earRow2.addView(actionButton("整体右转", () -> adjustEarPairRotation(-1f)), weighted());
        panel.addView(earRow2);
        panel.addView(text("左右耳、间距与各自动作由 Sen 原生网格和参数负责；"
                        + "本版不再使用 App 镜像。",
                9, Color.rgb(180, 159, 199)));
        calibrationText = text("", 10, Color.rgb(225, 204, 240));
        panel.addView(calibrationText);
        updateCalibrationText();

        panel.addView(section("静止基准与吻合度动作"));
        LinearLayout staticRow = row();
        Button staticButton = panelButton(staticMode ? "完全静止：开启" : "完全静止：关闭");
        staticButton.setOnClickListener(v -> {
            staticMode = !staticMode;
            prefs.edit().putBoolean("static_mode", staticMode).apply();
            staticButton.setText(staticMode ? "完全静止：开启" : "完全静止：关闭");
            companionView.setStaticMode(staticMode);
            updateSummary();
        });
        staticRow.addView(staticButton, weighted());
        staticRow.addView(actionButton("左右大幅", () -> selectMotion(CompositeTestMotion.HEAD_X_SWEEP)), weighted());
        staticRow.addView(actionButton("上下大幅", () -> selectMotion(CompositeTestMotion.HEAD_Y_SWEEP)), weighted());
        panel.addView(staticRow);
        LinearLayout headMotionRow = row();
        headMotionRow.addView(actionButton("歪头大幅", () -> selectMotion(CompositeTestMotion.HEAD_Z_SWEEP)), weighted());
        headMotionRow.addView(actionButton("综合头摆", () -> selectMotion(CompositeTestMotion.HEAD_SWEEP)), weighted());
        headMotionRow.addView(actionButton("身体摆动", () -> selectMotion(CompositeTestMotion.BODY_SWEEP)), weighted());
        panel.addView(headMotionRow);
        LinearLayout motionRow = row();
        motionRow.addView(actionButton("实时", () -> selectMotion(CompositeTestMotion.LIVE)), weighted());
        motionRow.addView(actionButton("自动巡检", () -> selectMotion(CompositeTestMotion.AUTO)), weighted());
        motionRow.addView(actionButton("中立", () -> selectMotion(CompositeTestMotion.NEUTRAL)), weighted());
        panel.addView(motionRow);
        Button earTwitch = panelButton("单独测试：耳鳍快速抖动两次");
        earTwitch.setOnClickListener(v -> {
            if (staticMode) {
                staticMode = false;
                prefs.edit().putBoolean("static_mode", false).apply();
                staticButton.setText("完全静止：关闭");
                companionView.setStaticMode(false);
            }
            companionView.triggerEarTwitch();
            updateSummary();
        });
        panel.addView(earTwitch);

        panel.addView(section("表情（同组互斥）"));
        addPresetRows(panel, new String[]{"1爱心", "1生气", "1红脸", "1钱钱",
                "1黑脸", "1星星眼", "1流泪"}, true);
        panel.addView(section("动作（同组互斥）"));
        addPresetRows(panel, new String[]{"2奶茶", "2插手", "2比耶", "2点单",
                "2菜单", "2餐盘左", "2餐盘右"}, true);

        panel.addView(section("装扮与变小（可叠加）"));
        LinearLayout outfit1 = row();
        Button socks = panelButton("白袜");
        socks.setOnClickListener(v -> {
            companionView.applyExpression("1白袜");
            whiteSocks = !whiteSocks;
            socks.setText(whiteSocks ? "黑袜" : "白袜");
        });
        outfit1.addView(socks, weighted());
        outfit1.addView(presetButton("丝袜带子", "丝袜带子"), weighted());
        outfit1.addView(presetButton("双马尾", "双马尾"), weighted());
        panel.addView(outfit1);
        LinearLayout outfit2 = row();
        outfit2.addView(presetButton("发带", "发带"), weighted());
        outfit2.addView(presetButton("变小", "变小"), weighted());
        panel.addView(outfit2);

        panel.addView(section("诊断与舞台"));
        LinearLayout diagnostic = row();
        Button export = panelButton("导出位置诊断 JSON");
        export.setOnClickListener(v -> companionView.requestCompositeReport());
        diagnostic.addView(export, new LinearLayout.LayoutParams(0, dp(44), 2f));
        Button stageAdjust = panelButton("整体调整：关");
        stageAdjust.setOnClickListener(v -> {
            stageAdjustmentEnabled = !stageAdjustmentEnabled;
            stageAdjust.setText(stageAdjustmentEnabled ? "整体调整：开" : "整体调整：关");
        });
        diagnostic.addView(stageAdjust, weighted());
        diagnostic.addView(actionButton("还原整体", this::resetStage), weighted());
        panel.addView(diagnostic);
        panel.addView(text("点击模型会触发“点击”预设；完全静止时不会触发。"
                        + "图层：尾巴最后、耳鳍位于双马尾上方一层、呆毛最前。",
                9, Color.rgb(180, 159, 199)));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(Color.argb(210, 18, 14, 26));
        LinearLayout loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        loadingBox.addView(new ProgressBar(this));
        loadingText = text("", 12, Color.WHITE);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(dp(20), dp(10), dp(20), 0);
        loadingBox.addView(loadingText);
        loadingOverlay.addView(loadingBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        loadingOverlay.setVisibility(android.view.View.GONE);
        root.addView(loadingOverlay, match());
        setContentView(root);
        updateSummary();
    }

    private void addPresetRows(LinearLayout panel, String[] presets, boolean trimPrefix) {
        for (int start = 0; start < presets.length; start += 4) {
            LinearLayout row = row();
            for (int i = start; i < Math.min(start + 4, presets.length); i++) {
                String name = presets[i];
                String label = trimPrefix && name.length() > 1 ? name.substring(1) : name;
                row.addView(presetButton(label, name), weighted());
            }
            panel.addView(row);
        }
    }

    private Button presetButton(String label, String expression) {
        Button button = panelButton(label);
        button.setOnClickListener(v -> companionView.applyExpression(expression));
        return button;
    }

    private void installStageGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        if (!stageAdjustmentEnabled) return false;
                        stageScale = clamp(stageScale * detector.getScaleFactor(), .35f, 6f);
                        applyStage();
                        return true;
                    }
                });
        companionView.setOnTouchListener((view, event) -> {
            if (!stageAdjustmentEnabled) {
                if (!staticMode && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    companionView.applyExpression("点击");
                }
                return true;
            }
            scaleGestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && event.getPointerCount() == 1 && !scaleGestureDetector.isInProgress()) {
                stageX += (event.getX() - lastTouchX) * 2f / Math.max(1, view.getWidth());
                stageY -= (event.getY() - lastTouchY) * 2f / Math.max(1, view.getHeight());
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                applyStage();
            }
            return true;
        });
    }

    private void adjust(float scale, float x, float y) {
        calibration = calibration.withDelta(selectedGroup, scale, x, y);
        persistCalibration();
    }

    private void adjustEarPairRotation(float pairRotation) {
        calibration = calibration.withEarDelta(0f, 0f, pairRotation);
        selectedGroup = CompositeOverlayGroup.EAR_FINS;
        persistCalibration();
    }

    private void resetGroup() {
        calibration = calibration.reset(selectedGroup);
        persistCalibration();
    }

    private void toggleGroup() {
        OverlayCalibration.Transform current = calibration.get(selectedGroup);
        calibration = calibration.withVisible(selectedGroup, !current.visible);
        persistCalibration();
    }

    private void stepGroup(int delta) {
        int current = 0;
        for (int i = 0; i < SELECTABLE_ACCESSORY_GROUPS.length; i++) {
            if (SELECTABLE_ACCESSORY_GROUPS[i] == selectedGroup) {
                current = i;
                break;
            }
        }
        int index = (current + delta + SELECTABLE_ACCESSORY_GROUPS.length)
                % SELECTABLE_ACCESSORY_GROUPS.length;
        selectedGroup = SELECTABLE_ACCESSORY_GROUPS[index];
        prefs.edit().putString("calibration_group", selectedGroup.id).apply();
        updateCalibrationText();
    }

    private void persistCalibration() {
        prefs.edit().putString(CALIBRATION_KEY,
                calibration.toPreferenceJson()).apply();
        companionView.setOverlayCalibration(calibration.toPreferenceJson());
        updateCalibrationText();
    }

    private void updateCalibrationText() {
        if (calibrationText != null) {
            calibrationText.setText(calibration.describe(selectedGroup) + "（自动保存）");
        }
    }

    private void selectMotion(CompositeTestMotion motion) {
        selectedMotion = motion;
        companionView.setCompositeTestMotion(motion.id);
        if (motion != CompositeTestMotion.NEUTRAL && staticMode) {
            staticMode = false;
            prefs.edit().putBoolean("static_mode", false).apply();
            companionView.setStaticMode(false);
        }
        updateSummary();
    }

    private void resetStage() {
        stageScale = 1f;
        stageX = 0f;
        stageY = 0f;
        applyStage();
    }

    private void applyStage() {
        companionView.setStageTransform(stageScale, stageX, stageY);
    }

    private void importPackage(Uri uri) {
        if (uri == null) return;
        showLoading("正在导入单 ZIP 模型包…");
        executor.execute(() -> {
            try {
                deleteRecursively(importRoot);
                if (!importRoot.mkdirs() && !importRoot.isDirectory()) {
                    throw new IOException("无法创建导入临时目录");
                }
                unzipSecure(uri, importRoot);
                File manifest = findFirst(importRoot, "accessory-lab.json");
                if (manifest == null) throw new IOException("ZIP 缺少 accessory-lab.json");
                JSONObject config = new JSONObject(new String(
                        NativeFileLoader.readFile(manifest), StandardCharsets.UTF_8));
                File packageBase = manifest.getParentFile();
                File main = safeChild(packageBase, config.getString("mainModel"));
                File accessory = safeChild(packageBase, config.getString("accessoryModel"));
                if (!main.isFile()) throw new IOException("找不到主模型：" + main.getName());
                if (!accessory.isFile()) throw new IOException("找不到配件模型：" + accessory.getName());
                deleteRecursively(modelRoot);
                if (!modelRoot.mkdirs() && !modelRoot.isDirectory()) {
                    throw new IOException("无法创建模型目录");
                }
                copyChildren(packageBase, modelRoot);
                prefs.edit()
                        .putString("main_model_path", relativePath(packageBase, main))
                        .putString("accessory_model_path", relativePath(packageBase, accessory))
                        .apply();
                runOnUiThread(() -> {
                    toast("鲸鱼女仆模型包导入成功");
                    loadModels();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    setStatus("导入失败：" + readable(error));
                    toast("导入失败：" + readable(error));
                });
            } finally {
                try { deleteRecursively(importRoot); } catch (IOException ignored) { }
            }
        });
    }

    private void loadModels() {
        String mainPath = prefs.getString("main_model_path", "");
        String accessoryPath = prefs.getString("accessory_model_path", "");
        File main = new File(modelRoot, mainPath);
        File accessory = new File(modelRoot, accessoryPath);
        updateSummary();
        if (mainPath.isBlank() || accessoryPath.isBlank()
                || !main.isFile() || !accessory.isFile()) {
            hideLoading();
            setStatus("请导入鲸鱼女仆单 ZIP 测试包");
            return;
        }
        showLoading("正在加载菜菜女仆与 Sen 三配件动力层…");
        companionView.setOverlayCalibration(calibration.toPreferenceJson());
        companionView.setCompositeTestMotion(selectedMotion.id);
        companionView.setStaticMode(staticMode);
        companionView.loadModels(main, accessory, !staticMode,
                SenMotionMode.EV_FAITHFUL.id,
                SenRenderOptions.DEFAULT_EV_BODY_FOLLOW_STRENGTH,
                CompositeOutfit.MAID_WITH_SEN_ACCESSORIES.id);
    }

    private void updateSummary() {
        if (summaryText == null) return;
        boolean imported = !prefs.getString("main_model_path", "").isBlank();
        summaryText.setText("模型包：" + (imported ? "已导入" : "未导入")
                + " · 基准：" + (staticMode ? "完全静止" : selectedMotion.displayName)
                + " · 默认黑袜（按钮显示目标服装）");
    }

    @Override public void onStatus(String status) { runOnUiThread(() -> setStatus(status)); }
    @Override public void onReady(String detail) {
        runOnUiThread(() -> {
            hideLoading();
            setStatus("模型与三配件已就绪");
            summaryText.setText(summaryText.getText() + "\n" + detail);
        });
    }
    @Override public void onError(Throwable error) {
        runOnUiThread(() -> {
            hideLoading();
            setStatus("渲染失败：" + readable(error));
            toast("渲染失败：" + readable(error));
        });
    }
    @Override public void onMotionDiagnosticStep(String label, int index, int total) { }
    @Override public void onMotionDiagnosticComplete(String report) { }
    @Override public void onCompositeReport(String report) {
        runOnUiThread(() -> {
            pendingExportReport = report;
            reportCreator.launch("caicai-maid-accessory-diagnostic-v0.1.11.json");
        });
    }

    private void writeReport(Uri uri) {
        if (uri == null || pendingExportReport == null) return;
        String report = pendingExportReport;
        pendingExportReport = null;
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("无法打开导出文件");
                output.write(report.getBytes(StandardCharsets.UTF_8));
                output.flush();
                runOnUiThread(() -> toast("诊断 JSON 已导出，请连同静止截图发回来"));
            } catch (Throwable error) {
                runOnUiThread(() -> toast("导出失败：" + readable(error)));
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (companionView != null) companionView.onHostResume();
    }
    @Override protected void onPause() {
        if (companionView != null) companionView.onHostPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        executor.shutdownNow();
        if (companionView != null) companionView.release();
        super.onDestroy();
    }

    private void unzipSecure(Uri uri, File destination) throws IOException {
        long total = 0;
        int entries = 0;
        InputStream raw = getContentResolver().openInputStream(uri);
        if (raw == null) throw new IOException("无法读取 ZIP");
        try (InputStream input = raw; ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            String root = destination.getCanonicalPath() + File.separator;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IOException("ZIP 文件项过多");
                File out = new File(destination, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) throw new IOException("ZIP 路径不安全");
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) throw new IOException("无法创建目录");
                    continue;
                }
                File parent = out.getParentFile();
                if (parent == null || (!parent.mkdirs() && !parent.isDirectory())) {
                    throw new IOException("无法创建解压目录");
                }
                try (OutputStream output = new FileOutputStream(out)) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        total += read;
                        if (total > MAX_EXTRACTED_BYTES) throw new IOException("ZIP 解压后体积过大");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static File safeChild(File root, String relative) throws IOException {
        File result = new File(root, relative);
        String safeRoot = root.getCanonicalPath() + File.separator;
        if (!result.getCanonicalPath().startsWith(safeRoot)) throw new IOException("模型路径不安全");
        return result;
    }

    private static File findFirst(File root, String name) {
        if (root == null || !root.exists()) return null;
        if (root.isFile()) return root.getName().equalsIgnoreCase(name) ? root : null;
        File[] children = root.listFiles();
        if (children == null) return null;
        Arrays.sort(children, java.util.Comparator.comparing(File::getName));
        for (File child : children) {
            File found = findFirst(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static void copyChildren(File source, File destination) throws IOException {
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) copyRecursively(child, new File(destination, child.getName()));
    }

    private static void copyRecursively(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.mkdirs() && !destination.isDirectory()) {
                throw new IOException("无法创建目录：" + destination.getName());
            }
            File[] children = source.listFiles();
            if (children != null) for (File child : children) {
                copyRecursively(child, new File(destination, child.getName()));
            }
            return;
        }
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("无法清理：" + file.getName());
    }

    private static String relativePath(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath() + File.separator;
        String filePath = file.getCanonicalPath();
        if (!filePath.startsWith(rootPath)) throw new IOException("模型路径不安全");
        return filePath.substring(rootPath.length()).replace(File.separatorChar, '/');
    }

    private static String[] zipMimeTypes() {
        return new String[]{"application/zip", "application/octet-stream"};
    }
    private void showLoading(String message) {
        if (loadingText != null) loadingText.setText(message);
        if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.VISIBLE);
    }
    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.GONE);
    }
    private void setStatus(String value) { if (statusText != null) statusText.setText(value); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private static String readable(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    private TextView section(String value) {
        TextView view = text(value, 12, Color.rgb(238, 207, 255));
        view.setPadding(0, dp(7), 0, dp(3));
        return view;
    }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
    private Button compactButton(String label) {
        Button button = panelButton(label);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(9), 0, dp(9), 0);
        return button;
    }
    private Button panelButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(Color.rgb(86, 57, 119), 11));
        return button;
    }
    private Button actionButton(String label, Runnable action) {
        Button button = panelButton(label);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }
    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
