package com.catkiss.senlive2dcompanion;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.CubismFrameworkConfig;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class SenRenderer implements GLSurfaceView.Renderer {
    interface Listener {
        void onStatus(String status);
        void onReady(String detail);
        void onError(Throwable error);
        void onMotionDiagnosticStep(String label, int index, int total);
        void onMotionDiagnosticComplete(String report);
        void onCompositeReport(String report);
    }

    private static final String TAG = "SenNativeCubism";
    // The maid and Sen have different compiled deformation hierarchies and incompatible meanings
    // for several identically named parameters. Rigid motion therefore travels only one way: from
    // the maid's actual deformed carrier meshes to each neutral accessory projection. Sen retains
    // only accessory-local mesh dynamics such as its authored paired-ear twitch.
    private static final boolean TRIANGLE_CARRIER_ATTACHMENT_ENABLED = true;

    private final Context context;
    private final Listener listener;
    private final NativeTextureManager textures = new NativeTextureManager();
    private final CubismMatrix44 projection = CubismMatrix44.create();
    private final CubismMatrix44 maidProjection = CubismMatrix44.create();
    private final CubismMatrix44 senGroupProjection = CubismMatrix44.create();
    private final CubismMatrix44 interactionMvp = CubismMatrix44.create();

    private SenLive2DModel model;
    private SenLive2DModel overlayModel;
    private ModelRequest pendingRequest;
    private int surfaceWidth;
    private int surfaceHeight;
    private int maxTextureSize;
    private boolean frameworkReady;
    private boolean contextRecreated;
    private boolean released;
    private long lastFrameNanos;
    private volatile float stageScale = 1.0f;
    private volatile float stageTranslateX;
    private volatile float stageTranslateY;
    private float stagePivotX;
    private float stagePivotY;
    private volatile OverlayCalibration overlayCalibration = OverlayCalibration.defaults();
    private volatile CompositeTestMotion compositeTestMotion = CompositeTestMotion.LIVE;
    private volatile CompositeOutfit compositeOutfit =
            CompositeOutfit.MAID_WITH_SEN_ACCESSORIES;
    private volatile boolean touchFollowEnabled = true;
    private volatile boolean staticMode;
    private volatile float lipSyncValue;
    private volatile float modelBoundsLeft;
    private volatile float modelBoundsRight;
    private volatile float modelBoundsTop;
    private volatile float modelBoundsBottom;
    private volatile boolean modelBoundsValid;

    SenRenderer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void requestModel(File maidModelFile, File senModelFile,
                      List<String> startupExpressions, SenVtsAppearance appearance,
                      SenVtsProfile frozenProfile, SenRenderOptions options,
                      CompositeOutfit outfit) {
        if (released) return;
        pendingRequest = new ModelRequest(maidModelFile, senModelFile, startupExpressions,
                appearance, frozenProfile, options, outfit);
    }

    void setStageTransform(float scale, float translateX, float translateY) {
        stageScale = Math.max(0.35f, Math.min(6.0f, scale));
        // Keep at least part of the model inside the viewport at every zoom level. The previous
        // fixed +/-4 range allowed a small model to be moved completely outside the clip volume.
        float translationLimit = 0.9f + 0.5f * stageScale;
        stageTranslateX = Math.max(-translationLimit, Math.min(translationLimit, translateX));
        stageTranslateY = Math.max(-translationLimit, Math.min(translationLimit, translateY));
    }

    void setOverlayCalibration(OverlayCalibration calibration) {
        overlayCalibration = calibration == null ? OverlayCalibration.defaults() : calibration;
    }

    void setCompositeTestMotion(CompositeTestMotion motion) {
        compositeTestMotion = motion == null ? CompositeTestMotion.LIVE : motion;
        if (model != null) model.setCompositeTestMotion(compositeTestMotion);
    }

    void setStaticMode(boolean enabled) {
        staticMode = enabled;
        if (model != null) model.setStaticMode(enabled);
        if (overlayModel != null) overlayModel.setStaticMode(enabled);
    }

    void emitCompositeReport() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", "caicai-maid-accessory-calibration-v2");
            root.put("app_version", appVersionName());
            root.put("generated_at_epoch_ms", System.currentTimeMillis());
            root.put("main_model", "caicai_maid");
            root.put("accessories", new org.json.JSONArray(
                    Arrays.asList("ahoge", "ear_fins", "tail")));
            root.put("calibration_cycle", new org.json.JSONArray(
                    Arrays.asList("tail", "ahoge", "ear_fins")));
            root.put("test_motion", compositeTestMotion.id);
            root.put("attachment_mode", TRIANGLE_CARRIER_ATTACHMENT_ENABLED
                    ? "maid_coarse_part_adjacent_one_way_carriers"
                    : "neutral_accessory_projection_only");
            root.put("attachment_transform_space", "shared_post_projection");
            root.put("sen_rigid_parameter_drive", false);
            root.put("sen_local_accessory_dynamics", true);
            root.put("attachment_groups", new JSONObject()
                    .put("ahoge", "selected_slot_behind_adjacent_part")
                    .put("ear_fins_screen_left", "selected_slot_front_adjacent_part_screen_left")
                    .put("ear_fins_screen_right", "selected_slot_front_adjacent_part_screen_right")
                    .put("tail", "maid_body_neutral_to_current_on_accessory_bind_pose")
                    .put("combined_group", false));
            root.put("ear_visibility_source", "sen_accessory_only_not_headwear_opacity");
            root.put("ear_neutral_pose_policy", "inherit_v0.1.12_pair_projection_identity_offsets");
            root.put("ear_layer_policy", "independent_coarse_part_slot_per_side");
            root.put("head_test_motions", new org.json.JSONArray(Arrays.asList(
                    "head_x_sweep", "head_y_sweep", "head_z_sweep", "head_sweep")));
            root.put("stage_transform", new JSONObject()
                    .put("scale", stageScale)
                    .put("x", stageTranslateX)
                    .put("y", stageTranslateY)
                    .put("pivot_x", stagePivotX)
                    .put("pivot_y", stagePivotY));
            root.put("ear_right_mode", "sen_native_parameter_discovery");
            root.put("calibration", overlayCalibration.toJsonObject());
            root.put("maid_carrier_anchors", model == null
                    ? JSONObject.NULL : model.buildCarrierInventory());
            root.put("maid_part_layer_calibration", model == null
                    ? JSONObject.NULL : model.buildLayerCalibrationInventory(overlayCalibration));
            root.put("sen_runtime_inventory", overlayModel == null
                    ? JSONObject.NULL : overlayModel.buildCompositeInventory());
            listener.onCompositeReport(root.toString(2));
        } catch (JSONException error) {
            listener.onError(error);
        }
    }

    private String appVersionName() {
        try {
            return context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "0.1.14-part-layer-calibration";
        }
    }

    boolean screenToModelNormalized(float screenX, float screenY, float[] result) {
        if (!modelBoundsValid || result == null || result.length < 2) return false;
        float left = modelBoundsLeft;
        float right = modelBoundsRight;
        float top = modelBoundsTop;
        float bottom = modelBoundsBottom;
        if (right - left < 1e-5f || top - bottom < 1e-5f) return false;
        float clipX = screenX * 2.0f - 1.0f;
        float clipY = 1.0f - screenY * 2.0f;
        result[0] = (clipX - left) / (right - left);
        result[1] = (top - clipY) / (top - bottom);
        return Float.isFinite(result[0]) && Float.isFinite(result[1]);
    }

    void applyExpression(String name) {
        if (model != null) model.setExpression(name);
    }

    void resetNativePresets() {
        if (model != null) model.resetNativePresets();
    }

    void selectEmotion(String name) {
        if (model != null) model.selectEmotion(name);
    }

    void setLipSyncValue(float value) {
        lipSyncValue = Math.max(0.0f, Math.min(1.0f, value));
        if (model != null) model.setLipSyncValue(lipSyncValue);
    }

    void playAction(String name) {
        if (model != null) model.playAction(name);
    }

    void playNativeMotion(String name) {
        if (model != null) model.playNativeMotion(name);
    }

    void stopNativeMotion() {
        if (model != null) model.stopNativeMotion();
    }

    void triggerEarTwitch() {
        // The maid is the primary model, but Part113 and its isolated physics live in the Sen
        // donor. Route the test/personality event to the actual accessory owner.
        if (overlayModel != null) overlayModel.triggerEarTwitch();
        else if (model != null) model.triggerEarTwitch();
    }

    void setTouchFollowEnabled(boolean enabled) {
        touchFollowEnabled = enabled;
        if (model != null) model.setTouchFollowEnabled(enabled);
    }

    void setTouchTarget(boolean active, float normalizedX, float normalizedY) {
        if (model != null) model.setTouchTarget(active, normalizedX, normalizedY);
    }

    void triggerHeadPat(boolean confused) {
        if (model != null) model.triggerHeadPat(confused);
    }

    void releaseHeadPat() {
        if (model != null) model.releaseHeadPat();
    }

    void setAutoIdle(boolean enabled) {
        if (model != null) model.setAutoIdle(enabled);
    }

    void setMotionMode(SenMotionMode mode) {
        if (model != null) model.setMotionMode(mode);
    }

    void setEvBodyFollowStrength(float strength) {
        if (model != null) model.setEvBodyFollowStrength(strength);
    }

    void startMotionDiagnostic(SenMotionMode mode) {
        if (model != null) model.startMotionDiagnostic(mode);
    }

    void stopMotionDiagnostic() {
        if (model != null) model.stopMotionDiagnostic();
    }

    void selectOutfit(CompositeOutfit outfit) {
        compositeOutfit = outfit == null
                ? CompositeOutfit.MAID_WITH_SEN_ACCESSORIES : outfit;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        if (released) return;
        try {
            initializeFramework();
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            int[] value = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0);
            maxTextureSize = value[0];
            textures.forgetAfterContextLoss();
            CubismShaderAndroid.getInstance().releaseInvalidShaderProgram();
            CubismShaderAndroid.deleteInstance();
            contextRecreated = model != null || overlayModel != null;
            listener.onStatus("原生 OpenGL 已启动 · 最大贴图 " + maxTextureSize + "px");
        } catch (Throwable error) {
            listener.onError(error);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        if (released) return;
        surfaceWidth = width;
        surfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
        if (contextRecreated && model != null) {
            try {
                listener.onStatus("OpenGL 上下文已恢复，正在重建原生贴图…");
                model.reloadRenderer(width, height, textures, listener);
                if (overlayModel != null) {
                    overlayModel.reloadRenderer(width, height, textures, listener);
                }
                contextRecreated = false;
                listener.onReady(readyDetail());
            } catch (Throwable error) {
                listener.onError(error);
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // The test Activity supplies its own dark background. Keeping the GL surface transparent
        // lets this renderer later replace AI Companion's middle portrait layer unchanged.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearDepthf(1.0f);

        if (released) return;

        if (pendingRequest != null && surfaceWidth > 0 && surfaceHeight > 0) {
            ModelRequest request = pendingRequest;
            pendingRequest = null;
            loadRequestedModel(request);
        }

        if (model == null) return;
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1.0f / 60.0f
                : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f);
        lastFrameNanos = now;

        try {
            model.update(delta);
            boolean showSen = overlayModel != null;
            if (showSen) {
                overlayModel.update(delta);
            }

            prepareProjection(model, maidProjection, 1.0f, 0.0f, 0.0f);
            projection.setMatrix(maidProjection);
            updateInteractionBounds();

            if (showSen) {
                drawOverlayGroup(CompositeOverlayGroup.TAIL);
            }
            if (!showSen) {
                model.draw(maidProjection);
                return;
            }
            drawMainWithCalibratedAccessoryLayers();
        } catch (Throwable error) {
            listener.onError(error);
            releaseCurrentModel();
        }
    }

    private void drawMainWithCalibratedAccessoryLayers() {
        OverlayCalibration calibration = overlayCalibration;
        List<Integer> thresholds = new ArrayList<>();
        int ahogeThreshold = model.resolvedLayerThreshold(
                CompositeOverlayGroup.AHOGE, true,
                calibration.getLayerOffset(CompositeOverlayGroup.AHOGE, true));
        int leftEarThreshold = model.resolvedLayerThreshold(
                CompositeOverlayGroup.EAR_FINS, true,
                calibration.getLayerOffset(CompositeOverlayGroup.EAR_FINS, true));
        int rightEarThreshold = model.resolvedLayerThreshold(
                CompositeOverlayGroup.EAR_FINS, false,
                calibration.getLayerOffset(CompositeOverlayGroup.EAR_FINS, false));
        if (calibration.isVisible(CompositeOverlayGroup.AHOGE)) {
            thresholds.add(ahogeThreshold);
        }
        if (calibration.isVisible(CompositeOverlayGroup.EAR_FINS)) {
            if (!thresholds.contains(leftEarThreshold)) thresholds.add(leftEarThreshold);
            if (!thresholds.contains(rightEarThreshold)) thresholds.add(rightEarThreshold);
        }
        Collections.sort(thresholds);
        int previous = Integer.MIN_VALUE;
        for (int threshold : thresholds) {
            model.drawMainRenderRange(maidProjection, previous, threshold);
            if (calibration.isVisible(CompositeOverlayGroup.EAR_FINS)) {
                if (leftEarThreshold == threshold) drawEarFinSide(true, calibration);
                if (rightEarThreshold == threshold) drawEarFinSide(false, calibration);
            }
            if (calibration.isVisible(CompositeOverlayGroup.AHOGE)
                    && ahogeThreshold == threshold) {
                drawOverlayGroup(CompositeOverlayGroup.AHOGE);
            }
            previous = threshold;
        }
        model.drawMainRenderRange(maidProjection, previous, Integer.MAX_VALUE);
    }

    private void drawOverlayGroup(CompositeOverlayGroup group) {
        OverlayCalibration calibration = overlayCalibration;
        if (overlayModel == null || !calibration.isVisible(group)) return;
        prepareProjection(overlayModel, senGroupProjection,
                calibration.combinedScale(group),
                calibration.combinedX(group), calibration.combinedY(group));
        if (TRIANGLE_CARRIER_ATTACHMENT_ENABLED) {
            applyMaidCarrierMotion(group, senGroupProjection);
        }
        overlayModel.drawSenGroup(senGroupProjection, group);
    }

    private void drawEarFins() {
        OverlayCalibration calibration = overlayCalibration;
        CompositeOverlayGroup group = CompositeOverlayGroup.EAR_FINS;
        if (overlayModel == null || !calibration.isVisible(group)) return;
        drawEarFinSide(true, calibration);
        drawEarFinSide(false, calibration);
    }

    private void drawEarFinSide(boolean screenLeft, OverlayCalibration calibration) {
        CompositeOverlayGroup group = CompositeOverlayGroup.EAR_FINS;
        prepareProjection(overlayModel, senGroupProjection,
                calibration.combinedScale(group),
                calibration.combinedX(group), calibration.combinedY(group));
        // Preserve the confirmed v0.1.12 neutral pose: both side passes begin from exactly the
        // same pair projection and pair-rotation pivot. Side fine-tuning defaults to identity.
        OverlayCalibration.Transform ear = calibration.get(group);
        float[] pairCenterModel = overlayModel.currentCompositeGroupCenter(group);
        float[] pairCenter = pointToClip(overlayModel, senGroupProjection, pairCenterModel);
        if (pairCenter != null) {
            applyRotateAround(senGroupProjection, ear.pairRotation,
                    pairCenter[0], pairCenter[1]);
        }
        applyEarFineTune(screenLeft, calibration.getEarFineTune(screenLeft));
        if (TRIANGLE_CARRIER_ATTACHMENT_ENABLED) {
            applyMaidEarCarrierMotion(screenLeft, senGroupProjection);
        }
        overlayModel.drawSenEarSide(senGroupProjection, screenLeft);
    }

    private void applyEarFineTune(boolean screenLeft,
                                  OverlayCalibration.EarFineTune fineTune) {
        if (fineTune == null) return;
        float[] modelCenter = overlayModel.currentEarSideCenter(screenLeft);
        float[] center = pointToClip(overlayModel, senGroupProjection, modelCenter);
        if (center == null) return;
        float radians = (float) Math.toRadians(fineTune.rotation);
        float a = fineTune.scale * (float) Math.cos(radians);
        float b = fineTune.scale * (float) Math.sin(radians);
        float[] transform = {
                a, b, 0f, 0f,
                -b, a, 0f, 0f,
                0f, 0f, 1f, 0f,
                fineTune.x + center[0] - a * center[0] + b * center[1],
                fineTune.y + center[1] - b * center[0] - a * center[1],
                0f, 1f
        };
        overlayModel.applyClipTransform(senGroupProjection, transform);
    }

    private void applyMaidEarCarrierMotion(boolean screenLeft,
                                           CubismMatrix44 accessoryProjection) {
        if (model == null || overlayModel == null) return;
        int layerOffset = overlayCalibration.getLayerOffset(
                CompositeOverlayGroup.EAR_FINS, screenLeft);
        float[] mainNow = triangleToClip(model, maidProjection,
                model.currentLayerCarrierTriangle(
                        CompositeOverlayGroup.EAR_FINS, screenLeft, layerOffset));
        float[] mainNeutral = triangleToClip(model, maidProjection,
                model.neutralLayerCarrierTriangle(
                        CompositeOverlayGroup.EAR_FINS, screenLeft, layerOffset));
        if (mainNow == null || mainNeutral == null) return;
        Similarity2D maidMotion = Similarity2D.betweenTriangle(
                mainNeutral, mainNow, .50f, 1.80f);
        overlayModel.applyClipTransform(accessoryProjection, maidMotion.toMatrix());
    }

    private void applyMaidCarrierMotion(CompositeOverlayGroup group,
                                        CubismMatrix44 accessoryProjection) {
        if (model == null || overlayModel == null) return;
        int layerOffset = overlayCalibration.getLayerOffset(group, true);
        float[] mainNow = triangleToClip(model, maidProjection,
                group == CompositeOverlayGroup.AHOGE
                        ? model.currentLayerCarrierTriangle(group, true, layerOffset)
                        : model.currentCarrierTriangle(group));
        float[] mainNeutral = triangleToClip(model, maidProjection,
                group == CompositeOverlayGroup.AHOGE
                        ? model.neutralLayerCarrierTriangle(group, true, layerOffset)
                        : model.neutralCarrierTriangle(group));
        if (mainNow == null || mainNeutral == null) return;

        // Apply the maid carrier's observed neutral-to-current motion directly to the already
        // calibrated accessory bind pose. We deliberately never inspect or cancel a Sen head/body
        // carrier here: doing so lets the donor's incompatible rig re-enter the root transform and
        // was the source of the previous reversed, delayed and over-amplified movement.
        Similarity2D maidMotion = Similarity2D.betweenTriangle(
                mainNeutral, mainNow, .50f, 1.80f);
        overlayModel.applyClipTransform(accessoryProjection, maidMotion.toMatrix());
    }

    private float[] triangleToClip(SenLive2DModel target,
                                   CubismMatrix44 targetProjection, float[] triangle) {
        if (triangle == null || triangle.length < 6) return null;
        float[] result = new float[6];
        for (int i = 0; i < 3; i++) {
            float[] point = pointToClip(target, targetProjection,
                    new float[]{triangle[i * 2], triangle[i * 2 + 1]});
            if (point == null) return null;
            result[i * 2] = point[0];
            result[i * 2 + 1] = point[1];
        }
        return result;
    }

    private float[] pointToClip(SenLive2DModel target, CubismMatrix44 projection,
                                float[] point) {
        if (target == null || point == null || point.length < 2) return null;
        target.copyMvpMatrix(projection, interactionMvp);
        float[] matrix = interactionMvp.getArray();
        return new float[]{
                matrix[0] * point[0] + matrix[4] * point[1] + matrix[12],
                matrix[1] * point[0] + matrix[5] * point[1] + matrix[13]
        };
    }

    private void applyRotateAround(CubismMatrix44 matrix, float degrees,
                                   float centerX, float centerY) {
        if (Math.abs(degrees) < .001f) return;
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float[] rotate = {
                cos, sin, 0f, 0f,
                -sin, cos, 0f, 0f,
                0f, 0f, 1f, 0f,
                centerX - cos * centerX + sin * centerY,
                centerY - sin * centerX - cos * centerY,
                0f, 1f
        };
        overlayModel.applyClipTransform(matrix, rotate);
    }

    private static final class Similarity2D {
        final float a;
        final float b;
        final float tx;
        final float ty;

        private Similarity2D(float a, float b, float tx, float ty) {
            this.a = a;
            this.b = b;
            this.tx = tx;
            this.ty = ty;
        }

        static Similarity2D betweenTriangle(float[] source, float[] destination,
                                            float minimumScale, float maximumScale) {
            float sourceCenterX = (source[0] + source[2] + source[4]) / 3f;
            float sourceCenterY = (source[1] + source[3] + source[5]) / 3f;
            float destinationCenterX = (destination[0] + destination[2]
                    + destination[4]) / 3f;
            float destinationCenterY = (destination[1] + destination[3]
                    + destination[5]) / 3f;
            float numeratorA = 0f;
            float numeratorB = 0f;
            float denominator = 0f;
            for (int i = 0; i < 3; i++) {
                float sx = source[i * 2] - sourceCenterX;
                float sy = source[i * 2 + 1] - sourceCenterY;
                float dx = destination[i * 2] - destinationCenterX;
                float dy = destination[i * 2 + 1] - destinationCenterY;
                numeratorA += sx * dx + sy * dy;
                numeratorB += sx * dy - sy * dx;
                denominator += sx * sx + sy * sy;
            }
            float a = denominator < 1e-8f ? 1f : numeratorA / denominator;
            float b = denominator < 1e-8f ? 0f : numeratorB / denominator;
            float scale = (float) Math.hypot(a, b);
            if (!Float.isFinite(scale) || scale < 1e-6f) {
                a = 1f;
                b = 0f;
            } else {
                float clamped = Math.max(minimumScale, Math.min(maximumScale, scale));
                a *= clamped / scale;
                b *= clamped / scale;
            }
            float tx = destinationCenterX - a * sourceCenterX + b * sourceCenterY;
            float ty = destinationCenterY - b * sourceCenterX - a * sourceCenterY;
            return new Similarity2D(a, b, tx, ty);
        }

        float[] toMatrix() {
            return new float[]{
                    a, b, 0f, 0f,
                    -b, a, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    tx, ty, 0f, 1f
            };
        }
    }

    private void prepareProjection(SenLive2DModel target, CubismMatrix44 destination,
                                   float localScale, float localX, float localY) {
        destination.loadIdentity();
        float aspectRatio = (float) surfaceWidth / (float) surfaceHeight;
        float displayRatio = (float) surfaceHeight / (float) surfaceWidth;
        float canvasRatio = target.getCanvasHeight() / target.getCanvasWidth();
        if (canvasRatio < displayRatio) {
            target.fitWidth(2.0f);
            destination.scale(1.0f, aspectRatio);
        } else {
            target.fitHeight(2.0f);
            destination.scale(1.0f / aspectRatio, 1.0f);
        }
        // Keep confirmed accessory calibration in the projection layer, then append one common
        // post-projection stage transform. applyClipTransform() right-multiplies this matrix, so
        // filtered passes no longer receive model-layout-dependent scale or translation.
        destination.scaleRelative(localScale, localScale);
        destination.translateRelative(localX, localY);
        if (target == model) updateStagePivot(target, destination);
        applyStageTransform(target, destination);
    }

    private void updateStagePivot(SenLive2DModel target, CubismMatrix44 baseProjection) {
        float centerX = (target.getReferenceDrawableLeft()
                + target.getReferenceDrawableRight()) * .5f;
        float centerY = (target.getReferenceDrawableTop()
                + target.getReferenceDrawableBottom()) * .5f;
        float[] pivot = pointToClip(target, baseProjection, new float[]{centerX, centerY});
        if (pivot != null) {
            stagePivotX = pivot[0];
            stagePivotY = pivot[1];
        }
    }

    private void applyStageTransform(SenLive2DModel target, CubismMatrix44 destination) {
        float scale = stageScale;
        if (Math.abs(scale - 1f) < .00001f
                && Math.abs(stageTranslateX) < .00001f
                && Math.abs(stageTranslateY) < .00001f) return;
        float[] stage = {
                scale, 0f, 0f, 0f,
                0f, scale, 0f, 0f,
                0f, 0f, 1f, 0f,
                stageTranslateX + (1f - scale) * stagePivotX,
                stageTranslateY + (1f - scale) * stagePivotY,
                0f, 1f
        };
        target.applyClipTransform(destination, stage);
    }

    private void updateInteractionBounds() {
        model.copyMvpMatrix(projection, interactionMvp);
        float x1 = interactionMvp.transformX(model.getReferenceDrawableLeft());
        float x2 = interactionMvp.transformX(model.getReferenceDrawableRight());
        float y1 = interactionMvp.transformY(model.getReferenceDrawableTop());
        float y2 = interactionMvp.transformY(model.getReferenceDrawableBottom());
        modelBoundsLeft = Math.min(x1, x2);
        modelBoundsRight = Math.max(x1, x2);
        modelBoundsTop = Math.max(y1, y2);
        modelBoundsBottom = Math.min(y1, y2);
        modelBoundsValid = modelBoundsRight - modelBoundsLeft > 1e-5f
                && modelBoundsTop - modelBoundsBottom > 1e-5f;
    }

    void release() {
        if (released) return;
        released = true;
        pendingRequest = null;
        releaseCurrentModel();
        if (frameworkReady && CubismFramework.isInitialized()) CubismFramework.dispose();
        CubismFramework.cleanUp();
        frameworkReady = false;
    }

    private void initializeFramework() {
        if (frameworkReady && CubismFramework.isInitialized()) return;
        CubismFramework.Option option = new CubismFramework.Option();
        option.logFunction = message -> Log.d(TAG, message);
        option.loggingLevel = CubismFrameworkConfig.LogLevel.INFO;
        option.loadFileFunction = new NativeFileLoader(context);
        CubismFramework.cleanUp();
        if (!CubismFramework.startUp(option)) throw new IllegalStateException("Cubism Framework 启动失败");
        CubismFramework.initialize();
        if (!CubismFramework.isInitialized()) throw new IllegalStateException("Cubism Framework 初始化失败");
        frameworkReady = true;
    }

    private void loadRequestedModel(ModelRequest request) {
        try {
            releaseCurrentModel();
            lastFrameNanos = 0L;
            listener.onStatus("原生渲染：准备加载菜菜女仆主模型…");
            SenLive2DModel next = new SenLive2DModel(CompositeModelRole.MAID_PRIMARY);
            model = next;
            next.setMotionDiagnosticListener(new SenLive2DModel.MotionDiagnosticListener() {
                @Override public void onStep(String label, int index, int total) {
                    listener.onMotionDiagnosticStep(label, index, total);
                }

                @Override public void onComplete(String report) {
                    listener.onMotionDiagnosticComplete(report);
                }
            });
            EvMotionPack evMotionPack = EvMotionPack.load(context.getAssets());
            SenVtsAppearance maidAppearance = SenVtsAppearance.fromEncoded(Arrays.asList(
                    new String[]{"ArtMesh122", "9E9EB2FF|000000FF"},
                    new String[]{"ArtMesh149", "9E9EB2FF|000000FF"}));
            next.load(request.maidModelFile, surfaceWidth, surfaceHeight, textures,
                    listener, request.startupExpressions, maidAppearance,
                    null, request.options, SenOutfitPresets.MAID,
                    evMotionPack);
            next.setTouchFollowEnabled(touchFollowEnabled);
            next.setEarTuning(SenRenderOptions.EAR_SPEED_PERCENT,
                    SenRenderOptions.EAR_AMPLITUDE_PERCENT);
            next.setLipSyncValue(lipSyncValue);
            next.setCompositeTestMotion(compositeTestMotion);
            next.setStaticMode(staticMode);
            listener.onStatus("原生渲染：准备加载 Sen 三配件动力层…");
            SenLive2DModel overlay = new SenLive2DModel(
                    CompositeModelRole.SEN_ACCESSORY_DONOR);
            overlayModel = overlay;
            overlay.load(request.senModelFile, surfaceWidth, surfaceHeight, textures,
                    listener, new ArrayList<>(), null,
                    request.frozenProfile, new SenRenderOptions(false),
                    SenOutfitPresets.MAID, evMotionPack);
            overlay.setStaticMode(staticMode);
            compositeOutfit = request.outfit;
            listener.onReady(readyDetail());
        } catch (Throwable error) {
            releaseCurrentModel();
            listener.onError(error);
        }
    }

    private String readyDetail() {
        String maid = model == null ? "" : model.getAppearanceDetail();
        String sen = overlayModel == null ? "" : overlayModel.getAppearanceDetail();
        return "菜菜女仆×Sen三配件 Cubism 5 已就绪 · GL_LINEAR · GL_MAX_TEXTURE_SIZE="
                + maxTextureSize
                + (maid.isEmpty() ? "" : "\n主模型：" + maid)
                + (sen.isEmpty() ? "" : "\n配件动力层：" + sen);
    }

    private void releaseCurrentModel() {
        modelBoundsValid = false;
        textures.releaseAll();
        if (model != null) {
            model.closeModel();
            model = null;
        }
        if (overlayModel != null) {
            overlayModel.closeModel();
            overlayModel = null;
        }
    }

    private static final class ModelRequest {
        final File maidModelFile;
        final File senModelFile;
        final List<String> startupExpressions;
        final SenVtsAppearance appearance;
        final SenVtsProfile frozenProfile;
        final SenRenderOptions options;
        final CompositeOutfit outfit;

        ModelRequest(File maidModelFile, File senModelFile,
                     List<String> startupExpressions, SenVtsAppearance appearance,
                     SenVtsProfile frozenProfile, SenRenderOptions options,
                     CompositeOutfit outfit) {
            this.maidModelFile = maidModelFile;
            this.senModelFile = senModelFile;
            this.startupExpressions = new ArrayList<>(startupExpressions);
            this.appearance = appearance;
            this.frozenProfile = frozenProfile;
            this.options = options;
            this.outfit = outfit == null
                    ? CompositeOutfit.MAID_WITH_SEN_ACCESSORIES : outfit;
        }
    }
}
