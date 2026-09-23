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
    private final CubismMatrix44 leftEarProjection = CubismMatrix44.create();
    private final CubismMatrix44 rightEarProjection = CubismMatrix44.create();
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
    private float frameDeltaSeconds = 1.0f / 60.0f;
    private boolean ahogeMotionInitialized;
    private float previousAhogeRootX;
    private float previousAhogeRootY;
    private float previousAhogeHeadAngle;
    private float ahogeLagX;
    private float ahogeLagY;
    private float ahogeLagAngle;
    private float ahogeLagVelocityX;
    private float ahogeLagVelocityY;
    private float ahogeLagAngularVelocity;
    private volatile boolean ahogeMotionResetRequested = true;
    private volatile boolean geometryConstraintEnabled = true;
    private final long[] geometryFrames = new long[2];
    private final float[] maximumEarCorrection = new float[2];
    private final float[] maximumRootCorrection = new float[2];
    private final float[] maximumProximalCorrection = new float[2];
    private float lastEarBeforeSpan;
    private float lastEarAfterSpan;
    private float lastEarAllowedSpan;
    private float lastEarCorrection;
    private float lastHeadTurn;
    private float lastRootBeforeGap;
    private float lastRootCorrection;
    private float lastProximalCorrection;
    private long earMeasurementFailures;
    private long ahogeHairFallbackFrames;

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
        // Stage gestures are camera/layout changes, not character acceleration.
        ahogeMotionResetRequested = true;
    }

    void setOverlayCalibration(OverlayCalibration calibration) {
        overlayCalibration = calibration == null ? OverlayCalibration.defaults() : calibration;
        ahogeMotionResetRequested = true;
    }

    void setCompositeTestMotion(CompositeTestMotion motion) {
        compositeTestMotion = motion == null ? CompositeTestMotion.LIVE : motion;
        if (model != null) model.setCompositeTestMotion(compositeTestMotion);
    }

    void setGeometryConstraintEnabled(boolean enabled) {
        geometryConstraintEnabled = enabled;
        ahogeMotionResetRequested = true;
    }

    void setStaticMode(boolean enabled) {
        staticMode = enabled;
        ahogeMotionResetRequested = true;
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
            root.put("geometry_comparison", new JSONObject()
                    .put("mode", geometryConstraintEnabled ? "actual_mesh" : "v0.1.16_baseline")
                    .put("baseline_frames", geometryFrames[0])
                    .put("new_frames", geometryFrames[1])
                    .put("ear_actual_outer_span_before_clip", lastEarBeforeSpan)
                    .put("ear_actual_outer_span_allowed_clip", lastEarAllowedSpan)
                    .put("ear_actual_outer_span_after_clip", lastEarAfterSpan)
                    .put("ear_translation_per_side_clip", lastEarCorrection)
                    .put("normalized_head_turn", lastHeadTurn)
                    .put("maximum_ear_translation_baseline_clip", maximumEarCorrection[0])
                    .put("maximum_ear_translation_new_clip", maximumEarCorrection[1])
                    .put("root_to_hair_target_before_clip", lastRootBeforeGap)
                    .put("root_to_hair_target_after_clip", lastRootCorrection)
                    .put("maximum_root_gap_baseline_clip", maximumRootCorrection[0])
                    .put("maximum_root_gap_new_clip", maximumRootCorrection[1])
                    .put("near_root_vertex_correction_model", lastProximalCorrection)
                    .put("maximum_near_root_vertex_correction_new_model",
                            maximumProximalCorrection[1])
                    .put("ear_measurement_failures", earMeasurementFailures)
                    .put("ahoge_hair_fallback_frames", ahogeHairFallbackFrames));
            root.put("attachment_mode", TRIANGLE_CARRIER_ATTACHMENT_ENABLED
                    ? (geometryConstraintEnabled ? "actual_mesh_ear_span_and_top_hair_root"
                    : "v0.1.16_independent_face_mesh_pins")
                    : "neutral_accessory_projection_only");
            root.put("attachment_transform_space", "shared_post_projection");
            root.put("sen_rigid_parameter_drive", false);
            root.put("sen_local_accessory_dynamics", true);
            root.put("attachment_groups", new JSONObject()
                    .put("ahoge", geometryConstraintEnabled
                            ? "maid_top_hair_root_with_near_mesh_lock_and_free_tip"
                            : "v0.1.16_face_mesh_top_center_pin")
                    .put("ear_fins_screen_left", geometryConstraintEnabled
                            ? "actual_drawable_outer_span_constraint" : "v0.1.16_face_mesh_left_pin")
                    .put("ear_fins_screen_right", geometryConstraintEnabled
                            ? "actual_drawable_outer_span_constraint" : "v0.1.16_face_mesh_right_pin")
                    .put("tail", "maid_body_neutral_to_current_on_accessory_bind_pose")
                    .put("combined_group", false));
            root.put("ear_pair_constraint", new JSONObject()
                    .put("horizontal_local_response", .28)
                    .put("vertical_local_response", .60)
                    .put("actual_outer_span_over_neutral", 1.01)
                    .put("maximum_head_turn_narrowing", .16)
                    .put("enabled", geometryConstraintEnabled));
            root.put("ahoge_root_lock", new JSONObject()
                    .put("root_source", "Sen ArtMesh151 vertex 8 barycentric anchor")
                    .put("moving_target", "maid_top_hair_triangle_with_neutral_bind_offset")
                    .put("native_near_root_weight", 0)
                    .put("locked_root_zone_fraction", .12)
                    .put("native_blend_complete_fraction", .45)
                    .put("secondary_motion", "smooth_tip_weighted_velocity_spring")
                    .put("stage_gesture_drives_physics", false)
                    .put("enabled", geometryConstraintEnabled));
            root.put("ear_visibility_source", "sen_accessory_only_not_headwear_opacity");
            root.put("ear_neutral_pose_policy", "inherit_v0.1.12_pair_projection_identity_offsets");
            root.put("ear_layer_policy", "independent_material_skinning_section_slot_per_side");
            root.put("ahoge_layer_policy", "draw_immediately_in_front_of_headwear");
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
            return "0.1.18-actual-mesh-comparison";
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
        frameDeltaSeconds = delta;

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
        if (calibration.isVisible(CompositeOverlayGroup.EAR_FINS)) {
            prepareEarPairProjections(calibration);
        }
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
        if (group == CompositeOverlayGroup.AHOGE) {
            lastProximalCorrection = geometryConstraintEnabled
                    ? overlayModel.lockAhogeProximalVertices() : 0f;
            int index = geometryConstraintEnabled ? 1 : 0;
            maximumProximalCorrection[index] = Math.max(
                    maximumProximalCorrection[index], lastProximalCorrection);
        }
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
        prepareEarPairProjections(calibration);
        drawEarFinSide(true, calibration);
        drawEarFinSide(false, calibration);
    }

    private void drawEarFinSide(boolean screenLeft, OverlayCalibration calibration) {
        overlayModel.drawSenEarSide(screenLeft ? leftEarProjection : rightEarProjection,
                screenLeft);
    }

    private void prepareEarPairProjections(OverlayCalibration calibration) {
        float[] neutralLeft = prepareOneEarProjection(true, calibration, leftEarProjection);
        float[] neutralRight = prepareOneEarProjection(false, calibration, rightEarProjection);
        float[] left = overlayModel.currentEarClipBounds(leftEarProjection, true);
        float[] right = overlayModel.currentEarClipBounds(rightEarProjection, false);
        if (left == null || right == null || neutralLeft == null || neutralRight == null) {
            earMeasurementFailures++;
            return;
        }
        float neutralSpan = neutralRight[2] - neutralLeft[0];
        float beforeSpan = right[2] - left[0];
        if (!Float.isFinite(neutralSpan) || neutralSpan <= 0f || beforeSpan <= 0f) {
            earMeasurementFailures++;
            return;
        }

        float[] leftNow = triangleToClip(model, maidProjection,
                model.currentHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, true));
        float[] leftNeutral = triangleToClip(model, maidProjection,
                model.neutralHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, true));
        float[] rightNow = triangleToClip(model, maidProjection,
                model.currentHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, false));
        float[] rightNeutral = triangleToClip(model, maidProjection,
                model.neutralHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, false));
        float perspective = 1f;
        if (leftNow != null && leftNeutral != null && rightNow != null && rightNeutral != null) {
            float neutralWidth = triangleCenterX(rightNeutral) - triangleCenterX(leftNeutral);
            float currentWidth = triangleCenterX(rightNow) - triangleCenterX(leftNow);
            if (neutralWidth > 1e-5f) {
                perspective = clamp(currentWidth / neutralWidth, .78f, 1f);
            }
        }
        lastHeadTurn = model.horizontalHeadTurnMagnitude();
        // This parameter only sets an envelope; the actual correction is decided from rendered
        // fin vertices. At a full side turn, roots should sit closer than in the neutral pose.
        perspective = Math.min(perspective, 1f - .16f * lastHeadTurn);
        float allowedSpan = neutralSpan * 1.01f * perspective;
        float correction = geometryConstraintEnabled
                ? Math.max(0f, (beforeSpan - allowedSpan) * .5f) : 0f;
        if (correction > 0f) {
            overlayModel.applyClipTransform(leftEarProjection,
                    new Similarity2D(1f, 0f, correction, 0f).toMatrix());
            overlayModel.applyClipTransform(rightEarProjection,
                    new Similarity2D(1f, 0f, -correction, 0f).toMatrix());
        }
        lastEarBeforeSpan = beforeSpan;
        lastEarAllowedSpan = allowedSpan;
        lastEarCorrection = correction;
        float[] afterLeft = overlayModel.currentEarClipBounds(leftEarProjection, true);
        float[] afterRight = overlayModel.currentEarClipBounds(rightEarProjection, false);
        if (afterLeft == null || afterRight == null) {
            earMeasurementFailures++;
            lastEarAfterSpan = 0f;
        } else {
            lastEarAfterSpan = afterRight[2] - afterLeft[0];
        }
        int index = geometryConstraintEnabled ? 1 : 0;
        geometryFrames[index]++;
        maximumEarCorrection[index] = Math.max(maximumEarCorrection[index], correction);
    }

    private float[] prepareOneEarProjection(boolean screenLeft,
                                            OverlayCalibration calibration,
                                            CubismMatrix44 destination) {
        CompositeOverlayGroup group = CompositeOverlayGroup.EAR_FINS;
        prepareProjection(overlayModel, destination,
                calibration.combinedScale(group),
                calibration.combinedX(group), calibration.combinedY(group));
        // Preserve the confirmed v0.1.12 neutral pose: both side passes begin from exactly the
        // same pair projection and pair-rotation pivot. Side fine-tuning defaults to identity.
        OverlayCalibration.Transform ear = calibration.get(group);
        float[] pairCenterModel = overlayModel.currentCompositeGroupCenter(group);
        float[] pairCenter = pointToClip(overlayModel, destination, pairCenterModel);
        if (pairCenter != null) {
            applyRotateAround(destination, ear.pairRotation,
                    pairCenter[0], pairCenter[1]);
        }
        applyEarFineTune(destination, screenLeft, calibration.getEarFineTune(screenLeft));
        float[] neutral = overlayModel.neutralEarClipBounds(destination, screenLeft);
        if (TRIANGLE_CARRIER_ATTACHMENT_ENABLED) {
            if (geometryConstraintEnabled) {
                applyMaidEarCarrierMotion(screenLeft, destination);
            } else {
                applyMaidHeadPinMotionLegacy(group, screenLeft, destination,
                        .65f, .78f, 1.22f);
            }
        }
        return neutral;
    }

    private void applyEarFineTune(CubismMatrix44 projection, boolean screenLeft,
                                  OverlayCalibration.EarFineTune fineTune) {
        if (fineTune == null) return;
        float[] modelCenter = overlayModel.currentEarSideCenter(screenLeft);
        float[] center = pointToClip(overlayModel, projection, modelCenter);
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
        overlayModel.applyClipTransform(projection, transform);
    }

    private void applyMaidEarCarrierMotion(boolean screenLeft,
                                           CubismMatrix44 accessoryProjection) {
        if (model == null || overlayModel == null) return;
        float[] leftNow = triangleToClip(model, maidProjection,
                model.currentHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, true));
        float[] leftNeutral = triangleToClip(model, maidProjection,
                model.neutralHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, true));
        float[] rightNow = triangleToClip(model, maidProjection,
                model.currentHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, false));
        float[] rightNeutral = triangleToClip(model, maidProjection,
                model.neutralHeadPinTriangle(CompositeOverlayGroup.EAR_FINS, false));
        Similarity2D grossHeadMotion = currentGrossHeadMotion(.65f, .78f, 1.22f);
        if (leftNow == null || leftNeutral == null || rightNow == null
                || rightNeutral == null || grossHeadMotion == null) return;

        float leftNeutralX = triangleCenterX(leftNeutral);
        float leftNeutralY = triangleCenterY(leftNeutral);
        float rightNeutralX = triangleCenterX(rightNeutral);
        float rightNeutralY = triangleCenterY(rightNeutral);
        float[] rigidLeft = grossHeadMotion.transformPoint(leftNeutralX, leftNeutralY);
        float[] rigidRight = grossHeadMotion.transformPoint(rightNeutralX, rightNeutralY);

        // Side-face deformation is useful, but the face surface stretches much more than an ear
        // root should. Keep a small amount of horizontal parallax and more vertical correction.
        float desiredLeftX = rigidLeft[0]
                + (triangleCenterX(leftNow) - rigidLeft[0]) * .28f;
        float desiredLeftY = rigidLeft[1]
                + (triangleCenterY(leftNow) - rigidLeft[1]) * .60f;
        float desiredRightX = rigidRight[0]
                + (triangleCenterX(rightNow) - rigidRight[0]) * .28f;
        float desiredRightY = rigidRight[1]
                + (triangleCenterY(rightNow) - rigidRight[1]) * .60f;

        // Perspective may bring the fins closer together, but may never stretch their roots
        // farther apart than the rigid head pose plus a tiny tolerance.
        float rigidDx = rigidRight[0] - rigidLeft[0];
        float rigidDy = rigidRight[1] - rigidLeft[1];
        float desiredDx = desiredRightX - desiredLeftX;
        float desiredDy = desiredRightY - desiredLeftY;
        float rigidDistance = (float) Math.hypot(rigidDx, rigidDy);
        float desiredDistance = (float) Math.hypot(desiredDx, desiredDy);
        float maximumDistance = rigidDistance * 1.03f;
        if (desiredDistance > maximumDistance && desiredDistance > 1e-6f) {
            float centerX = (desiredLeftX + desiredRightX) * .5f;
            float centerY = (desiredLeftY + desiredRightY) * .5f;
            float factor = maximumDistance / desiredDistance;
            desiredDx *= factor;
            desiredDy *= factor;
            desiredLeftX = centerX - desiredDx * .5f;
            desiredLeftY = centerY - desiredDy * .5f;
            desiredRightX = centerX + desiredDx * .5f;
            desiredRightY = centerY + desiredDy * .5f;
        }

        Similarity2D pinnedMotion = grossHeadMotion.mappingPoint(
                screenLeft ? leftNeutralX : rightNeutralX,
                screenLeft ? leftNeutralY : rightNeutralY,
                screenLeft ? desiredLeftX : desiredRightX,
                screenLeft ? desiredLeftY : desiredRightY);
        overlayModel.applyClipTransform(accessoryProjection, pinnedMotion.toMatrix());
    }

    private void applyMaidCarrierMotion(CompositeOverlayGroup group,
                                        CubismMatrix44 accessoryProjection) {
        if (model == null || overlayModel == null) return;
        if (group == CompositeOverlayGroup.AHOGE) {
            if (geometryConstraintEnabled) {
                applyMaidAhogeRootMotion(accessoryProjection);
            } else {
                applyMaidHeadPinMotionLegacy(group, true, accessoryProjection,
                        .85f, .70f, 1.35f);
            }
            return;
        }
        float[] mainNow = triangleToClip(model, maidProjection,
                model.currentCarrierTriangle(group));
        float[] mainNeutral = triangleToClip(model, maidProjection,
                model.neutralCarrierTriangle(group));
        if (mainNow == null || mainNeutral == null) return;

        // Apply the maid carrier's observed neutral-to-current motion directly to the already
        // calibrated accessory bind pose. We deliberately never inspect or cancel a Sen head/body
        // carrier here: doing so lets the donor's incompatible rig re-enter the root transform and
        // was the source of the previous reversed, delayed and over-amplified movement.
        Similarity2D maidMotion = Similarity2D.betweenTriangle(
                mainNeutral, mainNow, .50f, 1.80f);
        overlayModel.applyClipTransform(accessoryProjection, maidMotion.toMatrix());
    }

    private Similarity2D currentGrossHeadMotion(float scaleResponse,
                                                float minimumScale,
                                                float maximumScale) {
        float[] headNow = triangleToClip(model, maidProjection,
                model.currentCarrierTriangle(CompositeOverlayGroup.EAR_FINS));
        float[] headNeutral = triangleToClip(model, maidProjection,
                model.neutralCarrierTriangle(CompositeOverlayGroup.EAR_FINS));
        if (headNow == null || headNeutral == null) return null;
        return Similarity2D.betweenTriangle(
                        headNeutral, headNow, .50f, 1.80f)
                .withScaleResponse(scaleResponse, minimumScale, maximumScale);
    }

    /** Locks the actual Sen root anchor to the maid head, then adds physics only past the root. */
    private void applyMaidAhogeRootMotion(CubismMatrix44 accessoryProjection) {
        float[] hairNow = triangleToClip(model, maidProjection,
                model.currentCarrierTriangle(CompositeOverlayGroup.AHOGE));
        float[] hairNeutral = triangleToClip(model, maidProjection,
                model.neutralCarrierTriangle(CompositeOverlayGroup.AHOGE));
        Similarity2D grossHeadMotion = currentGrossHeadMotion(.85f, .70f, 1.35f);
        float[] donorRootNow = pointToClip(overlayModel, accessoryProjection,
                overlayModel.currentAhogeRootPoint());
        float[] donorRootNeutral = pointToClip(overlayModel, accessoryProjection,
                overlayModel.neutralAhogeRootPoint());
        if (hairNow == null || hairNeutral == null || grossHeadMotion == null
                || donorRootNow == null || donorRootNeutral == null) {
            ahogeHairFallbackFrames++;
            applyMaidHeadPinMotionLegacy(CompositeOverlayGroup.AHOGE, true,
                    accessoryProjection, .85f, .70f, 1.35f);
            return;
        }

        // The bind offset remains exactly as calibrated at rest, but its moving origin is the
        // actual top-hair mesh rather than a proxy point on the face.
        float bindOffsetX = donorRootNeutral[0] - triangleCenterX(hairNeutral);
        float bindOffsetY = donorRootNeutral[1] - triangleCenterY(hairNeutral);
        float transformedOffsetX = grossHeadMotion.a * bindOffsetX
                - grossHeadMotion.b * bindOffsetY;
        float transformedOffsetY = grossHeadMotion.b * bindOffsetX
                + grossHeadMotion.a * bindOffsetY;
        float targetRootX = triangleCenterX(hairNow) + transformedOffsetX;
        float targetRootY = triangleCenterY(hairNow) + transformedOffsetY;
        lastRootBeforeGap = (float) Math.hypot(
                targetRootX - donorRootNow[0], targetRootY - donorRootNow[1]);
        Similarity2D rootLockedMotion = grossHeadMotion.mappingPoint(
                donorRootNow[0], donorRootNow[1], targetRootX, targetRootY);
        overlayModel.applyClipTransform(accessoryProjection, rootLockedMotion.toMatrix());
        float[] rootAfter = pointToClip(overlayModel, accessoryProjection,
                overlayModel.currentAhogeRootPoint());
        if (rootAfter != null) {
            lastRootCorrection = (float) Math.hypot(
                    targetRootX - rootAfter[0], targetRootY - rootAfter[1]);
            maximumRootCorrection[1] = Math.max(maximumRootCorrection[1], lastRootCorrection);
        }

        applyAhogeSecondaryMotion(accessoryProjection, targetRootX, targetRootY,
                grossHeadMotion.angleRadians());
    }

    /** The accepted v0.1.16 head path, kept intact for one-tap on-device comparison. */
    private void applyMaidHeadPinMotionLegacy(CompositeOverlayGroup group,
                                              boolean screenLeft,
                                              CubismMatrix44 accessoryProjection,
                                              float scaleResponse,
                                              float minimumScale,
                                              float maximumScale) {
        float[] pinNow = triangleToClip(model, maidProjection,
                model.currentHeadPinTriangle(group, screenLeft));
        float[] pinNeutral = triangleToClip(model, maidProjection,
                model.neutralHeadPinTriangle(group, screenLeft));
        Similarity2D grossHeadMotion = currentGrossHeadMotion(
                scaleResponse, minimumScale, maximumScale);
        if (pinNow == null || pinNeutral == null || grossHeadMotion == null) return;
        float[] donorRootNeutral = group == CompositeOverlayGroup.AHOGE
                ? pointToClip(overlayModel, accessoryProjection,
                        overlayModel.neutralAhogeRootPoint()) : null;
        Similarity2D pinnedMotion = grossHeadMotion.mappingPoint(
                triangleCenterX(pinNeutral), triangleCenterY(pinNeutral),
                triangleCenterX(pinNow), triangleCenterY(pinNow));
        overlayModel.applyClipTransform(accessoryProjection, pinnedMotion.toMatrix());
        if (group == CompositeOverlayGroup.AHOGE && donorRootNeutral != null) {
            float[] hairNow = triangleToClip(model, maidProjection,
                    model.currentCarrierTriangle(group));
            float[] hairNeutral = triangleToClip(model, maidProjection,
                    model.neutralCarrierTriangle(group));
            float[] rootAfter = pointToClip(overlayModel, accessoryProjection,
                    overlayModel.currentAhogeRootPoint());
            if (hairNow != null && hairNeutral != null && rootAfter != null) {
                float offsetX = donorRootNeutral[0] - triangleCenterX(hairNeutral);
                float offsetY = donorRootNeutral[1] - triangleCenterY(hairNeutral);
                float targetX = triangleCenterX(hairNow) + grossHeadMotion.a * offsetX
                        - grossHeadMotion.b * offsetY;
                float targetY = triangleCenterY(hairNow) + grossHeadMotion.b * offsetX
                        + grossHeadMotion.a * offsetY;
                lastRootCorrection = (float) Math.hypot(
                        targetX - rootAfter[0], targetY - rootAfter[1]);
                lastRootBeforeGap = lastRootCorrection;
                maximumRootCorrection[0] = Math.max(
                        maximumRootCorrection[0], lastRootCorrection);
            }
        }
    }

    private void applyAhogeSecondaryMotion(CubismMatrix44 accessoryProjection,
                                           float rootX, float rootY, float headAngle) {
        float dt = Math.max(1.0f / 240.0f, Math.min(.05f, frameDeltaSeconds));
        if (staticMode || ahogeMotionResetRequested || !ahogeMotionInitialized) {
            ahogeMotionInitialized = true;
            ahogeMotionResetRequested = false;
            previousAhogeRootX = rootX;
            previousAhogeRootY = rootY;
            previousAhogeHeadAngle = headAngle;
            ahogeLagX = ahogeLagY = ahogeLagAngle = 0f;
            ahogeLagVelocityX = ahogeLagVelocityY = ahogeLagAngularVelocity = 0f;
            return;
        }

        float rootVelocityX = (rootX - previousAhogeRootX) / dt;
        float rootVelocityY = (rootY - previousAhogeRootY) / dt;
        float angularVelocity = wrapRadians(headAngle - previousAhogeHeadAngle) / dt;
        previousAhogeRootX = rootX;
        previousAhogeRootY = rootY;
        previousAhogeHeadAngle = headAngle;

        float targetLagX = clamp(-rootVelocityX * .045f, -.035f, .035f);
        float targetLagY = clamp(-rootVelocityY * .040f, -.030f, .030f);
        float targetLagAngle = clamp(-angularVelocity * .070f, -.14f, .14f);
        float stiffness = 30f;
        float damping = (float) Math.exp(-9f * dt);
        ahogeLagVelocityX = (ahogeLagVelocityX
                + (targetLagX - ahogeLagX) * stiffness * dt) * damping;
        ahogeLagVelocityY = (ahogeLagVelocityY
                + (targetLagY - ahogeLagY) * stiffness * dt) * damping;
        ahogeLagAngularVelocity = (ahogeLagAngularVelocity
                + (targetLagAngle - ahogeLagAngle) * stiffness * dt) * damping;
        ahogeLagX += ahogeLagVelocityX * dt;
        ahogeLagY += ahogeLagVelocityY * dt;
        ahogeLagAngle += ahogeLagAngularVelocity * dt;

        float[] localLag = clipVectorToModel(overlayModel, accessoryProjection,
                ahogeLagX, ahogeLagY);
        if (localLag != null) {
            overlayModel.applyAhogeSecondaryMotion(
                    localLag[0], localLag[1], ahogeLagAngle);
        }
    }

    private float[] clipVectorToModel(SenLive2DModel target, CubismMatrix44 targetProjection,
                                      float clipX, float clipY) {
        target.copyMvpMatrix(targetProjection, interactionMvp);
        float[] matrix = interactionMvp.getArray();
        float determinant = matrix[0] * matrix[5] - matrix[4] * matrix[1];
        if (Math.abs(determinant) < 1e-8f) return null;
        return new float[]{
                (matrix[5] * clipX - matrix[4] * clipY) / determinant,
                (-matrix[1] * clipX + matrix[0] * clipY) / determinant
        };
    }

    private static float wrapRadians(float value) {
        while (value > Math.PI) value -= (float) (Math.PI * 2.0);
        while (value < -Math.PI) value += (float) (Math.PI * 2.0);
        return value;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float triangleCenterX(float[] triangle) {
        return (triangle[0] + triangle[2] + triangle[4]) / 3f;
    }

    private static float triangleCenterY(float[] triangle) {
        return (triangle[1] + triangle[3] + triangle[5]) / 3f;
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

        Similarity2D withScaleResponse(float response, float minimum, float maximum) {
            float scale = (float) Math.hypot(a, b);
            if (!Float.isFinite(scale) || scale < 1e-6f) {
                return new Similarity2D(1f, 0f, tx, ty);
            }
            float adjusted = 1f + (scale - 1f) * Math.max(0f, Math.min(1f, response));
            adjusted = Math.max(minimum, Math.min(maximum, adjusted));
            return new Similarity2D(a * adjusted / scale, b * adjusted / scale, tx, ty);
        }

        Similarity2D mappingPoint(float sourceX, float sourceY,
                                  float destinationX, float destinationY) {
            return new Similarity2D(a, b,
                    destinationX - a * sourceX + b * sourceY,
                    destinationY - b * sourceX - a * sourceY);
        }

        float[] transformPoint(float x, float y) {
            return new float[]{a * x - b * y + tx, b * x + a * y + ty};
        }

        float angleRadians() {
            return (float) Math.atan2(b, a);
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
        ahogeMotionInitialized = false;
        ahogeMotionResetRequested = true;
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
