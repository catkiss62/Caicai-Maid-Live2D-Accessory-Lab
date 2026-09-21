package com.catkiss.senlive2dcompanion;

import com.live2d.sdk.cubism.framework.CubismModelSettingJson;
import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.ICubismModelSetting;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.model.CubismModelMultiplyAndScreenColor;
import com.live2d.sdk.cubism.framework.model.CubismModelPartInfo;
import com.live2d.sdk.cubism.framework.model.CubismUserModel;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.ACubismUpdater;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotionManager;
import com.live2d.sdk.cubism.framework.motion.CubismLipSyncUpdater;
import com.live2d.sdk.cubism.framework.motion.CubismMotion;
import com.live2d.sdk.cubism.framework.motion.CubismMotionQueueEntry;
import com.live2d.sdk.cubism.framework.motion.CubismPoseUpdater;
import com.live2d.sdk.cubism.framework.motion.IParameterProvider;
import com.live2d.sdk.cubism.framework.physics.CubismPhysics;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SenLive2DModel extends CubismUserModel {
    interface MotionDiagnosticListener {
        void onStep(String label, int index, int total);
        void onComplete(String report);
    }

    private static final String[] AHOGE_PART_IDS = {
            "Part13", "Part220", "ArtMesh140_Skinning2", "ArtMesh140_Skinning"
    };
    private static final String[] TAIL_PART_IDS = {"Part239"};
    private static final String[] MAIN_TWIN_TAIL_PART_IDS = {
            "Part48", "Part49", "Part56"
    };
    private static final String[] MAIN_HEAD_ANCHOR_PART_IDS = {"Part25", "Part53"};
    private static final String[] MAIN_TAIL_ANCHOR_PART_IDS = {"Part29"};
    private static final String[] ACCESSORY_TAIL_PART_IDS = {"Part239"};
    private static final String[] ACCESSORY_EAR_FIN_PART_IDS = {"Part113"};
    private static final String[] ACCESSORY_AHOGE_PART_IDS = {"Part13", "Part220"};
    private static final String[] ACCESSORY_AHOGE_DIRECT_DRAWABLE_IDS = {
            "ArtMesh140_Skinning2", "ArtMesh140_Skinning"
    };
    private static final String[] COMPOSITE_DRIVE_IDS = {
            "ParamAngleX", "ParamAngleY", "ParamAngleZ",
            "ParamAngleX2", "ParamAngleY2", "ParamAngleZ2",
            "ParamBodyPositionx", "ParamBodyPositiony",
            "ParamBodyPositionX2", "ParamBodyPositionY2",
            "ParamBodyAngleX", "ParamBodyAngleY", "ParamBodyAngleZ",
            "ParamBodyAngleX2", "ParamBodyAngleY2", "ParamBodyAngleZ2",
            "ParamBodyZdown", "ParamBodyZLowerPhysic", "ParamBodyStep",
            "ParamBodyShoulder", "ParamBreath",
            "Boobax1", "Boobax2", "Boobay1", "BoobaY2",
            "larmrotate", "larmrotate2", "larmrotate3", "larmrotate4", "larmrotate5",
            "rarmrotate", "rarmrotate2", "rarmrotate3", "rarmrotate4", "rarmrotate5",
            "Cloth1", "Cloth2", "Cloth3", "Cloth4", "Cloth5", "Cloth6",
            "Cloth7", "Cloth8", "Cloth9", "Cloth10", "Cloth11", "Cloth12", "Cloth13"
    };
    private static final String[] COMPOSITE_TEST_RESET_IDS = {
            "ParamAngleX", "ParamAngleY", "ParamAngleZ",
            "ParamAngleX2", "ParamAngleY2", "ParamAngleZ2",
            "ParamBodyPositionx", "ParamBodyPositiony",
            "ParamBodyPositionX2", "ParamBodyPositionY2",
            "ParamBodyAngleX", "ParamBodyAngleY", "ParamBodyAngleZ",
            "ParamBodyAngleX2", "ParamBodyAngleY2", "ParamBodyAngleZ2",
            "larmrotate", "larmrotate2", "larmrotate3", "larmrotate4", "larmrotate5",
            "rarmrotate", "rarmrotate2", "rarmrotate3", "rarmrotate4", "rarmrotate5"
    };
    private static final float WHITE_SHIRT_POSE_FIRST_KEYFORM = 0.11f;
    private static final float ACTION_FACE_FADE_SECONDS = 0.28f;
    private static final float LOADING_SPIN_SECONDS = 0.90f;
    // Expressions and motions finish by order 310; mouth-driven physics starts at 600.
    // Put lip sync in between so the model's authored MouthOpenY physics receives the voice.
    private static final int LIP_SYNC_UPDATE_ORDER = 550;
    private static final String[] RABBIT_EAR_PHYSICS_OUTPUT_IDS = {
            "ParamL_angle", "ParamR_angle", "ParamR_angle2"
    };
    private static final float EAR_HIDDEN_EYE_DRIVE = -1.05f;
    private static final float EAR_HIDDEN_NINE_AXIS_DRIVE = -6.0f;
    private static final String[] ARM_PHYSICS_OUTPUT_IDS = {
            "ParamBodyShoulder", "ParamBodyShoulder2", "ParamBodyShoulder3",
            "ParamBodyShoulder4", "larmrotate", "larmrotate2", "larmrotate3",
            "larmrotate4", "larmrotate5", "larmrotate7", "larmrotate8",
            "rarmrotate", "rarmrotate2", "rarmrotate3", "rarmrotate4",
            "rarmrotate5", "larmrotate17", "larmrotate18"
    };
    private final Map<String, ACubismMotion> expressions = new HashMap<>();
    private final Map<String, CubismExpressionMotionManager> expressionManagers =
            new LinkedHashMap<>();
    private final Set<String> activeExpressionNames = new LinkedHashSet<>();
    private final Map<String, CubismMotion> nativeMotions = new HashMap<>();
    private final CompositeModelRole compositeRole;
    private final Map<String, Float> compositeDriveValues = new LinkedHashMap<>();
    private final CubismExpressionMotionManager transientExpressionManager =
            new CubismExpressionMotionManager();
    private volatile float lipSyncValue;
    private final SenPerformanceEngine performance = new SenPerformanceEngine();
    private EvMotionPack evMotionPack;
    private EvFaithfulMotionEngine evFaithfulMotion;
    private SenNaturalMotionEngine senNaturalMotion;
    private SenMotionMode motionMode = SenMotionMode.ORIGINAL;
    private boolean autoIdleEnabled;
    private float evBodyFollowStrength = SenRenderOptions.DEFAULT_EV_BODY_FOLLOW_STRENGTH;
    private SenMotionDiagnostic motionDiagnostic;
    private MotionDiagnosticListener motionDiagnosticListener;
    private ICubismModelSetting setting;
    private File homeDirectory;
    private String appearanceDetail = "";
    private boolean hasVtsBaseProfile;
    private boolean geometryDiagnosticsAdded;
    private int[] armPhysicsIndices = new int[0];
    private float[] armPhysicsBaseValues = new float[0];
    private int[] rabbitEarPhysicsIndices = new int[0];
    private float[] isolatedEarValues = new float[0];
    private CubismPhysics isolatedEarPhysics;
    private float[] prePhysicsValues = new float[0];
    private float[] normalPhysicsValues = new float[0];
    private float pendingEarPhysicsDrive;
    private float pendingEarPhysicsMix;
    private boolean pendingEarPhysicsActive;
    private AhogeAnchorPoint ahogeRootAnchor;
    private AhogeAnchorPoint ahogeDirectionAnchor;
    private float referenceDrawableLeft = -1.0f;
    private float referenceDrawableRight = 1.0f;
    private float referenceDrawableTop = 1.0f;
    private float referenceDrawableBottom = -1.0f;
    private String transientExpressionName = "";
    private float transientExpressionRemaining;
    private float transientExpressionDuration = 1.0f;
    private float transientExpressionFadeOut = 0.05f;
    private float loadingSpinTime;
    private boolean glassesEnabled;
    private int[] shapeLockedOutfitDrawables = new int[0];
    private float[][] shapeLockedOutfitVertices = new float[0][];
    private int[] shapeLockedOutfitParameterIndices = new int[0];
    private float[] shapeLockedOutfitParameterValues = new float[0];
    private float[] shapeLockedOutfitParameterRestore = new float[0];
    private SenOutfitPresets.Preset outfitPreset = SenOutfitPresets.MAID;
    private SenRenderOptions renderOptions = new SenRenderOptions(false);
    private boolean[] mainLowLayerFilter;
    private boolean[] mainHighLayerFilter;
    private final EnumMap<CompositeOverlayGroup, boolean[]> compositeGroupFilters =
            new EnumMap<>(CompositeOverlayGroup.class);
    private boolean[] earLeftFilter;
    private boolean[] earRightFilter;
    private final EnumMap<CompositeOverlayGroup, float[]> neutralGroupCenters =
            new EnumMap<>(CompositeOverlayGroup.class);
    private float[] neutralHeadAnchor;
    private float[] neutralTailAnchor;
    private boolean staticMode;
    private CompositeTestMotion compositeTestMotion = CompositeTestMotion.LIVE;
    private float compositeTestMotionElapsed;

    SenLive2DModel() {
        this(CompositeModelRole.RUBY_PRIMARY);
    }

    SenLive2DModel(CompositeModelRole compositeRole) {
        this.compositeRole = compositeRole == null
                ? CompositeModelRole.RUBY_PRIMARY : compositeRole;
    }

    void load(File modelFile, int width, int height, NativeTextureManager textures,
              SenRenderer.Listener listener, List<String> startupExpressions,
              SenVtsAppearance appearance, SenVtsProfile frozenProfile,
              SenRenderOptions requestedOptions,
              SenOutfitPresets.Preset requestedOutfit,
              EvMotionPack requestedEvMotionPack) throws IOException {
        homeDirectory = modelFile.getParentFile();
        if (homeDirectory == null) throw new IOException("model3 所在目录无效");

        listener.onStatus("原生渲染：正在读取 model3…");
        setting = new CubismModelSettingJson(NativeFileLoader.readFile(modelFile));
        if (setting.getJson() == null) throw new IOException("无法解析 model3.json");

        String mocName = setting.getModelFileName();
        if (mocName == null || mocName.isEmpty()) throw new IOException("model3 没有登记 moc3");
        File mocFile = child(mocName);
        listener.onStatus("原生渲染：正在创建 Cubism Core 模型…\n"
                + String.format(java.util.Locale.ROOT, "moc3 %.1f MiB · 已关闭重复一致性检查",
                mocFile.length() / 1048576.0));
        byte[] mocBytes = NativeFileLoader.readFile(mocFile);
        loadModel(mocBytes, false);
        mocBytes = null;
        if (model == null || modelMatrix == null) throw new IOException("Cubism Core 无法创建模型");

        // The 139 MiB Java buffer is no longer needed once Core has created its native model.
        // Reclaim it before decoding 26 textures one by one.
        System.gc();

        hasVtsBaseProfile = compositeRole == CompositeModelRole.SEN_OVERLAY
                && frozenProfile != null;
        outfitPreset = requestedOutfit == null ? SenOutfitPresets.MAID : requestedOutfit;
        renderOptions = requestedOptions == null ? renderOptions : requestedOptions;
        evMotionPack = requestedEvMotionPack;
        if (evMotionPack == null) throw new IOException("E.V动作包未加载");
        evFaithfulMotion = new EvFaithfulMotionEngine(evMotionPack);
        senNaturalMotion = new SenNaturalMotionEngine(evMotionPack);
        motionMode = renderOptions.motionMode;
        autoIdleEnabled = renderOptions.autoIdleEnabled;
        evBodyFollowStrength = renderOptions.evBodyFollowStrength;
        applyMotionModeState();
        SenVtsHotkeySettings vtsHotkeys = SenVtsHotkeySettings.load(homeDirectory);
        // A VTS profile is now the appearance base, not a frozen final frame. Expressions and
        // native physics are loaded in both modes so body motion, ears and tail can stay alive.
        loadExpressions(listener, vtsHotkeys);
        loadNativeMotions(listener, vtsHotkeys);
        registerLipSyncUpdater();
        loadPhysicsAndPose(listener);

        Map<String, Float> layout = new HashMap<>();
        if (setting.getLayoutMap(layout)) modelMatrix.setupFromLayout(layout);
        appearanceDetail = "";
        geometryDiagnosticsAdded = false;
        if (hasVtsBaseProfile) {
            applyFrozenProfile(frozenProfile, listener);
            applyOutfitParameters(outfitPreset, listener);
        }
        resolveArmPhysicsParameters();
        if (compositeRole == CompositeModelRole.SEN_OVERLAY) {
            resolveRabbitEarPhysicsParameters();
        }
        prePhysicsValues = new float[model.getParameterCount()];
        normalPhysicsValues = new float[model.getParameterCount()];
        model.saveParameters();
        applyVtsArtMeshColors(appearance, listener);
        if (compositeRole == CompositeModelRole.SEN_OVERLAY) {
            resolveOutfitShapeLock(outfitPreset, listener);
        }
        updateScheduler.sortUpdatableList();
        updateModelWithOutfitShapeLock();
        captureReferenceDrawableBounds();
        if (compositeRole == CompositeModelRole.SEN_OVERLAY) {
            restoreAhogeAnchors(SenRenderOptions.AHOGE_ANCHOR_JSON);
            applyRuntimeGeometry();
            appendAppearanceDetail("合成覆盖层：Ruby驱动→Sen物理→服装/配件分层");
        } else {
            model.saveParameters();
            appendAppearanceDetail("菜菜女仆主体 · 原装服装与动作结构完整保留");
        }

        listener.onStatus("原生渲染：正在创建 OpenGL 渲染器…\n蒙版模式："
                + SenRenderOptions.MASK_MODE.displayName());
        setupNativeRenderer(width, height);
        resolveCompositeDrawableFilters();
        captureNeutralAttachmentPoints();
        setupTextures(textures, listener);

        for (String expression : startupExpressions) setExpression(expression);
    }

    void reloadRenderer(int width, int height, NativeTextureManager textures,
                        SenRenderer.Listener listener) throws IOException {
        deleteRenderer();
        setupNativeRenderer(width, height);
        setupTextures(textures, listener);
    }

    void update(float deltaSeconds) {
        if (model == null) return;
        if (compositeRole == CompositeModelRole.SEN_OVERLAY) {
            updateCompositeOverlay(deltaSeconds);
            return;
        }
        float frameDelta = staticMode ? 0.0f : deltaSeconds;
        if (motionDiagnostic != null) motionDiagnostic.beforeFrame(deltaSeconds);
        // Always restore the captured appearance base. Dynamic features must never accumulate
        // into part-selection, opacity or colour parameters from a previous frame.
        model.loadParameters();
        captureArmPhysicsBase();
        SenPerformanceEngine.ParameterWriter experimentalWriter =
                new SenPerformanceEngine.ParameterWriter() {
                    @Override public void add(String id, float value) {
                        addParameter(id, value);
                    }

                    @Override public void set(String id, float value) {
                        setParameter(id, value);
                    }
                };
        if (!staticMode && evFaithfulMotion != null) {
            evFaithfulMotion.update(frameDelta, experimentalWriter);
        }
        if (!staticMode && senNaturalMotion != null) {
            senNaturalMotion.update(frameDelta, experimentalWriter);
        }
        // Sen's established layer stays above both experiments. Manual actions, emotions and
        // touch-follow therefore keep their original priority and remain usable in all modes.
        if (!staticMode) performance.update(frameDelta, new SenPerformanceEngine.ParameterWriter() {
            @Override public void add(String id, float value) { addParameter(id, value); }
            @Override public void set(String id, float value) { setParameter(id, value); }
        });
        if (!staticMode) setParameter("ParamBreath", performance.getBreathValue());
        pendingEarPhysicsDrive = performance.getEarPhysicsDrive();
        pendingEarPhysicsMix = performance.getEarPhysicsMix();
        pendingEarPhysicsActive = performance.isEarPhysicsActive();
        if (transientExpressionRemaining > 0.0f) {
            transientExpressionRemaining = Math.max(0.0f,
                    transientExpressionRemaining - deltaSeconds);
            if (transientExpressionRemaining == 0.0f) {
                fadeOutManager(transientExpressionManager, transientExpressionFadeOut);
            }
        }
        updateScheduler.onLateUpdate(model, frameDelta);
        // Outfit selection is an App-owned preset. Expressions, native motions and program
        // actions may animate pose parameters, but they must never alter the selected clothes.
        if (hasVtsBaseProfile) applyOutfitParameters(outfitPreset, null);
        applyCompositeTestMotion(frameDelta);
        if (!staticMode) updateLoadingSpinner(frameDelta);
        updateModelWithOutfitShapeLock();
        if (motionDiagnostic != null) {
            motionDiagnostic.afterFrame(this);
            if (motionDiagnostic.isFinished()) {
                motionDiagnostic = null;
                applyMotionModeState();
            }
        }
    }

    private void updateCompositeOverlay(float deltaSeconds) {
        model.loadParameters();
        applyCompositeDriveValues();
        updateScheduler.onLateUpdate(model, staticMode ? 0.0f : deltaSeconds);
        applyOutfitParameters(SenOutfitPresets.MAID, null);
        // Shared body/arm/cloth physics outputs from Ruby are authoritative. Applying them again
        // after Sen's native physics prevents the two compiled rigs from slowly drifting apart.
        applyCompositeDriveValues();
        updateModelWithOutfitShapeLock();
        applyRuntimeGeometry();
    }

    void setStaticMode(boolean enabled) {
        if (staticMode == enabled) return;
        staticMode = enabled;
        if (enabled) {
            motionManager.stopAllMotions();
            transientExpressionManager.stopAllMotions();
            if (physics != null) physics.reset();
            if (isolatedEarPhysics != null) isolatedEarPhysics.reset();
        }
    }

    void setCompositeTestMotion(CompositeTestMotion motion) {
        CompositeTestMotion next = motion == null ? CompositeTestMotion.LIVE : motion;
        if (compositeTestMotion != next) compositeTestMotionElapsed = 0f;
        compositeTestMotion = next;
    }

    private void applyCompositeTestMotion(float deltaSeconds) {
        if (compositeRole != CompositeModelRole.RUBY_PRIMARY
                || compositeTestMotion == CompositeTestMotion.LIVE) {
            compositeTestMotionElapsed = 0f;
            return;
        }
        compositeTestMotionElapsed += Math.max(0f, deltaSeconds);
        for (String id : COMPOSITE_TEST_RESET_IDS) setParameterDefault(id);
        if (compositeTestMotion == CompositeTestMotion.NEUTRAL) return;

        CompositeTestMotion active = compositeTestMotion;
        float localTime = compositeTestMotionElapsed;
        if (active == CompositeTestMotion.AUTO) {
            int slot = ((int) (localTime / 4f)) % 3;
            localTime %= 4f;
            active = slot == 0 ? CompositeTestMotion.HEAD_SWEEP
                    : slot == 1 ? CompositeTestMotion.BODY_SWEEP
                    : CompositeTestMotion.ARM_SWEEP;
        }
        float wave = (float) Math.sin(localTime * Math.PI * .5);
        if (active == CompositeTestMotion.HEAD_SWEEP) {
            setParameterCentered("ParamAngleX", wave);
            setParameterCentered("ParamAngleX2", wave);
            setParameterCentered("ParamAngleY", wave * .45f);
            setParameterCentered("ParamAngleY2", wave * .45f);
            setParameterCentered("ParamAngleZ", -wave * .35f);
            setParameterCentered("ParamAngleZ2", -wave * .35f);
        } else if (active == CompositeTestMotion.BODY_SWEEP) {
            setParameterCentered("ParamBodyPositionx", wave * .75f);
            setParameterCentered("ParamBodyPositionX2", wave * .75f);
            setParameterCentered("ParamBodyAngleX", wave * .70f);
            setParameterCentered("ParamBodyAngleX2", wave * .70f);
            setParameterCentered("ParamBodyAngleZ", -wave * .65f);
            setParameterCentered("ParamBodyAngleZ2", -wave * .65f);
        } else if (active == CompositeTestMotion.ARM_SWEEP) {
            setParameterCentered("larmrotate", wave * .85f);
            setParameterCentered("larmrotate2", -wave * .65f);
            setParameterCentered("larmrotate4", wave * .55f);
            setParameterCentered("rarmrotate", -wave * .85f);
            setParameterCentered("rarmrotate2", wave * .65f);
            setParameterCentered("rarmrotate4", -wave * .55f);
        }
    }

    private boolean hasCompleteAhogeAnchor() {
        return ahogeRootAnchor != null && ahogeDirectionAnchor != null;
    }

    void draw(CubismMatrix44 matrix) {
        drawWithFilter(matrix, null);
    }

    void drawMainLow(CubismMatrix44 matrix) {
        drawWithFilter(matrix, mainLowLayerFilter);
    }

    void drawMainHigh(CubismMatrix44 matrix) {
        drawWithFilter(matrix, mainHighLayerFilter);
    }

    void drawSenGroup(CubismMatrix44 matrix, CompositeOverlayGroup group) {
        drawWithFilter(matrix, compositeGroupFilters.get(group));
    }

    void drawEarSide(CubismMatrix44 matrix, boolean left) {
        drawWithFilter(matrix, left ? earLeftFilter : earRightFilter);
    }

    private void drawWithFilter(CubismMatrix44 matrix, boolean[] filter) {
        if (model == null || getRenderer() == null) return;
        CubismMatrix44.multiply(modelMatrix.getArray(), matrix.getArray(), matrix.getArray());
        CubismRendererAndroid renderer = getRenderer();
        renderer.setDrawableVisibilityFilter(filter);
        renderer.setMvpMatrix(matrix);
        renderer.drawModel();
    }

    void copyCompositeDriveFrom(SenLive2DModel primary) {
        compositeDriveValues.clear();
        if (primary == null || primary.model == null) return;
        for (String id : COMPOSITE_DRIVE_IDS) {
            float value = primary.getParameterValue(id);
            if (Float.isFinite(value) && hasParameter(id)) compositeDriveValues.put(id, value);
        }
    }

    private void applyCompositeDriveValues() {
        for (Map.Entry<String, Float> entry : compositeDriveValues.entrySet()) {
            setParameter(entry.getKey(), entry.getValue());
        }
    }

    private void resolveCompositeDrawableFilters() {
        int count = model.getDrawableCount();
        if (compositeRole == CompositeModelRole.RUBY_PRIMARY) {
            Set<Integer> twinTails = collectChildDrawables(MAIN_TWIN_TAIL_PART_IDS);
            int[] renderOrders = model.getRenderOrders();
            int cutoff = Integer.MIN_VALUE;
            for (int index : twinTails) {
                if (index >= 0 && index < renderOrders.length) {
                    cutoff = Math.max(cutoff, renderOrders[index]);
                }
            }
            if (cutoff == Integer.MIN_VALUE) cutoff = medianRenderOrder(renderOrders);
            mainLowLayerFilter = new boolean[count];
            mainHighLayerFilter = new boolean[count];
            for (int i = 0; i < count; i++) {
                if (renderOrders[i] <= cutoff) mainLowLayerFilter[i] = true;
                else mainHighLayerFilter[i] = true;
            }
            appendAppearanceDetail("主模型双马尾分层：低层 "
                    + countEnabled(mainLowLayerFilter) + " · 前层 "
                    + countEnabled(mainHighLayerFilter) + " · 阈值 " + cutoff);
            return;
        }

        compositeGroupFilters.clear();
        putCompositeFilter(CompositeOverlayGroup.TAIL, count,
                collectChildDrawables(ACCESSORY_TAIL_PART_IDS), null);
        putCompositeFilter(CompositeOverlayGroup.AHOGE, count,
                collectChildDrawables(ACCESSORY_AHOGE_PART_IDS),
                ACCESSORY_AHOGE_DIRECT_DRAWABLE_IDS);
        Set<Integer> ears = collectChildDrawables(ACCESSORY_EAR_FIN_PART_IDS);
        putCompositeFilter(CompositeOverlayGroup.EAR_FINS, count, ears, null);
        earLeftFilter = new boolean[count];
        earRightFilter = new boolean[count];
        for (int index : ears) {
            if (drawableCenterX(index) < 0f) earLeftFilter[index] = true;
            else earRightFilter[index] = true;
        }

        StringBuilder detail = new StringBuilder("Sen配件网格");
        for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
            if (group == CompositeOverlayGroup.GLOBAL) continue;
            detail.append(' ').append(group.id).append('=')
                    .append(countEnabled(compositeGroupFilters.get(group)));
        }
        appendAppearanceDetail(detail.toString());
    }

    private static int medianRenderOrder(int[] renderOrders) {
        if (renderOrders == null || renderOrders.length == 0) return 0;
        int[] copy = renderOrders.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private void putCompositeFilter(CompositeOverlayGroup group, int count,
                                    Set<Integer> indices, String[] directDrawableIds) {
        boolean[] filter = new boolean[count];
        enableDrawables(filter, indices);
        if (directDrawableIds != null) for (String id : directDrawableIds) {
            int index = findExistingDrawableIndex(id);
            if (index >= 0 && index < count) filter[index] = true;
        }
        compositeGroupFilters.put(group, filter);
    }

    private float drawableCenterX(int index) {
        if (index < 0 || index >= model.getDrawableCount()) return 0f;
        float[] vertices = model.getDrawableVertices(index);
        if (vertices == null || vertices.length < 2) return 0f;
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 1 < vertices.length; i += 2) {
            minimum = Math.min(minimum, vertices[i]);
            maximum = Math.max(maximum, vertices[i]);
        }
        return (minimum + maximum) * .5f;
    }

    private static void enableDrawables(boolean[] filter, Set<Integer> indices) {
        if (filter == null || indices == null) return;
        for (int index : indices) if (index >= 0 && index < filter.length) filter[index] = true;
    }

    private static int countEnabled(boolean[] filter) {
        int count = 0;
        if (filter != null) for (boolean value : filter) if (value) count++;
        return count;
    }

    private static int countDisabled(boolean[] filter) {
        if (filter == null) return 0;
        return filter.length - countEnabled(filter);
    }

    JSONObject buildCompositeInventory() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("drawable_count", model == null ? 0 : model.getDrawableCount());
        JSONObject groups = new JSONObject();
        if (model != null) for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
            if (group == CompositeOverlayGroup.GLOBAL) continue;
            boolean[] filter = compositeGroupFilters.get(group);
            JSONArray entries = new JSONArray();
            if (filter != null) for (int i = 0; i < filter.length; i++) {
                if (!filter[i]) continue;
                JSONObject drawable = new JSONObject();
                drawable.put("index", i);
                drawable.put("id", model.getDrawableId(i).getString());
                int parent = model.getDrawableParentPartIndex(i);
                drawable.put("parent_part", parent >= 0 && parent < model.getPartCount()
                        ? model.getPartId(parent).getString() : JSONObject.NULL);
                drawable.put("opacity", model.getDrawableOpacity(i));
                drawable.put("core_visible", model.getDrawableDynamicFlagIsVisible(i));
                drawable.put("texture_index", model.getDrawableTextureIndex(i));
                JSONArray masks = new JSONArray();
                int[] sourceMasks = model.getDrawableMasks()[i];
                int sourceCount = Math.min(model.getDrawableMaskCounts()[i],
                        sourceMasks == null ? 0 : sourceMasks.length);
                for (int mask = 0; mask < sourceCount; mask++) {
                    int source = sourceMasks[mask];
                    masks.put(new JSONObject()
                            .put("id", model.getDrawableId(source).getString())
                            .put("texture_index", model.getDrawableTextureIndex(source)));
                }
                drawable.put("masks", masks);
                drawable.put("bounds", drawableBoundsJson(i));
                entries.put(drawable);
            }
            groups.put(group.id, entries);
        }
        root.put("groups", groups);

        Set<Integer> part115 = collectChildDrawables(new String[]{"Part115"});
        JSONArray excludedBow = new JSONArray();
        for (int index : part115) {
            excludedBow.put(new JSONObject()
                    .put("id", model.getDrawableId(index).getString())
                    .put("texture_index", model.getDrawableTextureIndex(index))
                    .put("selected", isSelectedAccessoryDrawable(index)));
        }
        root.put("part115_rabbit_ear_bow", new JSONObject()
                .put("policy", "excluded_unless_mask_dependency")
                .put("drawables", excludedBow));

        JSONObject parameters = new JSONObject();
        for (String id : SenOutfitPresets.MAID.parameterOverrides.keySet()) {
            int index = findParameterIndex(id);
            if (index >= 0) parameters.put(id,
                    model.getModel().getParameterViews()[index].getValue());
        }
        root.put("maid_parameters", parameters);
        return root;
    }

    private boolean isSelectedAccessoryDrawable(int index) {
        for (boolean[] filter : compositeGroupFilters.values()) {
            if (filter != null && index >= 0 && index < filter.length && filter[index]) return true;
        }
        return false;
    }

    private void captureNeutralAttachmentPoints() {
        if (model == null) return;
        if (compositeRole == CompositeModelRole.RUBY_PRIMARY) {
            neutralHeadAnchor = centerOf(collectChildDrawables(MAIN_HEAD_ANCHOR_PART_IDS));
            neutralTailAnchor = centerOf(collectChildDrawables(MAIN_TAIL_ANCHOR_PART_IDS));
            return;
        }
        neutralGroupCenters.clear();
        for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
            if (group == CompositeOverlayGroup.GLOBAL) continue;
            neutralGroupCenters.put(group, centerOfFilter(compositeGroupFilters.get(group)));
        }
    }

    float[] currentMainAnchor(CompositeOverlayGroup group) {
        if (model == null || compositeRole != CompositeModelRole.RUBY_PRIMARY) return null;
        return centerOf(collectChildDrawables(group == CompositeOverlayGroup.TAIL
                ? MAIN_TAIL_ANCHOR_PART_IDS : MAIN_HEAD_ANCHOR_PART_IDS));
    }

    float[] neutralMainAnchor(CompositeOverlayGroup group) {
        float[] value = group == CompositeOverlayGroup.TAIL
                ? neutralTailAnchor : neutralHeadAnchor;
        return value == null ? null : value.clone();
    }

    float[] currentGroupCenter(CompositeOverlayGroup group) {
        return centerOfFilter(compositeGroupFilters.get(group));
    }

    float[] neutralGroupCenter(CompositeOverlayGroup group) {
        float[] value = neutralGroupCenters.get(group);
        return value == null ? null : value.clone();
    }

    float[] currentEarCenter(boolean left) {
        return centerOfFilter(left ? earLeftFilter : earRightFilter);
    }

    private float[] centerOfFilter(boolean[] filter) {
        if (filter == null) return null;
        Set<Integer> indices = new LinkedHashSet<>();
        for (int i = 0; i < filter.length; i++) if (filter[i]) indices.add(i);
        return centerOf(indices);
    }

    private float[] centerOf(Set<Integer> indices) {
        if (indices == null || indices.isEmpty()) return null;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int index : indices) {
            if (index < 0 || index >= model.getDrawableCount()) continue;
            float[] vertices = model.getDrawableVertices(index);
            if (vertices == null) continue;
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                minX = Math.min(minX, vertices[i]);
                maxX = Math.max(maxX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                maxY = Math.max(maxY, vertices[i + 1]);
            }
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY)) return null;
        return new float[]{(minX + maxX) * .5f, (minY + maxY) * .5f};
    }

    private JSONObject drawableBoundsJson(int index) throws JSONException {
        float[] vertices = model.getDrawableVertices(index);
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        if (vertices != null) for (int i = 0; i + 1 < vertices.length; i += 2) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            maxY = Math.max(maxY, vertices[i + 1]);
        }
        JSONArray result = new JSONArray();
        if (Float.isFinite(minX)) {
            result.put(minX).put(minY).put(maxX).put(maxY);
        }
        return new JSONObject().put("xyxy", result);
    }

    void copyMvpMatrix(CubismMatrix44 projection, CubismMatrix44 destination) {
        destination.setMatrix(projection);
        CubismMatrix44.multiply(modelMatrix.getArray(), destination.getArray(),
                destination.getArray());
    }

    float getReferenceDrawableLeft() { return referenceDrawableLeft; }
    float getReferenceDrawableRight() { return referenceDrawableRight; }
    float getReferenceDrawableTop() { return referenceDrawableTop; }
    float getReferenceDrawableBottom() { return referenceDrawableBottom; }

    void setExpression(String name) {
        if ("glasses".equals(normalizeExpressionName(name))) {
            glassesEnabled = !glassesEnabled;
            return;
        }
        if (name != null && name.equals(transientExpressionName)) {
            ACubismMotion transientMotion = expressions.get(name);
            if (transientMotion != null) {
                transientExpressionManager.startMotionPriority(transientMotion, 3);
                transientExpressionRemaining = transientExpressionDuration;
            }
            return;
        }
        ACubismMotion motion = expressions.get(name);
        if (motion == null) return;
        CubismExpressionMotionManager manager = expressionManagers.get(name);
        if (manager == null) return;
        if (activeExpressionNames.contains(name)) {
            // A toggle-off is an explicit state change, not another authored transition.
            // Stopping immediately guarantees model.loadParameters() restores the base value
            // on the next frame instead of leaving a fading queue entry apparently enabled.
            manager.stopAllMotions();
            activeExpressionNames.remove(name);
            return;
        }
        String exclusivePrefix = exclusivePresetPrefix(name);
        if (!exclusivePrefix.isEmpty() || isExclusiveProp(name)) {
            for (String activeName : new ArrayList<>(activeExpressionNames)) {
                if (!exclusivePrefix.isEmpty()) {
                    if (!activeName.startsWith(exclusivePrefix)) continue;
                } else if (!isExclusiveProp(activeName)) continue;
                CubismExpressionMotionManager activeManager = expressionManagers.get(activeName);
                if (activeManager != null) activeManager.stopAllMotions();
                activeExpressionNames.remove(activeName);
            }
        }
        manager.startMotionPriority(motion, 3);
        activeExpressionNames.add(name);
    }

    void resetNativePresets() {
        for (CubismExpressionMotionManager manager : expressionManagers.values()) {
            manager.stopAllMotions();
        }
        activeExpressionNames.clear();
        transientExpressionManager.stopAllMotions();
        transientExpressionRemaining = 0.0f;
        glassesEnabled = false;
    }

    void playNativeMotion(String name) {
        CubismMotion motion = nativeMotions.get(name);
        if (motion == null) return;
        motionManager.startMotionPriority(motion, 3);
    }

    void stopNativeMotion() {
        for (CubismMotionQueueEntry entry : motionManager.getCubismMotionQueueEntries()) {
            if (entry != null && !entry.isFinished()) {
                entry.setFadeOut(entry.getMotion().getFadeOutTime());
            }
        }
    }

    void selectEmotion(String name) {
        // Program emotions and authored ZIP switches are independent layers. Selecting one must
        // not silently turn off props or another explicitly enabled ZIP effect.
        performance.selectEmotion(name);
    }

    void playAction(String name) {
        if ("head_pat".equals(name) || "head_pat_confused".equals(name)) {
            prepareForHeadPat();
        }
        performance.playAction(name);
    }

    void triggerEarTwitch() {
        performance.triggerEarTwitch();
    }

    void setEarTuning(float speedPercent, float amplitudePercent) {
        performance.setEarTuning(speedPercent, amplitudePercent);
    }

    void setLipSyncValue(float value) {
        lipSyncValue = Math.max(0.0f, Math.min(1.0f, value));
    }

    void setTouchFollowEnabled(boolean enabled) {
        performance.setTouchFollowEnabled(enabled);
    }

    void setTouchTarget(boolean active, float normalizedX, float normalizedY) {
        performance.setTouchTarget(active, normalizedX, normalizedY);
    }

    void triggerHeadPat(boolean confused) {
        prepareForHeadPat();
        performance.triggerHeadPat(confused);
    }

    void releaseHeadPat() {
        performance.releaseHeadPat();
    }

    void setAutoIdle(boolean enabled) {
        autoIdleEnabled = enabled;
        applyMotionModeState();
    }

    void setMotionMode(SenMotionMode mode) {
        motionMode = mode == null ? SenMotionMode.ORIGINAL : mode;
        applyMotionModeState();
    }

    void setEvBodyFollowStrength(float strength) {
        evBodyFollowStrength = Math.max(0.0f, Math.min(.60f, strength));
        applyMotionModeState();
    }

    void setMotionDiagnosticListener(MotionDiagnosticListener listener) {
        motionDiagnosticListener = listener;
    }

    void startMotionDiagnostic(SenMotionMode mode) {
        if (evMotionPack == null || evFaithfulMotion == null || senNaturalMotion == null) return;
        SenMotionMode requested;
        if (mode == SenMotionMode.SEN_ADAPTED) {
            requested = SenMotionMode.SEN_ADAPTED;
        } else if (mode == SenMotionMode.EV_BODY_ENHANCED) {
            requested = SenMotionMode.EV_BODY_ENHANCED;
        } else {
            requested = SenMotionMode.EV_FAITHFUL;
        }
        if (motionDiagnostic != null) motionDiagnostic.stop();
        performance.setAutoIdle(false);
        motionDiagnostic = new SenMotionDiagnostic(requested, evMotionPack,
                evFaithfulMotion, senNaturalMotion, evBodyFollowStrength,
                new SenMotionDiagnostic.Listener() {
            @Override public void onStep(String label, int index, int total) {
                if (motionDiagnosticListener != null) {
                    motionDiagnosticListener.onStep(label, index, total);
                }
            }

            @Override public void onComplete(String report) {
                if (motionDiagnosticListener != null) {
                    motionDiagnosticListener.onComplete(report);
                }
            }
        });
    }

    void stopMotionDiagnostic() {
        if (motionDiagnostic != null) motionDiagnostic.stop();
    }

    private void applyMotionModeState() {
        boolean diagnosticActive = motionDiagnostic != null && !motionDiagnostic.isFinished();
        if (diagnosticActive) return;
        performance.setAutoIdle(autoIdleEnabled && motionMode == SenMotionMode.ORIGINAL);
        if (evFaithfulMotion != null) {
            evFaithfulMotion.setBodyFollowStrength(
                    motionMode == SenMotionMode.EV_BODY_ENHANCED
                            ? evBodyFollowStrength : 0.0f);
            evFaithfulMotion.setEnabled(autoIdleEnabled
                    && (motionMode == SenMotionMode.EV_FAITHFUL
                    || motionMode == SenMotionMode.EV_BODY_ENHANCED));
        }
        if (senNaturalMotion != null) {
            senNaturalMotion.setDiagnosticMode(false);
            senNaturalMotion.setEnabled(autoIdleEnabled
                    && motionMode == SenMotionMode.SEN_ADAPTED);
        }
    }

    void selectOutfit(SenOutfitPresets.Preset preset) {
        if (model == null || preset == null || !hasVtsBaseProfile) return;
        outfitPreset = preset;
        model.loadParameters();
        applyOutfitParameters(preset, null);
        model.saveParameters();
        applyVtsArtMeshColors(preset.appearance, null);
        resolveOutfitShapeLock(preset, null);
        updateModelWithOutfitShapeLock();
        applyRuntimeGeometry();
    }

    boolean isAutoIdle() {
        return autoIdleEnabled;
    }

    boolean hasParameter(String id) {
        return findParameterIndex(id) >= 0;
    }

    float getParameterValue(String id) {
        int index = findParameterIndex(id);
        return index < 0 ? Float.NaN
                : model.getModel().getParameterViews()[index].getValue();
    }

    int countChangedVisibleDrawables() {
        int changed = 0;
        for (int i = 0; i < model.getDrawableCount(); i++) {
            if (isDrawableVisible(i)
                    && model.getDrawableDynamicFlagVertexPositionsDidChange(i)) changed++;
        }
        return changed;
    }

    int countVisibleDrawables() {
        int visible = 0;
        for (int i = 0; i < model.getDrawableCount(); i++) {
            if (isDrawableVisible(i)) visible++;
        }
        return visible;
    }

    float getCanvasWidth() {
        return model == null ? 1.0f : model.getCanvasWidth();
    }

    float getCanvasHeight() {
        return model == null ? 1.0f : model.getCanvasHeight();
    }

    void fitWidth(float width) {
        if (modelMatrix != null) modelMatrix.setWidth(width);
    }

    void fitHeight(float height) {
        if (modelMatrix != null) modelMatrix.setHeight(height);
    }

    void closeModel() {
        delete();
    }

    String getAppearanceDetail() {
        return appearanceDetail;
    }

    private void loadExpressions(SenRenderer.Listener listener,
                                 SenVtsHotkeySettings hotkeys) throws IOException {
        Map<String, File> sources = new LinkedHashMap<>();
        for (int i = 0; i < setting.getExpressionCount(); i++) {
            String name = setting.getExpressionName(i);
            String fileName = setting.getExpressionFileName(i);
            sources.put(name, child(fileName));
        }
        File[] rootFiles = homeDirectory.listFiles();
        if (rootFiles != null) {
            Arrays.sort(rootFiles, java.util.Comparator.comparing(File::getName));
            for (File file : rootFiles) {
                String fileName = file.getName();
                if (!file.isFile() || !fileName.toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".exp3.json")) continue;
                String name = fileName.replaceFirst("(?i)\\.exp3\\.json$", "");
                sources.putIfAbsent(name, file);
            }
        }
        int count = sources.size();
        if (count <= 0) return;
        listener.onStatus("原生渲染：正在读取表情 0/" + count + "…");
        int i = 0;
        for (Map.Entry<String, File> source : sources.entrySet()) {
            String name = source.getKey();
            String fileName = source.getValue().getName();
            CubismExpressionMotion motion = loadExpression(
                    NativeFileLoader.readFile(source.getValue()));
            if (motion != null) {
                SenVtsHotkeySettings.Rule rule = hotkeys.forFile(fileName);
                if (rule != null && rule.fadeSeconds >= 0.0f) {
                    motion.setFadeInTime(rule.fadeSeconds);
                    motion.setFadeOutTime(rule.fadeSeconds);
                }
                expressions.put(name, motion);
                expressionManagers.put(name, new CubismExpressionMotionManager());
                if (rule != null && rule.deactivateAfterSeconds) {
                    transientExpressionName = name;
                    transientExpressionDuration = Math.max(0.01f,
                            rule.deactivateAfterSecondsAmount);
                    transientExpressionFadeOut = Math.max(0.0f, rule.fadeSeconds);
                }
            }
            i++;
            listener.onStatus("原生渲染：正在读取表情 " + i + "/" + count + "…");
        }
        updateScheduler.addUpdatableList(new ACubismUpdater(300) {
            @Override public void onLateUpdate(
                    com.live2d.sdk.cubism.framework.model.CubismModel target,
                    float deltaTimeSeconds) {
                for (CubismExpressionMotionManager manager : expressionManagers.values()) {
                    manager.updateMotion(target, deltaTimeSeconds);
                }
            }
        });
        updateScheduler.addUpdatableList(new ACubismUpdater(310) {
            @Override public void onLateUpdate(
                    com.live2d.sdk.cubism.framework.model.CubismModel target,
                    float deltaTimeSeconds) {
                transientExpressionManager.updateMotion(target, deltaTimeSeconds);
            }
        });
    }

    private void loadNativeMotions(SenRenderer.Listener listener,
                                   SenVtsHotkeySettings hotkeys) throws IOException {
        List<File> files = new ArrayList<>();
        collectFiles(homeDirectory, ".motion3.json", files);
        int loaded = 0;
        for (File file : files) {
            String baseName = file.getName();
            boolean keyboard = file.getParentFile() != null
                    && "keyboard".equalsIgnoreCase(file.getParentFile().getName());
            if (!keyboard) continue;
            CubismMotion motion = loadMotion(NativeFileLoader.readFile(file));
            if (motion == null) continue;
            SenVtsHotkeySettings.Rule rule = hotkeys.forFile(baseName);
            if (rule != null && rule.fadeSeconds >= 0.0f) {
                motion.setFadeInTime(rule.fadeSeconds);
                motion.setFadeOutTime(rule.fadeSeconds);
            }
            String key = "keyboard/"
                    + baseName.replaceFirst("(?i)\\.motion3\\.json$", "");
            nativeMotions.put(key, motion);
            loaded++;
        }
        if (loaded == 0) return;
        listener.onStatus("原生渲染：已读取原包动作 " + loaded + " 个…");
        updateScheduler.addUpdatableList(new ACubismUpdater(250) {
            @Override public void onLateUpdate(
                    com.live2d.sdk.cubism.framework.model.CubismModel target,
                    float deltaTimeSeconds) {
                motionManager.updateMotion(target, deltaTimeSeconds);
            }
        });
    }

    private void loadPhysicsAndPose(SenRenderer.Listener listener) throws IOException {
        String physicsName = setting.getPhysicsFileName();
        if (physicsName != null && !physicsName.isEmpty()) {
            listener.onStatus("原生渲染：正在读取物理参数…");
            byte[] physicsBytes = NativeFileLoader.readFile(child(physicsName));
            loadPhysics(physicsBytes);
            isolatedEarPhysics = CubismPhysics.create(physicsBytes);
            if (physics != null) {
                // Run the ordinary rig and an independent hidden slow-blink rig from the same
                // pre-physics parameters. Only the latter's three rabbit-ear outputs are copied
                // back, so eyes, head angles, hair, body, tail and every other physics output
                // remain exactly as produced by the ordinary pass.
                updateScheduler.addUpdatableList(new ACubismUpdater(600) {
                    @Override public void onLateUpdate(
                            com.live2d.sdk.cubism.framework.model.CubismModel ignored,
                            float deltaTimeSeconds) {
                        evaluateIsolatedEarPhysics(deltaTimeSeconds);
                    }
                });
            }
        }
        String poseName = setting.getPoseFileName();
        if (poseName != null && !poseName.isEmpty()) {
            listener.onStatus("原生渲染：正在读取部件姿态…");
            loadPose(NativeFileLoader.readFile(child(poseName)));
            if (pose != null) updateScheduler.addUpdatableList(new CubismPoseUpdater(pose));
        }
    }

    private void registerLipSyncUpdater() {
        int index = findParameterIndex("ParamMouthOpenY");
        if (index < 0) return;
        IParameterProvider provider = new IParameterProvider() {
            @Override public boolean update() { return true; }
            @Override public boolean update(float deltaTimeSeconds) { return true; }
            @Override public float getParameter() { return lipSyncValue; }
        };
        updateScheduler.addUpdatableList(new CubismLipSyncUpdater(
                Collections.singletonList(model.getParameterId(index)),
                provider, LIP_SYNC_UPDATE_ORDER));
    }

    private void applyVtsArtMeshColors(SenVtsAppearance appearance,
                                       SenRenderer.Listener listener) {
        if (appearance == null) return;
        if (listener != null) listener.onStatus("正在叠加 VTS 逐部件颜色…");

        CubismModelMultiplyAndScreenColor overrides = model.getOverrideMultiplyAndScreenColor();
        // A preset is a complete desired colour state. Disable every previous override first so
        // bunny-only greys cannot leak into maid/white-shirt after an in-place switch.
        for (int i = 0; i < model.getDrawableCount(); i++) {
            overrides.setDrawableMultiplyColorEnabled(i, false);
            overrides.setDrawableScreenColorEnabled(i, false);
        }
        int applied = 0;
        int missing = 0;
        for (SenVtsAppearance.ArtMeshColor entry : appearance.colors) {
            int index = model.getDrawableIndex(
                    CubismFramework.getIdManager().getId(entry.id));
            if (index < 0) {
                missing++;
                continue;
            }
            overrides.setDrawableMultiplyColor(index,
                    entry.multiply[0], entry.multiply[1], entry.multiply[2], entry.multiply[3]);
            overrides.setDrawableMultiplyColorEnabled(index, true);
            overrides.setDrawableScreenColor(index,
                    entry.screen[0], entry.screen[1], entry.screen[2], entry.screen[3]);
            overrides.setDrawableScreenColorEnabled(index, true);
            applied++;
        }
        if (listener != null) {
            appendAppearanceDetail("内置服装染色 " + applied + "项"
                    + (missing == 0 ? "" : " · 缺失 " + missing + "项"));
        }
    }

    private void applyOutfitParameters(SenOutfitPresets.Preset preset,
                                       SenRenderer.Listener listener) {
        if (preset == null) return;
        int applied = 0;
        int missing = 0;
        for (Map.Entry<String, Float> entry : preset.parameterOverrides.entrySet()) {
            int index = findParameterIndex(entry.getKey());
            if (index < 0) {
                missing++;
                continue;
            }
            model.getModel().getParameterViews()[index].setValue(entry.getValue());
            applied++;
        }
        if (listener != null) {
            appendAppearanceDetail("服装“" + preset.displayName + "”参数 " + applied + "项"
                    + (missing == 0 ? "" : " · 缺失 " + missing + "项"));
        }
    }

    private void resolveOutfitShapeLock(SenOutfitPresets.Preset preset,
                                        SenRenderer.Listener listener) {
        Set<Integer> drawables = new LinkedHashSet<>();
        if (preset != null) {
            drawables.addAll(collectChildDrawables(
                    preset.shapeLockedPartIds.toArray(new String[0])));
            for (String drawableId : preset.shapeLockedDrawableIds) {
                int drawableIndex = findExistingDrawableIndex(drawableId);
                if (drawableIndex >= 0) drawables.add(drawableIndex);
            }
        }
        shapeLockedOutfitDrawables = new int[drawables.size()];
        shapeLockedOutfitVertices = new float[drawables.size()][];
        int output = 0;
        for (int drawable : drawables) {
            shapeLockedOutfitDrawables[output] = drawable;
            shapeLockedOutfitVertices[output] = new float[model.getDrawableVertices(drawable).length];
            output++;
        }
        List<Integer> parameterIndices = new ArrayList<>();
        List<Float> parameterValues = new ArrayList<>();
        if (preset != null) {
            for (Map.Entry<String, Float> entry : preset.shapeLockedParameters.entrySet()) {
                int index = findParameterIndex(entry.getKey());
                if (index < 0) continue;
                parameterIndices.add(index);
                parameterValues.add(entry.getValue());
            }
        }
        shapeLockedOutfitParameterIndices = new int[parameterIndices.size()];
        shapeLockedOutfitParameterValues = new float[parameterIndices.size()];
        shapeLockedOutfitParameterRestore = new float[parameterIndices.size()];
        for (int i = 0; i < parameterIndices.size(); i++) {
            shapeLockedOutfitParameterIndices[i] = parameterIndices.get(i);
            shapeLockedOutfitParameterValues[i] = parameterValues.get(i);
        }
        if (listener != null && preset != null && !drawables.isEmpty()) {
            appendAppearanceDetail("Top 0固定版型 " + drawables.size() + "个网格"
                    + " · 隔离胸型/弹跳 " + parameterIndices.size() + "项");
        }
    }

    private void updateModelWithOutfitShapeLock() {
        if (shapeLockedOutfitDrawables.length == 0
                || shapeLockedOutfitParameterIndices.length == 0) {
            model.update();
            return;
        }

        // First evaluate the selected Top with only its breast-size/bounce inputs held at the
        // neutral authored values. All body, head, arm, breathing and action parameters remain
        // untouched, so the garment still follows the character instead of being screen-fixed.
        for (int i = 0; i < shapeLockedOutfitParameterIndices.length; i++) {
            int index = shapeLockedOutfitParameterIndices[i];
            shapeLockedOutfitParameterRestore[i] =
                    model.getModel().getParameterViews()[index].getValue();
            model.getModel().getParameterViews()[index].setValue(
                    shapeLockedOutfitParameterValues[i]);
        }
        model.update();
        for (int i = 0; i < shapeLockedOutfitDrawables.length; i++) {
            float[] source = model.getDrawableVertices(shapeLockedOutfitDrawables[i]);
            System.arraycopy(source, 0, shapeLockedOutfitVertices[i], 0, source.length);
        }

        // Restore the live physics values and evaluate every other drawable normally. Replacing
        // only the selected Top vertices prevents this compatibility layer from freezing skin,
        // hair, ears, tail or the Bottom=4 garment.
        for (int i = 0; i < shapeLockedOutfitParameterIndices.length; i++) {
            model.getModel().getParameterViews()[shapeLockedOutfitParameterIndices[i]].setValue(
                    shapeLockedOutfitParameterRestore[i]);
        }
        model.update();
        for (int i = 0; i < shapeLockedOutfitDrawables.length; i++) {
            float[] destination = model.getDrawableVertices(shapeLockedOutfitDrawables[i]);
            System.arraycopy(shapeLockedOutfitVertices[i], 0, destination, 0,
                    destination.length);
        }
    }

    private void resolveRabbitEarPhysicsParameters() {
        int count = 0;
        int[] candidates = new int[RABBIT_EAR_PHYSICS_OUTPUT_IDS.length];
        for (String id : RABBIT_EAR_PHYSICS_OUTPUT_IDS) {
            int index = findParameterIndex(id);
            if (index >= 0) candidates[count++] = index;
        }
        rabbitEarPhysicsIndices = Arrays.copyOf(candidates, count);
        isolatedEarValues = new float[count];
        appendAppearanceDetail("九轴兔耳隔离输出 " + count + "/3");
    }

    private void evaluateIsolatedEarPhysics(float deltaTimeSeconds) {
        if (physics == null) return;
        captureParameterValues(prePhysicsValues);
        physics.evaluate(model, deltaTimeSeconds);
        captureParameterValues(normalPhysicsValues);

        if (isolatedEarPhysics != null) {
            restoreParameterValues(prePhysicsValues);
            if (pendingEarPhysicsActive) {
                addParameter("ParamEyeLOpen", EAR_HIDDEN_EYE_DRIVE * pendingEarPhysicsDrive);
                addParameter("ParamEyeROpen", EAR_HIDDEN_EYE_DRIVE * pendingEarPhysicsDrive);
                addParameter("ParamAngleY", EAR_HIDDEN_NINE_AXIS_DRIVE
                        * pendingEarPhysicsDrive);
            }
            isolatedEarPhysics.evaluate(model, deltaTimeSeconds);
            for (int i = 0; i < rabbitEarPhysicsIndices.length; i++) {
                isolatedEarValues[i] = model.getModel().getParameterViews()[
                        rabbitEarPhysicsIndices[i]].getValue();
            }
            restoreParameterValues(normalPhysicsValues);
            if (pendingEarPhysicsActive) {
                for (int i = 0; i < rabbitEarPhysicsIndices.length; i++) {
                    int index = rabbitEarPhysicsIndices[i];
                    float normal = normalPhysicsValues[index];
                    model.getModel().getParameterViews()[index].setValue(
                            normal + (isolatedEarValues[i] - normal) * pendingEarPhysicsMix);
                }
            }
        }
        dampActionArmPhysics();
    }

    private void captureParameterValues(float[] destination) {
        int count = Math.min(destination.length, model.getParameterCount());
        for (int i = 0; i < count; i++) {
            destination[i] = model.getModel().getParameterViews()[i].getValue();
        }
    }

    private void restoreParameterValues(float[] source) {
        int count = Math.min(source.length, model.getParameterCount());
        for (int i = 0; i < count; i++) {
            model.getModel().getParameterViews()[i].setValue(source[i]);
        }
    }

    private void resolveArmPhysicsParameters() {
        int count = 0;
        int[] candidates = new int[ARM_PHYSICS_OUTPUT_IDS.length];
        for (String id : ARM_PHYSICS_OUTPUT_IDS) {
            int index = findParameterIndex(id);
            if (index >= 0) candidates[count++] = index;
        }
        armPhysicsIndices = Arrays.copyOf(candidates, count);
        armPhysicsBaseValues = new float[count];
        appendAppearanceDetail("动作手臂物理 " + count + "项×35%→平滑100%");
    }

    private void captureArmPhysicsBase() {
        for (int i = 0; i < armPhysicsIndices.length; i++) {
            armPhysicsBaseValues[i] = model.getModel().getParameterViews()[
                    armPhysicsIndices[i]].getValue();
        }
    }

    private void dampActionArmPhysics() {
        float gain = performance.getActionArmPhysicsGain();
        if (gain >= .999f) return;
        for (int i = 0; i < armPhysicsIndices.length; i++) {
            int index = armPhysicsIndices[i];
            float current = model.getModel().getParameterViews()[index].getValue();
            float base = armPhysicsBaseValues[i];
            model.getModel().getParameterViews()[index].setValue(
                    base + (current - base) * gain);
        }
    }

    private void updateLoadingSpinner(float deltaSeconds) {
        if (!isNativeExpressionEnabled("loading")) {
            loadingSpinTime = 0.0f;
            return;
        }
        int index = findParameterIndex("Param29");
        if (index < 0) return;
        float minimum = model.getParameterMinimumValue(index);
        float maximum = model.getParameterMaximumValue(index);
        if (maximum - minimum < 0.0001f) return;
        loadingSpinTime = (loadingSpinTime + Math.max(0.0f, deltaSeconds))
                % LOADING_SPIN_SECONDS;
        float phase = loadingSpinTime / LOADING_SPIN_SECONDS;
        // Param28 is the authored visibility switch. CDI identifies Param29 as the second
        // Loading channel; sweeping its full declared range makes the icon use the moc3's own
        // rotation keyforms. The endpoints are authored as the same orientation, so wrapping is
        // continuous and independent of screen zoom, translation or frame rate.
        model.getModel().getParameterViews()[index].setValue(
                minimum + (maximum - minimum) * phase);
    }

    private boolean isNativeExpressionEnabled(String normalizedName) {
        for (String name : activeExpressionNames) {
            if (normalizedName.equals(normalizeExpressionName(name))) return true;
        }
        return false;
    }

    private void applyFrozenProfile(SenVtsProfile profile, SenRenderer.Listener listener) {
        listener.onStatus("正在写入VTS动态外观底座…\n"
                + "保留部件与颜色，并在每帧叠加情绪、动作和物理");

        int modelCount = model.getParameterCount();
        Map<String, Integer> actual = new HashMap<>();
        for (int i = 0; i < modelCount; i++) {
            actual.put(model.getParameterId(i).getString(), i);
        }

        int applied = 0;
        int outsideDeclaredRange = 0;
        int missing = 0;
        for (Map.Entry<String, Float> entry : profile.parameters.entrySet()) {
            Integer index = actual.get(entry.getKey());
            if (index == null) {
                missing++;
                continue;
            }
            float value = entry.getValue();
            if (value < model.getParameterMinimumValue(index)
                    || value > model.getParameterMaximumValue(index)) {
                outsideDeclaredRange++;
            }
            // Deliberately bypass Framework setParameterValue(): it clamps to the parameter's
            // declared range, while VTube Studio captured Warning2=-1 even though its declared
            // minimum is 0. VTS_Add relies on preserving that exact out-of-range result.
            model.getModel().getParameterViews()[index].setValue(value);
            applied++;
        }
        appendAppearanceDetail("VTS底座参数 " + applied + "项"
                + " · 越界直写 " + outsideDeclaredRange + "项"
                + (missing == 0 ? "" : " · 缺失 " + missing + "项"));
    }

    private int findParameterIndex(String id) {
        for (int i = 0; i < model.getParameterCount(); i++) {
            if (id.equals(model.getParameterId(i).getString())) return i;
        }
        return -1;
    }

    private void applyRuntimeGeometry() {
        Set<Integer> ahogeDrawables = collectChildDrawables(AHOGE_PART_IDS);
        Set<Integer> tailDrawables = collectChildDrawables(TAIL_PART_IDS);
        if (!geometryDiagnosticsAdded) {
            appendAppearanceDetail("耳鳍人工网格 0（已撤销）"
                    + " · 呆毛子网格 " + ahogeDrawables.size()
                    + "/可见 " + countVisible(ahogeDrawables)
                    + " · 呆毛模式 " + (hasCompleteAhogeAnchor()
                    ? "固化锚点调整" : "原生保护")
                    + " · 尾巴子网格 " + tailDrawables.size()
                    + "/可见 " + countVisible(tailDrawables));
            geometryDiagnosticsAdded = true;
        }
        // Keep the exact native vertices as the source. The adjusted mode is only allowed to
        // apply one affine transform around the captured barycentric root anchor. With no valid
        // pair of anchors we deliberately fall back to native output instead of guessing.
        if (hasCompleteAhogeAnchor()) {
            applyAnchoredAhogeTransform(ahogeDrawables);
        }
        applyTailMirror(tailDrawables);
    }

    private void skipWhiteShirtPosePreKeyframes() {
        if (outfitPreset != SenOutfitPresets.WHITE_SHIRT) return;
        skipPosePreKeyframes("ParamKeyboardmouse");
        skipPosePreKeyframes("Paramhandle");
    }

    private void skipPosePreKeyframes(String id) {
        int index = findParameterIndex(id);
        if (index < 0) return;
        float value = model.getModel().getParameterViews()[index].getValue();
        // The authored shirt pose does not have a valid action drawable before 0.11. VTS was
        // captured at 14 FPS and naturally stepped over this interval; a 60 Hz renderer lands
        // on 0.10 for one frame. Keep the native curve and timing after its first keyform, but
        // hold the exact idle endpoint until that keyform exists.
        if (value > 0.0f && value < WHITE_SHIRT_POSE_FIRST_KEYFORM) {
            model.getModel().getParameterViews()[index].setValue(0.0f);
        }
    }

    private static void fadeOutManager(
            com.live2d.sdk.cubism.framework.motion.CubismMotionQueueManager manager,
            float seconds) {
        for (CubismMotionQueueEntry entry : manager.getCubismMotionQueueEntries()) {
            if (entry != null && !entry.isFinished()) entry.setFadeOut(seconds);
        }
    }

    private void clearExpressionsForHeadPat() {
        for (String name : new ArrayList<>(activeExpressionNames)) {
            if (isHeadPatRetainedExpression(name)) continue;
            CubismExpressionMotionManager manager = expressionManagers.get(name);
            if (manager != null) fadeOutManager(manager, ACTION_FACE_FADE_SECONDS);
            activeExpressionNames.remove(name);
        }
        fadeOutManager(transientExpressionManager, ACTION_FACE_FADE_SECONDS);
        transientExpressionRemaining = 0.0f;
    }

    private void prepareForHeadPat() {
        clearExpressionsForHeadPat();
        performance.clearEmotionForHeadPat();
    }

    private static boolean isHeadPatRetainedExpression(String name) {
        String normalized = normalizeExpressionName(name);
        return "controller".equals(normalized)
                || "keyboardmouse".equals(normalized)
                || "microphone".equals(normalized)
                || "glasses".equals(normalized)
                || "loading".equals(normalized);
    }

    private static boolean isExclusiveProp(String name) {
        String normalized = normalizeExpressionName(name);
        return "controller".equals(normalized)
                || "keyboardmouse".equals(normalized)
                || "microphone".equals(normalized);
    }

    private static String exclusivePresetPrefix(String name) {
        if (name == null || name.isEmpty()) return "";
        if (name.equals("1爱心") || name.equals("1生气") || name.equals("1红脸")
                || name.equals("1钱钱") || name.equals("1黑脸")
                || name.equals("1星星眼") || name.equals("1流泪")) return "1";
        if (name.startsWith("2")) return "2";
        return "";
    }

    private static String normalizeExpressionName(String name) {
        if (name == null) return "";
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static void collectFiles(File directory, String suffix, List<File> destination) {
        File[] children = directory == null ? null : directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectFiles(child, suffix, destination);
            else if (child.getName().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(suffix.toLowerCase(java.util.Locale.ROOT))) {
                destination.add(child);
            }
        }
    }

    private void restoreAhogeAnchors(String json) {
        ahogeRootAnchor = null;
        ahogeDirectionAnchor = null;
        if (json == null || json.trim().isEmpty() || model == null) return;
        try {
            JSONObject object = new JSONObject(json);
            AhogeAnchorPoint root = AhogeAnchorPoint.fromJson(
                    object.optJSONObject("root"), model);
            AhogeAnchorPoint direction = AhogeAnchorPoint.fromJson(
                    object.optJSONObject("direction"), model);
            if (root != null && direction != null) {
                ahogeRootAnchor = root;
                ahogeDirectionAnchor = direction;
                appendAppearanceDetail("呆毛固定点已恢复 · " + root.drawableId);
            }
        } catch (JSONException ignored) {
            appendAppearanceDetail("呆毛固定点JSON无效，已回退原生保护");
        }
    }

    private static boolean validVertex(float[] vertices, int index) {
        return index >= 0 && index * 2 + 1 < vertices.length;
    }

    private void applyAnchoredAhogeTransform(Set<Integer> candidates) {
        float[] root = ahogeRootAnchor.currentPoint(model);
        float[] direction = ahogeDirectionAnchor.currentPoint(model);
        if (root == null || direction == null) return;
        float axisX = direction[0] - root[0];
        float axisY = direction[1] - root[1];
        float axisLength = (float) Math.hypot(axisX, axisY);
        if (axisLength < 1e-5f) return;
        axisX /= axisLength;
        axisY /= axisLength;
        float perpendicularX = -axisY;
        float perpendicularY = axisX;
        float overall = SenRenderOptions.AHOGE_SCALE_PERCENT / 100.0f;
        float lengthScale = overall * SenRenderOptions.AHOGE_LENGTH_PERCENT / 100.0f;
        float widthScale = overall * SenRenderOptions.AHOGE_WIDTH_PERCENT / 100.0f;
        double radians = Math.toRadians(SenRenderOptions.AHOGE_ROTATION_DEGREES);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float targetRootX = root[0] + SenRenderOptions.AHOGE_OFFSET_X;
        float targetRootY = root[1] + SenRenderOptions.AHOGE_OFFSET_Y;
        for (int index : candidates) {
            if (!isDrawableVisible(index)) continue;
            float[] vertices = model.getDrawableVertices(index);
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                float dx = vertices[i] - root[0];
                float dy = vertices[i + 1] - root[1];
                float along = (dx * axisX + dy * axisY) * lengthScale;
                float across = (dx * perpendicularX + dy * perpendicularY) * widthScale;
                float scaledX = axisX * along + perpendicularX * across;
                float scaledY = axisY * along + perpendicularY * across;
                vertices[i] = targetRootX + scaledX * cos - scaledY * sin;
                vertices[i + 1] = targetRootY + scaledX * sin + scaledY * cos;
            }
        }
    }

    private void captureReferenceDrawableBounds() {
        float left = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float top = Float.NEGATIVE_INFINITY;
        float bottom = Float.POSITIVE_INFINITY;
        for (int drawable = 0; drawable < model.getDrawableCount(); drawable++) {
            if (model.getDrawableOpacity(drawable) <= .001f) continue;
            float[] vertices = model.getDrawableVertices(drawable);
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                left = Math.min(left, vertices[i]);
                right = Math.max(right, vertices[i]);
                top = Math.max(top, vertices[i + 1]);
                bottom = Math.min(bottom, vertices[i + 1]);
            }
        }
        if (Float.isFinite(left) && Float.isFinite(right) && right - left > 1e-5f
                && Float.isFinite(top) && Float.isFinite(bottom) && top - bottom > 1e-5f) {
            referenceDrawableLeft = left;
            referenceDrawableRight = right;
            referenceDrawableTop = top;
            referenceDrawableBottom = bottom;
        }
    }

    private void applyTailMirror(Set<Integer> indices) {
        for (int index : indices) {
            if (!isDrawableVisible(index)) continue;
            float[] vertices = model.getDrawableVertices(index);
            for (int i = 0; i + 1 < vertices.length; i += 2) vertices[i] = -vertices[i];
        }
    }


    private void addParameter(String id, float delta) {
        int index = findParameterIndex(id);
        if (index < 0 || Math.abs(delta) < 0.00001f) return;
        float value = model.getModel().getParameterViews()[index].getValue() + delta;
        float min = model.getParameterMinimumValue(index);
        float max = model.getParameterMaximumValue(index);
        model.getModel().getParameterViews()[index].setValue(Math.max(min, Math.min(max, value)));
    }

    private void setParameter(String id, float value) {
        int index = findParameterIndex(id);
        if (index < 0) return;
        float min = model.getParameterMinimumValue(index);
        float max = model.getParameterMaximumValue(index);
        model.getModel().getParameterViews()[index].setValue(Math.max(min, Math.min(max, value)));
    }

    private void setParameterDefault(String id) {
        int index = findParameterIndex(id);
        if (index < 0) return;
        model.getModel().getParameterViews()[index].setValue(
                model.getParameterDefaultValue(index));
    }

    private void setParameterCentered(String id, float amount) {
        int index = findParameterIndex(id);
        if (index < 0) return;
        float bounded = Math.max(-1f, Math.min(1f, amount));
        float base = model.getParameterDefaultValue(index);
        float end = bounded >= 0f ? model.getParameterMaximumValue(index)
                : model.getParameterMinimumValue(index);
        model.getModel().getParameterViews()[index].setValue(
                base + (end - base) * Math.abs(bounded));
    }

    private Set<Integer> collectChildDrawables(String[] partIds) {
        Set<Integer> result = new LinkedHashSet<>();
        for (String partId : partIds) {
            int partIndex = findExistingPartIndex(partId);
            if (partIndex < 0 || partIndex >= model.getPartsHierarchy().size()) continue;
            model.getPartChildDrawObjects(partIndex);
            CubismModelPartInfo info = model.getPartsHierarchy().get(partIndex);
            result.addAll(info.childDrawObjects.drawableIndices);
        }
        return result;
    }

    private int findExistingPartIndex(String id) {
        for (int i = 0; i < model.getPartCount(); i++) {
            if (id.equals(model.getPartId(i).getString())) return i;
        }
        return -1;
    }

    private int findExistingDrawableIndex(String id) {
        for (int i = 0; i < model.getDrawableCount(); i++) {
            if (id.equals(model.getDrawableId(i).getString())) return i;
        }
        return -1;
    }

    private int countVisible(Set<Integer> indices) {
        int count = 0;
        for (int index : indices) if (isDrawableVisible(index)) count++;
        return count;
    }

    private boolean isDrawableVisible(int index) {
        return index >= 0 && index < model.getDrawableCount()
                && model.getDrawableDynamicFlagIsVisible(index)
                && model.getDrawableOpacity(index) > 0.001f;
    }

    private void appendAppearanceDetail(String detail) {
        if (detail == null || detail.isEmpty()) return;
        appearanceDetail = appearanceDetail.isEmpty() ? detail : appearanceDetail + " · " + detail;
    }

    private void setupNativeRenderer(int width, int height) {
        MaskStats stats = inspectMasks();
        SenMaskMode maskMode = SenRenderOptions.MASK_MODE;
        int requestedBuffers = maskMode == SenMaskMode.DEFAULT_SINGLE
                ? 1 : calculateDynamicBufferCount(stats);

        CubismRendererAndroid nativeRenderer = (CubismRendererAndroid)
                CubismRendererAndroid.create(width, height);
        setupRenderer(nativeRenderer, requestedBuffers);
        if (maskMode == SenMaskMode.HIGH_PRECISION) {
            nativeRenderer.setDrawableClippingMaskBufferSize(
                    SenRenderOptions.HIGH_PRECISION_MASK_SIZE,
                    SenRenderOptions.HIGH_PRECISION_MASK_SIZE);
            nativeRenderer.isUsingHighPrecisionMask(true);
        }

        int drawableBuffers = stats.drawableGroups == 0
                ? 0 : nativeRenderer.getDrawableRenderTextureCount();
        int offscreenBuffers = stats.offscreenGroups == 0
                ? 0 : nativeRenderer.getOffscreenRenderTextureCount();
        appendAppearanceDetail("蒙版" + maskMode.code
                + " · Drawable组 " + stats.drawableGroups
                + "/对象 " + stats.maskedDrawables
                + " · Offscreen组 " + stats.offscreenGroups
                + "/对象 " + stats.maskedOffscreens
                + " · 缓冲 D" + drawableBuffers + "/O" + offscreenBuffers
                + " · 尺寸 " + (maskMode == SenMaskMode.HIGH_PRECISION
                ? SenRenderOptions.HIGH_PRECISION_MASK_SIZE : 256) + "px"
                + " · 高精度 " + (nativeRenderer.isUsingHighPrecisionMask() ? "开" : "关")
                + " · Blend " + (model.isBlendModeEnabled() ? "有" : "无")
                + " · Offscreen总数 " + model.getOffscreenCount());
    }

    private MaskStats inspectMasks() {
        MaskStats drawable = countUniqueMaskGroups(
                model.getDrawableMasks(), model.getDrawableMaskCounts(), model.getDrawableCount());
        MaskStats offscreen = countUniqueMaskGroups(
                model.getOffscreenMasks(), model.getOffscreenMaskCounts(), model.getOffscreenCount());
        return new MaskStats(drawable.drawableGroups, drawable.maskedDrawables,
                offscreen.drawableGroups, offscreen.maskedDrawables);
    }

    private static MaskStats countUniqueMaskGroups(int[][] masks, int[] counts, int objectCount) {
        Set<String> unique = new HashSet<>();
        int maskedObjects = 0;
        int safeCount = Math.min(objectCount,
                Math.min(masks == null ? 0 : masks.length, counts == null ? 0 : counts.length));
        for (int i = 0; i < safeCount; i++) {
            int count = Math.min(Math.max(0, counts[i]), masks[i] == null ? 0 : masks[i].length);
            if (count == 0) continue;
            maskedObjects++;
            int[] canonical = Arrays.copyOf(masks[i], count);
            Arrays.sort(canonical);
            unique.add(Arrays.toString(canonical));
        }
        return new MaskStats(unique.size(), maskedObjects, 0, 0);
    }

    private static int calculateDynamicBufferCount(MaskStats stats) {
        int groups = Math.max(stats.drawableGroups, stats.offscreenGroups);
        if (groups <= 36) return 1;
        // With two or more render textures the official Framework lays out up to 32 contexts
        // per texture. 64 is a safety ceiling for malformed or hostile imported models.
        return Math.min(64, Math.max(2, (groups + 31) / 32));
    }

    private static final class AhogeAnchorPoint {
        final int drawableIndex;
        final String drawableId;
        final int vertex1;
        final int vertex2;
        final int vertex3;
        final float weight1;
        final float weight2;
        final float weight3;
        AhogeAnchorPoint(int drawableIndex, String drawableId,
                         int vertex1, int vertex2, int vertex3,
                         float weight1, float weight2, float weight3) {
            this.drawableIndex = drawableIndex;
            this.drawableId = drawableId;
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            this.vertex3 = vertex3;
            this.weight1 = weight1;
            this.weight2 = weight2;
            this.weight3 = weight3;
        }

        float[] currentPoint(com.live2d.sdk.cubism.framework.model.CubismModel model) {
            if (drawableIndex < 0 || drawableIndex >= model.getDrawableCount()) return null;
            float[] vertices = model.getDrawableVertices(drawableIndex);
            if (!validVertex(vertices, vertex1) || !validVertex(vertices, vertex2)
                    || !validVertex(vertices, vertex3)) return null;
            return new float[]{
                    vertices[vertex1 * 2] * weight1
                            + vertices[vertex2 * 2] * weight2
                            + vertices[vertex3 * 2] * weight3,
                    vertices[vertex1 * 2 + 1] * weight1
                            + vertices[vertex2 * 2 + 1] * weight2
                            + vertices[vertex3 * 2 + 1] * weight3
            };
        }

        static AhogeAnchorPoint fromJson(JSONObject object,
                                         com.live2d.sdk.cubism.framework.model.CubismModel model)
                throws JSONException {
            if (object == null) return null;
            String drawableId = object.optString("drawableId", "");
            int drawableIndex = -1;
            for (int i = 0; i < model.getDrawableCount(); i++) {
                if (drawableId.equals(model.getDrawableId(i).getString())) {
                    drawableIndex = i;
                    break;
                }
            }
            JSONArray vertices = object.optJSONArray("triangleVertexIds");
            JSONArray weights = object.optJSONArray("barycentricWeights");
            if (drawableIndex < 0 || vertices == null || vertices.length() != 3
                    || weights == null || weights.length() != 3) return null;
            AhogeAnchorPoint result = new AhogeAnchorPoint(drawableIndex, drawableId,
                    vertices.getInt(0), vertices.getInt(1), vertices.getInt(2),
                    (float) weights.getDouble(0), (float) weights.getDouble(1),
                    (float) weights.getDouble(2));
            return result.currentPoint(model) == null ? null : result;
        }
    }

    private static final class MaskStats {
        final int drawableGroups;
        final int maskedDrawables;
        final int offscreenGroups;
        final int maskedOffscreens;

        MaskStats(int drawableGroups, int maskedDrawables,
                  int offscreenGroups, int maskedOffscreens) {
            this.drawableGroups = drawableGroups;
            this.maskedDrawables = maskedDrawables;
            this.offscreenGroups = offscreenGroups;
            this.maskedOffscreens = maskedOffscreens;
        }
    }

    private void setupTextures(NativeTextureManager textures,
                               SenRenderer.Listener listener) throws IOException {
        int count = setting.getTextureCount();
        Set<Integer> required = requiredTextureIndices();
        int position = 0;
        for (int i = 0; i < count; i++) {
            if (!required.contains(i)) continue;
            String relative = setting.getTextureFileName(i);
            if (relative == null || relative.isEmpty()) continue;
            position++;
            File textureFile;
            try {
                textureFile = child(relative);
            } catch (IOException missing) {
                listener.onStatus("配件遮罩依赖贴图缺失：slot " + i + " · " + relative
                        + "（已跳过，诊断报告会列出）");
                continue;
            }
            listener.onStatus("原生渲染：正在上传所需贴图 " + position + "/" + required.size()
                    + "…\nGL_LINEAR 单级贴图，未生成 mipmap");
            NativeTextureManager.TextureInfo texture = textures.loadPng(textureFile);
            CubismRendererAndroid renderer = getRenderer();
            renderer.bindTexture(i, texture.id);
            renderer.isPremultipliedAlpha(true);
        }
        appendAppearanceDetail("贴图按需加载 " + position + "/" + count + "槽");
    }

    private Set<Integer> requiredTextureIndices() {
        Set<Integer> result = new LinkedHashSet<>();
        if (model == null) return result;
        if (compositeRole == CompositeModelRole.RUBY_PRIMARY) {
            for (int i = 0; i < setting.getTextureCount(); i++) result.add(i);
            return result;
        }
        Set<Integer> selected = new LinkedHashSet<>();
        for (boolean[] filter : compositeGroupFilters.values()) {
            if (filter == null) continue;
            for (int i = 0; i < filter.length; i++) if (filter[i]) selected.add(i);
        }
        int[][] masks = model.getDrawableMasks();
        int[] counts = model.getDrawableMaskCounts();
        for (int drawable : selected) {
            result.add(model.getDrawableTextureIndex(drawable));
            int[] sources = masks[drawable];
            int count = Math.min(counts[drawable], sources == null ? 0 : sources.length);
            for (int i = 0; i < count; i++) {
                result.add(model.getDrawableTextureIndex(sources[i]));
            }
        }
        return result;
    }

    private File child(String relative) throws IOException {
        File file = new File(homeDirectory, relative);
        String safeRoot = homeDirectory.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(safeRoot) || !file.isFile()) {
            throw new IOException("模型引用的文件不存在或路径不安全：" + relative);
        }
        return file;
    }
}
