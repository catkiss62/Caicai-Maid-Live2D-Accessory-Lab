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
    // Ruby and Sen do not share one rigid head topology. Their former second head anchors were
    // actually authored accessories (Ruby's head ornament and Sen's maid headband), so using them
    // as a two-point frame made the ahoge and ear fins turn opposite to the visible face. Keep the
    // two head groups on the shared compatible parameter drive and only use the independently
    // verified two-point correction for the tail's body frame.
    private static final boolean TAIL_ATTACHMENT_ENABLED = true;

    private final Context context;
    private final Listener listener;
    private final NativeTextureManager textures = new NativeTextureManager();
    private final CubismMatrix44 projection = CubismMatrix44.create();
    private final CubismMatrix44 rubyProjection = CubismMatrix44.create();
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
    private volatile CompositeOutfit compositeOutfit = CompositeOutfit.RUBY_ORIGINAL;
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

    void requestModel(File rubyModelFile, File senModelFile,
                      List<String> startupExpressions, SenVtsAppearance appearance,
                      SenVtsProfile frozenProfile, SenRenderOptions options,
                      CompositeOutfit outfit) {
        if (released) return;
        pendingRequest = new ModelRequest(rubyModelFile, senModelFile, startupExpressions,
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
            root.put("attachment_mode", TAIL_ATTACHMENT_ENABLED
                    ? "tail_two_point_head_parameter_driven" : "parameter_driven");
            root.put("attachment_transform_space", "shared_post_projection");
            root.put("attachment_groups", new JSONObject()
                    .put("ahoge", "shared_parameter_drive_native_rig")
                    .put("ear_fins", "shared_parameter_drive_native_rig")
                    .put("tail", "independent_body_frame_direct")
                    .put("combined_group", false));
            root.put("stage_transform", new JSONObject()
                    .put("scale", stageScale)
                    .put("x", stageTranslateX)
                    .put("y", stageTranslateY)
                    .put("pivot_x", stagePivotX)
                    .put("pivot_y", stagePivotY));
            root.put("ear_right_mode", "sen_native_parameter_discovery");
            root.put("calibration", overlayCalibration.toJsonObject());
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
            return "0.1.9-head-native-drive";
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
        compositeOutfit = outfit == null ? CompositeOutfit.RUBY_ORIGINAL : outfit;
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
                overlayModel.copyCompositeDriveFrom(model);
                overlayModel.update(delta);
            }

            prepareProjection(model, rubyProjection, 1.0f, 0.0f, 0.0f);
            projection.setMatrix(rubyProjection);
            updateInteractionBounds();

            if (showSen) {
                drawOverlayGroup(CompositeOverlayGroup.TAIL);
            }
            model.drawMainLow(rubyProjection);
            if (showSen) {
                drawEarFins();
            }
            model.drawMainHigh(rubyProjection);
            if (showSen) drawOverlayGroup(CompositeOverlayGroup.AHOGE);
        } catch (Throwable error) {
            listener.onError(error);
            releaseCurrentModel();
        }
    }

    private void drawOverlayGroup(CompositeOverlayGroup group) {
        OverlayCalibration calibration = overlayCalibration;
        if (overlayModel == null || !calibration.isVisible(group)) return;
        prepareProjection(overlayModel, senGroupProjection,
                calibration.combinedScale(group),
                calibration.combinedX(group), calibration.combinedY(group));
        if (TAIL_ATTACHMENT_ENABLED && group == CompositeOverlayGroup.TAIL) {
            applyAttachmentCorrection(group, senGroupProjection);
        }
        overlayModel.drawSenGroup(senGroupProjection, group);
    }

    private void drawEarFins() {
        OverlayCalibration calibration = overlayCalibration;
        CompositeOverlayGroup group = CompositeOverlayGroup.EAR_FINS;
        if (overlayModel == null || !calibration.isVisible(group)) return;
        prepareProjection(overlayModel, senGroupProjection,
                calibration.combinedScale(group),
                calibration.combinedX(group), calibration.combinedY(group));
        // The complete Sen ear rig is one authored object. Draw it once so ParamL_angle,
        // ParamR_angle and ParamR_angle2 keep their original independent left/right keyforms.
        // Only the pair rotation survives from the old tuning UI; manual spacing and mirrored
        // per-side rotation belonged to the removed reconstruction path.
        OverlayCalibration.Transform ear = calibration.get(group);
        float[] modelCenter = overlayModel.currentCompositeGroupCenter(group);
        float[] center = pointToClip(overlayModel, senGroupProjection, modelCenter);
        if (center != null) {
            applyRotateAround(senGroupProjection, ear.pairRotation, center[0], center[1]);
        }
        overlayModel.drawSenGroup(senGroupProjection, group);
    }

    private void applyAttachmentCorrection(CompositeOverlayGroup group,
                                           CubismMatrix44 accessoryProjection) {
        if (model == null || overlayModel == null) return;
        float[] mainNow = poseToClip(model, rubyProjection,
                model.currentAttachmentPose(group));
        float[] mainNeutral = poseToClip(model, rubyProjection,
                model.neutralAttachmentPose(group));
        float[] accessoryNow = poseToClip(overlayModel, accessoryProjection,
                overlayModel.currentAttachmentPose(group));
        float[] accessoryNeutral = poseToClip(overlayModel, accessoryProjection,
                overlayModel.neutralAttachmentPose(group));
        if (mainNow == null || mainNeutral == null
                || accessoryNow == null || accessoryNeutral == null) return;

        // Only the confirmed tail path reaches this method. Obtain the maid's neutral -> current
        // body motion, apply it to the donor's calibrated neutral body frame, then replace Sen's
        // incompatible rigid body frame while leaving the tail mesh's local swing intact.
        Similarity2D maidMotion = Similarity2D.between(mainNeutral, mainNow, .35f, 2.5f);
        float[] targetAccessoryPose = maidMotion.transformPose(accessoryNeutral);
        Similarity2D correction = Similarity2D.between(
                accessoryNow, targetAccessoryPose, .35f, 2.5f);
        overlayModel.applyClipTransform(accessoryProjection, correction.toMatrix());
    }

    private float[] poseToClip(SenLive2DModel target, CubismMatrix44 targetProjection,
                               float[] pose) {
        if (pose == null || pose.length < 4) return null;
        float[] origin = pointToClip(target, targetProjection,
                new float[]{pose[0], pose[1]});
        float[] direction = pointToClip(target, targetProjection,
                new float[]{pose[2], pose[3]});
        if (origin == null || direction == null) return null;
        return new float[]{origin[0], origin[1], direction[0], direction[1]};
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

        static Similarity2D between(float[] source, float[] destination,
                                    float minimumScale, float maximumScale) {
            float sourceDx = source[2] - source[0];
            float sourceDy = source[3] - source[1];
            float destinationDx = destination[2] - destination[0];
            float destinationDy = destination[3] - destination[1];
            float sourceLength = (float) Math.hypot(sourceDx, sourceDy);
            float destinationLength = (float) Math.hypot(destinationDx, destinationDy);
            float scale = sourceLength < 1e-5f || destinationLength < 1e-5f
                    ? 1f : destinationLength / sourceLength;
            scale = Math.max(minimumScale, Math.min(maximumScale, scale));
            float sourceAngle = (float) Math.atan2(sourceDy, sourceDx);
            float destinationAngle = (float) Math.atan2(destinationDy, destinationDx);
            float angle = destinationAngle - sourceAngle;
            float a = scale * (float) Math.cos(angle);
            float b = scale * (float) Math.sin(angle);
            float tx = destination[0] - a * source[0] + b * source[1];
            float ty = destination[1] - b * source[0] - a * source[1];
            return new Similarity2D(a, b, tx, ty);
        }

        float[] transformPose(float[] pose) {
            float[] result = new float[4];
            transformPoint(pose[0], pose[1], result, 0);
            transformPoint(pose[2], pose[3], result, 2);
            return result;
        }

        private void transformPoint(float x, float y, float[] destination, int offset) {
            destination[offset] = a * x - b * y + tx;
            destination[offset + 1] = b * x + a * y + ty;
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
            SenLive2DModel next = new SenLive2DModel(CompositeModelRole.RUBY_PRIMARY);
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
            next.load(request.rubyModelFile, surfaceWidth, surfaceHeight, textures,
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
            SenLive2DModel overlay = new SenLive2DModel(CompositeModelRole.SEN_OVERLAY);
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
        String ruby = model == null ? "" : model.getAppearanceDetail();
        String sen = overlayModel == null ? "" : overlayModel.getAppearanceDetail();
        return "菜菜女仆×Sen三配件 Cubism 5 已就绪 · GL_LINEAR · GL_MAX_TEXTURE_SIZE="
                + maxTextureSize
                + (ruby.isEmpty() ? "" : "\n主模型：" + ruby)
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
        final File rubyModelFile;
        final File senModelFile;
        final List<String> startupExpressions;
        final SenVtsAppearance appearance;
        final SenVtsProfile frozenProfile;
        final SenRenderOptions options;
        final CompositeOutfit outfit;

        ModelRequest(File rubyModelFile, File senModelFile,
                     List<String> startupExpressions, SenVtsAppearance appearance,
                     SenVtsProfile frozenProfile, SenRenderOptions options,
                     CompositeOutfit outfit) {
            this.rubyModelFile = rubyModelFile;
            this.senModelFile = senModelFile;
            this.startupExpressions = new ArrayList<>(startupExpressions);
            this.appearance = appearance;
            this.frozenProfile = frozenProfile;
            this.options = options;
            this.outfit = outfit == null ? CompositeOutfit.RUBY_ORIGINAL : outfit;
        }
    }
}
