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

    // Exact meshes confirmed by the first device inventory. Part13/Part220 are valid names, but
    // recursively collecting their hierarchy also pulls dormant texture_16 ear variants into the
    // ahoge pass. Keep geometry and rendering on the same strict six-segment whitelist.
    private static final String[] AHOGE_DRAWABLE_IDS = {
            "ArtMesh151", "ArtMesh160", "ArtMesh189",
            "ArtMesh190", "ArtMesh191", "ArtMesh192"
    };
    private static final String[] TAIL_PART_IDS = {"Part239"};
    // The side bows are the authored perspective-correct mounting points for the fish fins.
    // Part names contain editor-side left/right labels, but runtime assignment uses neutral X so
    // the UI and diagnostics always mean the viewer's screen-left and screen-right.
    private static final String[] MAID_SIDE_BOW_PART_IDS = {"Part30", "Part31"};
    private static final String[] MAID_HEADWEAR_PART_IDS = {"Part9"};
    private static final String[] MAID_TOP_HAIR_PART_IDS = {
            "ArtMesh363_Skinning", "ArtMesh264_Skinning"
    };
    // One group now represents one authored hair material/skinning section, not the broad
    // recursive "front hair/back hair" folders. The latter swallowed hundreds of independently
    // layered meshes in v0.1.14. IDs that are the same visible section in different colour/style
    // branches are deliberately merged into one calibration step.
    private static final MaidLayerGroupSpec[] MAID_HEAD_LAYER_GROUP_SPECS = {
            new MaidLayerGroupSpec("后发左1", new String[]{"ArtMesh254_Skinning"}),
            new MaidLayerGroupSpec("左双马尾", new String[]{"ArtMesh255_Skinning"}),
            new MaidLayerGroupSpec("后发左2", new String[]{"ArtMesh256_Skinning"}),
            new MaidLayerGroupSpec("后发右1", new String[]{"ArtMesh257_Skinning"}),
            new MaidLayerGroupSpec("右双马尾", new String[]{"ArtMesh258_Skinning"}),
            new MaidLayerGroupSpec("后发图层178", new String[]{"ArtMesh259_Skinning"}),
            new MaidLayerGroupSpec("右双马尾发丝", new String[]{"ArtMesh260_Skinning"}),
            new MaidLayerGroupSpec("后发右1发丝", new String[]{"ArtMesh261_Skinning"}),
            new MaidLayerGroupSpec("后发中间", new String[]{"ArtMesh262_Skinning"}),
            new MaidLayerGroupSpec("后发中间2", new String[]{"ArtMesh349_Skinning"}),
            new MaidLayerGroupSpec("后发左3", new String[]{"ArtMesh263_Skinning"}),
            new MaidLayerGroupSpec("顶部头发", new String[]{
                    "ArtMesh363_Skinning", "ArtMesh264_Skinning"}),
            new MaidLayerGroupSpec("头发图层155", new String[]{"ArtMesh265_Skinning"}),
            new MaidLayerGroupSpec("头发图层154", new String[]{"ArtMesh266_Skinning"}),
            new MaidLayerGroupSpec("头发图层153", new String[]{"ArtMesh267_Skinning"}),
            new MaidLayerGroupSpec("头发图层152", new String[]{"ArtMesh268_Skinning"}),
            new MaidLayerGroupSpec("头发图层151", new String[]{"ArtMesh269_Skinning"}),
            new MaidLayerGroupSpec("头发图层150", new String[]{"ArtMesh270_Skinning"}),
            new MaidLayerGroupSpec("头发图层149", new String[]{"ArtMesh271_Skinning"}),
            new MaidLayerGroupSpec("头发图层148", new String[]{"ArtMesh272_Skinning"}),
            new MaidLayerGroupSpec("头发图层147", new String[]{
                    "ArtMesh335_Skinning", "ArtMesh360_Skinning"}),
            new MaidLayerGroupSpec("头发图层146", new String[]{"ArtMesh274_Skinning"}),
            new MaidLayerGroupSpec("头发图层145", new String[]{
                    "ArtMesh364_Skinning", "ArtMesh275_Skinning"}),
            new MaidLayerGroupSpec("侧发左1", new String[]{
                    "ArtMesh276_Skinning", "ArtMesh287_Skinning"}),
            new MaidLayerGroupSpec("侧发左2", new String[]{
                    "ArtMesh284_Skinning", "ArtMesh288_Skinning"}),
            new MaidLayerGroupSpec("侧发左3", new String[]{
                    "ArtMesh278_Skinning", "ArtMesh289_Skinning"}),
            new MaidLayerGroupSpec("侧发右1", new String[]{
                    "ArtMesh279_Skinning", "ArtMesh290_Skinning"}),
            new MaidLayerGroupSpec("侧发右2", new String[]{
                    "ArtMesh334_Skinning", "ArtMesh291_Skinning"}),
            new MaidLayerGroupSpec("散发1", new String[]{
                    "ArtMesh281_Skinning", "ArtMesh292_Skinning"}),
            new MaidLayerGroupSpec("散发2", new String[]{
                    "ArtMesh280_Skinning", "ArtMesh293_Skinning"}),
            new MaidLayerGroupSpec("散发3", new String[]{
                    "ArtMesh283_Skinning", "ArtMesh294_Skinning"}),
            new MaidLayerGroupSpec("头发小节1", new String[]{
                    "ArtMesh336_Skinning", "ArtMesh362_Skinning",
                    "ArtMesh361_Skinning", "ArtMesh295_Skinning"}),
            new MaidLayerGroupSpec("头发小节2", new String[]{
                    "ArtMesh285_Skinning", "ArtMesh296_Skinning"}),
            new MaidLayerGroupSpec("头发图层114", new String[]{
                    "ArtMesh365_Skinning", "ArtMesh286_Skinning"}),
            new MaidLayerGroupSpec("头发图层组3", new String[]{
                    "ArtMesh366_Skinning", "ArtMesh297_Skinning"}),
            new MaidLayerGroupSpec("两侧蝴蝶结", MAID_SIDE_BOW_PART_IDS),
            new MaidLayerGroupSpec("头饰", MAID_HEADWEAR_PART_IDS)
    };
    // Stable carrier meshes used for cross-model attachment. They are deliberately taken from
    // face/body geometry, never from authored ornaments: a fixed triangle keeps vertex identity
    // across every frame and therefore supplies translation, rotation and scale during large
    // motions without depending on a jumping drawable bounding box.
    private static final String[] MAID_HEAD_CARRIER_PART_IDS = {"Part25"};
    private static final String[] SEN_HEAD_CARRIER_PART_IDS = {"Part39"};
    private static final String[] MAID_BODY_CARRIER_PART_IDS = {"Part29"};
    private static final String[] SEN_BODY_CARRIER_PART_IDS = {"Part93"};
    private static final String[] ACCESSORY_TAIL_PART_IDS = {"Part239"};
    // Part113 = 兔耳. These six meshes are the confirmed visible seed, not a left-ear list.
    // The compiled moc3 may keep the other side outside Part113 while reusing the same atlas
    // region. resolveRabbitEarDrawables() expands this seed by observing the three authored ear
    // parameters instead of guessing a side from model-local X coordinates.
    private static final String[] EAR_FIN_DRAWABLE_IDS = {
            "ArtMesh631", "ArtMesh1095", "ArtMesh1021",
            "ArtMesh146", "ArtMesh629", "ArtMesh1019"
    };
    private static final String[] COMPOSITE_TEST_RESET_IDS = {
            "ParamAngleX", "ParamAngleX3", "ParamAngleY", "ParamAngleZ",
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
    private float[] neutralAhogeRoot;
    private float[] neutralAhogeDirection;
    private float[] neutralLeftEarBounds;
    private float[] neutralRightEarBounds;
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
    private boolean[] mainLayerRangeFilter;
    private boolean[] earFinScreenLeftFilter;
    private boolean[] earFinScreenRightFilter;
    private float angryMouthGuardSeconds;
    private OverlayCalibration.AhogeShape ahogeShape =
            new OverlayCalibration.AhogeShape(1f, 1f, 0f);
    private final EnumMap<CompositeOverlayGroup, boolean[]> compositeGroupFilters =
            new EnumMap<>(CompositeOverlayGroup.class);
    private final Map<String, Set<String>> rabbitEarDiscoveryHits = new LinkedHashMap<>();
    private String rabbitEarDiscoveryMode = "unresolved";
    private final CubismMatrix44 drawMvpMatrix = CubismMatrix44.create();
    private final CubismMatrix44 earBoundsMvpMatrix = CubismMatrix44.create();
    private MeshAnchorFrame headCarrierFrame;
    private MeshAnchorFrame bodyCarrierFrame;
    private MeshAnchorFrame ahogeHairCarrierFrame;
    private AhogeAnchorPoint selectedMaidHairPoint;
    private MeshAnchorFrame ahogeHeadPinFrame;
    private MeshAnchorFrame screenLeftEarHeadPinFrame;
    private MeshAnchorFrame screenRightEarHeadPinFrame;
    private MeshAnchorFrame screenLeftBowCarrierFrame;
    private MeshAnchorFrame screenRightBowCarrierFrame;
    private final List<MaidLayerGroup> maidLayerGroups = new ArrayList<>();
    private final List<MaidLayerSlot> maidLayerSlots = new ArrayList<>();
    private int defaultEarLayerSlotIndex = -1;
    private int defaultAhogeLayerSlotIndex = -1;
    private boolean staticMode;
    private CompositeTestMotion compositeTestMotion = CompositeTestMotion.LIVE;
    private float compositeTestMotionElapsed;

    SenLive2DModel() {
        this(CompositeModelRole.MAID_PRIMARY);
    }

    SenLive2DModel(CompositeModelRole compositeRole) {
        this.compositeRole = compositeRole == null
                ? CompositeModelRole.MAID_PRIMARY : compositeRole;
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

        hasVtsBaseProfile = compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR
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
        if (compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR) {
            resolveRabbitEarPhysicsParameters();
        }
        prePhysicsValues = new float[model.getParameterCount()];
        normalPhysicsValues = new float[model.getParameterCount()];
        model.saveParameters();
        applyVtsArtMeshColors(appearance, listener);
        if (compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR) {
            resolveOutfitShapeLock(outfitPreset, listener);
        }
        updateScheduler.sortUpdatableList();
        updateModelWithOutfitShapeLock();
        captureReferenceDrawableBounds();
        if (compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR) {
            restoreAhogeAnchors(SenRenderOptions.AHOGE_ANCHOR_JSON);
            applyRuntimeGeometry();
            appendAppearanceDetail("Sen 三配件动力层：女仆参数驱动→Sen局部物理→载体锚点校正");
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
        if (compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR) {
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
        // The authored angry pose crossfades from the previous face for half a second. During
        // that transition, keep the mouth closed so an intermediate open-mouth keyform cannot
        // flash before the intended expression settles.
        if (angryMouthGuardSeconds > 0f) {
            setParameter("ParamMouthOpenY", 0f);
            angryMouthGuardSeconds = Math.max(0f, angryMouthGuardSeconds - frameDelta);
        }
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
        float frameDelta = staticMode ? 0.0f : Math.max(0.0f, Math.min(0.05f, deltaSeconds));
        // The maid and Sen do not share a compatible rigid head/body parameter space. Always
        // restore the donor's own neutral baseline and evaluate only accessory-local dynamics.
        // Root translation/rotation/scale is transferred one-way from the maid's actual meshes
        // by SenRenderer after both models have updated.
        model.loadParameters();
        if (!staticMode) {
            performance.updateAccessoryEarOnly(frameDelta);
            pendingEarPhysicsDrive = performance.getEarPhysicsDrive();
            pendingEarPhysicsMix = performance.getEarPhysicsMix();
            pendingEarPhysicsActive = performance.isEarPhysicsActive();
        } else {
            pendingEarPhysicsDrive = 0.0f;
            pendingEarPhysicsMix = 0.0f;
            pendingEarPhysicsActive = false;
        }
        updateScheduler.onLateUpdate(model, frameDelta);
        applyOutfitParameters(SenOutfitPresets.MAID, null);
        updateModelWithOutfitShapeLock();
        applyRuntimeGeometry();
    }

    /** Exposes the donor's authored ear twitch drive to the sweep diagnostic only. */
    float currentAccessoryEarPhysicsDrive() {
        return pendingEarPhysicsDrive;
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
        if (compositeRole != CompositeModelRole.MAID_PRIMARY
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
            int slot = ((int) (localTime / 4f)) % 5;
            localTime %= 4f;
            active = slot == 0 ? CompositeTestMotion.HEAD_X_SWEEP
                    : slot == 1 ? CompositeTestMotion.HEAD_Y_SWEEP
                    : slot == 2 ? CompositeTestMotion.HEAD_Z_SWEEP
                    : slot == 3 ? CompositeTestMotion.BODY_SWEEP
                    : CompositeTestMotion.ARM_SWEEP;
        }
        float wave = (float) Math.sin(localTime * Math.PI * .5);
        if (active == CompositeTestMotion.HEAD_X_SWEEP) {
            // This maid rig exposes visible horizontal head motion on AngleX3. AngleX is a
            // capture/auxiliary parameter and barely moves the rendered head.
            setParameterCentered("ParamAngleX3", wave);
        } else if (active == CompositeTestMotion.HEAD_Y_SWEEP) {
            setParameterCentered("ParamAngleY2", wave);
        } else if (active == CompositeTestMotion.HEAD_Z_SWEEP) {
            setParameterCentered("ParamAngleZ", wave);
            setParameterCentered("ParamAngleZ2", wave);
        } else if (active == CompositeTestMotion.HEAD_SWEEP) {
            setParameterCentered("ParamAngleX3", wave);
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

    void drawMainRenderRange(CubismMatrix44 matrix, int lowerExclusive,
                             int upperInclusive) {
        if (model == null) return;
        int count = model.getDrawableCount();
        if (mainLayerRangeFilter == null || mainLayerRangeFilter.length != count) {
            mainLayerRangeFilter = new boolean[count];
        }
        Arrays.fill(mainLayerRangeFilter, false);
        int[] renderOrders = model.getRenderOrders();
        for (int i = 0; i < count && i < renderOrders.length; i++) {
            int order = renderOrders[i];
            mainLayerRangeFilter[i] = order > lowerExclusive && order <= upperInclusive;
        }
        if (countEnabled(mainLayerRangeFilter) > 0) {
            drawWithFilter(matrix, mainLayerRangeFilter);
        }
    }

    void drawSenGroup(CubismMatrix44 matrix, CompositeOverlayGroup group) {
        drawWithFilter(matrix, compositeGroupFilters.get(group));
    }

    void drawSenEarSide(CubismMatrix44 matrix, boolean screenLeft) {
        drawWithFilter(matrix, screenLeft
                ? earFinScreenLeftFilter : earFinScreenRightFilter);
    }

    void setAhogeShape(OverlayCalibration.AhogeShape shape) {
        ahogeShape = shape == null ? new OverlayCalibration.AhogeShape(1.15f, 1.59f, -14f) : shape;
    }

    private void drawWithFilter(CubismMatrix44 matrix, boolean[] filter) {
        if (model == null || getRenderer() == null) return;
        // A frame can draw the same model several times (low layer, high layer and accessories).
        // Never multiply the caller's projection in place: doing so made every later pass apply
        // modelMatrix again, which displaced front hair and could push the model out of clip space
        // after zooming.
        drawMvpMatrix.setMatrix(matrix);
        CubismMatrix44.multiply(modelMatrix.getArray(), drawMvpMatrix.getArray(),
                drawMvpMatrix.getArray());
        CubismRendererAndroid renderer = getRenderer();
        renderer.setDrawableVisibilityFilter(filter);
        renderer.setMvpMatrix(drawMvpMatrix);
        renderer.drawModel();
    }

    private void resolveCompositeDrawableFilters() {
        int count = model.getDrawableCount();
        if (compositeRole == CompositeModelRole.MAID_PRIMARY) {
            mainLayerRangeFilter = new boolean[count];
            resolveMaidLayerGroups();
            MaidLayerSlot ear = resolveMaidLayerSlot(
                    CompositeOverlayGroup.EAR_FINS, true, 0);
            MaidLayerSlot ahoge = resolveMaidLayerSlot(
                    CompositeOverlayGroup.AHOGE, true, 0);
            appendAppearanceDetail("主模型粗粒度插层：" + maidLayerSlots.size()
                    + " 个位置 · 耳鳍默认 "
                    + (ear == null ? "缺失" : ear.label + " / 阈值 " + ear.threshold)
                    + " · 呆毛默认 "
                    + (ahoge == null ? "缺失" : ahoge.label + " / 阈值 "
                    + ahoge.threshold));
            return;
        }

        compositeGroupFilters.clear();
        Set<Integer> tail = retainVisibleDrawables(
                collectChildDrawables(ACCESSORY_TAIL_PART_IDS));
        putCompositeFilter(CompositeOverlayGroup.TAIL, count,
                tail, null);
        Set<Integer> ahoge = collectExistingDrawables(AHOGE_DRAWABLE_IDS);
        putCompositeFilter(CompositeOverlayGroup.AHOGE, count, ahoge, null);
        Set<Integer> earSeeds = collectExistingDrawables(EAR_FIN_DRAWABLE_IDS);
        Set<Integer> ears = resolveRabbitEarDrawables(earSeeds);
        putCompositeFilter(CompositeOverlayGroup.EAR_FINS, count, ears, null);
        splitEarFinFilters(ears, count);
        StringBuilder detail = new StringBuilder("Sen配件网格");
        for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
            if (group == CompositeOverlayGroup.GLOBAL) continue;
            detail.append(' ').append(group.id).append('=')
                    .append(countEnabled(compositeGroupFilters.get(group)));
        }
        appendAppearanceDetail(detail.toString());
        appendAppearanceDetail("耳鳍：原生参数差分 " + ears.size()
                + "（种子 " + earSeeds.size() + "）· 画面左 "
                + countEnabled(earFinScreenLeftFilter) + " / 画面右 "
                + countEnabled(earFinScreenRightFilter) + " · 双侧独立绘制");
        appendAppearanceDetail("尾巴：活动网格 " + tail.size() + " · 已排除隐藏变体");
    }

    private void resolveMaidLayerGroups() {
        maidLayerGroups.clear();
        maidLayerSlots.clear();
        defaultEarLayerSlotIndex = -1;
        defaultAhogeLayerSlotIndex = -1;
        if (model == null) return;

        int[] renderOrders = model.getRenderOrders();
        int maximumRenderOrder = Integer.MIN_VALUE;
        for (int order : renderOrders) maximumRenderOrder = Math.max(maximumRenderOrder, order);
        for (MaidLayerGroupSpec spec : MAID_HEAD_LAYER_GROUP_SPECS) {
            Set<Integer> drawables = collectChildDrawables(spec.partIds);
            if (drawables.isEmpty()) continue;
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            for (int drawable : drawables) {
                if (drawable < 0 || drawable >= renderOrders.length) continue;
                minimum = Math.min(minimum, renderOrders[drawable]);
                maximum = Math.max(maximum, renderOrders[drawable]);
            }
            if (minimum == Integer.MAX_VALUE) continue;
            maidLayerGroups.add(new MaidLayerGroup(spec.label, spec.partIds,
                    drawables, minimum, maximum));
        }
        Collections.sort(maidLayerGroups, (left, right) -> {
            int byMinimum = Integer.compare(left.minimumRenderOrder, right.minimumRenderOrder);
            if (byMinimum != 0) return byMinimum;
            return Integer.compare(left.maximumRenderOrder, right.maximumRenderOrder);
        });

        Map<Integer, MaidLayerSlot> uniqueSlots = new LinkedHashMap<>();
        for (MaidLayerGroup group : maidLayerGroups) {
            int threshold = group.minimumRenderOrder - 1;
            uniqueSlots.putIfAbsent(threshold, new MaidLayerSlot(threshold));
        }
        Set<Integer> sideBows = collectChildDrawables(MAID_SIDE_BOW_PART_IDS);
        int earThreshold = minimumRenderOrder(sideBows, renderOrders);
        if (earThreshold != Integer.MAX_VALUE) {
            earThreshold--;
            uniqueSlots.putIfAbsent(earThreshold, new MaidLayerSlot(earThreshold));
        }
        Set<Integer> headwear = collectChildDrawables(MAID_HEADWEAR_PART_IDS);
        int ahogeThreshold = maximumRenderOrder(headwear, renderOrders);
        if (ahogeThreshold != Integer.MAX_VALUE) {
            uniqueSlots.putIfAbsent(ahogeThreshold, new MaidLayerSlot(ahogeThreshold));
        }
        if (maximumRenderOrder != Integer.MIN_VALUE) {
            uniqueSlots.putIfAbsent(maximumRenderOrder,
                    new MaidLayerSlot(maximumRenderOrder));
        }
        maidLayerSlots.addAll(uniqueSlots.values());
        Collections.sort(maidLayerSlots,
                (left, right) -> Integer.compare(left.threshold, right.threshold));

        for (MaidLayerSlot slot : maidLayerSlots) {
            for (MaidLayerGroup group : maidLayerGroups) {
                if (group.minimumRenderOrder <= slot.threshold) slot.behind = group;
                else if (slot.front == null) slot.front = group;
            }
            if (slot.front == null) {
                slot.label = "最前（所有女仆部件之前）";
            } else if (slot.behind == null) {
                slot.label = "最后（在「" + slot.front.label + "」之后）";
            } else {
                slot.label = "「" + slot.behind.label + "」与「"
                        + slot.front.label + "」之间";
            }
            if (slot.threshold == ahogeThreshold) {
                slot.label = "头饰前一层（呆毛默认）";
            }
        }
        defaultEarLayerSlotIndex = indexOfLayerThreshold(earThreshold);
        defaultAhogeLayerSlotIndex = indexOfLayerThreshold(ahogeThreshold);
        if (defaultEarLayerSlotIndex < 0 && !maidLayerSlots.isEmpty()) {
            defaultEarLayerSlotIndex = maidLayerSlots.size() / 2;
        }
        if (defaultAhogeLayerSlotIndex < 0 && !maidLayerSlots.isEmpty()) {
            defaultAhogeLayerSlotIndex = maidLayerSlots.size() - 1;
        }
    }

    private static int minimumRenderOrder(Set<Integer> drawables, int[] renderOrders) {
        int result = Integer.MAX_VALUE;
        if (drawables == null || renderOrders == null) return result;
        for (int drawable : drawables) {
            if (drawable >= 0 && drawable < renderOrders.length) {
                result = Math.min(result, renderOrders[drawable]);
            }
        }
        return result;
    }

    private static int maximumRenderOrder(Set<Integer> drawables, int[] renderOrders) {
        int result = Integer.MIN_VALUE;
        if (drawables == null || renderOrders == null) return Integer.MAX_VALUE;
        for (int drawable : drawables) {
            if (drawable >= 0 && drawable < renderOrders.length) {
                result = Math.max(result, renderOrders[drawable]);
            }
        }
        return result == Integer.MIN_VALUE ? Integer.MAX_VALUE : result;
    }

    private int indexOfLayerThreshold(int threshold) {
        for (int i = 0; i < maidLayerSlots.size(); i++) {
            if (maidLayerSlots.get(i).threshold == threshold) return i;
        }
        return -1;
    }

    private MaidLayerSlot resolveMaidLayerSlot(CompositeOverlayGroup group,
                                                boolean screenLeft, int offset) {
        if (maidLayerSlots.isEmpty()) return null;
        int base = group == CompositeOverlayGroup.AHOGE
                ? defaultAhogeLayerSlotIndex : defaultEarLayerSlotIndex;
        if (base < 0) base = maidLayerSlots.size() - 1;
        int resolved = Math.max(0, Math.min(maidLayerSlots.size() - 1, base + offset));
        return maidLayerSlots.get(resolved);
    }

    int resolvedLayerThreshold(CompositeOverlayGroup group,
                               boolean screenLeft, int offset) {
        MaidLayerSlot slot = resolveMaidLayerSlot(group, screenLeft, offset);
        if (slot != null) return slot.threshold;
        int[] orders = model == null ? null : model.getRenderOrders();
        if (orders == null || orders.length == 0) return Integer.MAX_VALUE;
        if (group != CompositeOverlayGroup.AHOGE) return medianRenderOrder(orders);
        int maximum = Integer.MIN_VALUE;
        for (int order : orders) maximum = Math.max(maximum, order);
        return maximum;
    }

    String resolvedLayerLabel(CompositeOverlayGroup group,
                              boolean screenLeft, int offset) {
        MaidLayerSlot slot = resolveMaidLayerSlot(group, screenLeft, offset);
        return slot == null ? "未解析" : slot.label;
    }

    private Set<Integer> retainVisibleDrawables(Set<Integer> candidates) {
        Set<Integer> result = new LinkedHashSet<>();
        if (candidates == null) return result;
        for (int drawable : candidates) {
            if (isDrawableVisible(drawable)) result.add(drawable);
        }
        return result.isEmpty() ? candidates : result;
    }

    /**
     * Finds the complete authored rabbit-ear subsystem without depending on the editor's Part
     * hierarchy. A texture atlas can contain one ear image while the moc3 owns multiple meshes
     * that sample it, so model-local X and PNG layer count are not valid left/right signals.
     */
    private Set<Integer> resolveRabbitEarDrawables(Set<Integer> seeds) {
        Set<Integer> result = new LinkedHashSet<>(seeds);
        rabbitEarDiscoveryHits.clear();
        rabbitEarDiscoveryMode = "seed_only";
        if (model == null || seeds.isEmpty()) return result;

        Set<Integer> seedTextures = new LinkedHashSet<>();
        for (int seed : seeds) {
            if (seed >= 0 && seed < model.getDrawableCount()) {
                seedTextures.add(model.getDrawableTextureIndex(seed));
            }
        }
        float[] savedParameters = new float[model.getParameterCount()];
        captureParameterValues(savedParameters);
        model.update();
        DrawableSignature[] baseline = captureDrawableSignatures();
        boolean scanned = false;
        try {
            for (String parameterId : RABBIT_EAR_PHYSICS_OUTPUT_IDS) {
                int parameterIndex = findParameterIndex(parameterId);
                if (parameterIndex < 0) continue;
                scanned = true;
                Set<String> hits = new LinkedHashSet<>();
                float minimum = model.getParameterMinimumValue(parameterIndex);
                float maximum = model.getParameterMaximumValue(parameterIndex);
                float[] probes = {minimum, maximum};
                for (float probe : probes) {
                    restoreParameterValues(savedParameters);
                    model.getModel().getParameterViews()[parameterIndex].setValue(probe);
                    model.update();
                    for (int drawable = 0; drawable < model.getDrawableCount(); drawable++) {
                        if (!seedTextures.contains(model.getDrawableTextureIndex(drawable))) continue;
                        // Dormant animal-ear variants share this atlas. Keep the currently active
                        // rig plus the confirmed Part113 seed, and do not revive unrelated ears.
                        if (baseline[drawable].opacity <= .001f && !seeds.contains(drawable)) {
                            continue;
                        }
                        DrawableSignature current = DrawableSignature.capture(model, drawable);
                        if (!baseline[drawable].differsFrom(current)) continue;
                        result.add(drawable);
                        hits.add(model.getDrawableId(drawable).getString());
                    }
                }
                rabbitEarDiscoveryHits.put(parameterId, hits);
            }
        } finally {
            restoreParameterValues(savedParameters);
            updateModelWithOutfitShapeLock();
        }
        if (scanned) rabbitEarDiscoveryMode = "native_parameter_differential";
        return result;
    }

    private void splitEarFinFilters(Set<Integer> ears, int drawableCount) {
        earFinScreenLeftFilter = new boolean[drawableCount];
        earFinScreenRightFilter = new boolean[drawableCount];
        if (ears == null || ears.isEmpty()) return;

        List<Integer> sorted = new ArrayList<>(ears);
        sorted.sort((first, second) -> Float.compare(
                drawableCenterX(first), drawableCenterX(second)));
        int splitAfter = Math.max(1, sorted.size() / 2);
        float largestGap = Float.NEGATIVE_INFINITY;
        for (int i = 1; i < sorted.size(); i++) {
            float gap = drawableCenterX(sorted.get(i))
                    - drawableCenterX(sorted.get(i - 1));
            if (Float.isFinite(gap) && gap > largestGap) {
                largestGap = gap;
                splitAfter = i;
            }
        }
        splitAfter = Math.max(1, Math.min(sorted.size() - 1, splitAfter));
        for (int i = 0; i < sorted.size(); i++) {
            int drawable = sorted.get(i);
            if (drawable < 0 || drawable >= drawableCount) continue;
            if (i < splitAfter) earFinScreenLeftFilter[drawable] = true;
            else earFinScreenRightFilter[drawable] = true;
        }
    }

    private float drawableCenterX(int drawable) {
        if (drawable < 0 || drawable >= model.getDrawableCount()) return Float.NaN;
        float[] vertices = model.getDrawableVertices(drawable);
        if (vertices == null || vertices.length < 2) return Float.NaN;
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 1 < vertices.length; i += 2) {
            minimum = Math.min(minimum, vertices[i]);
            maximum = Math.max(maximum, vertices[i]);
        }
        return Float.isFinite(minimum) && Float.isFinite(maximum)
                ? (minimum + maximum) * .5f : Float.NaN;
    }

    private DrawableSignature[] captureDrawableSignatures() {
        DrawableSignature[] result = new DrawableSignature[model.getDrawableCount()];
        for (int i = 0; i < result.length; i++) {
            result[i] = DrawableSignature.capture(model, i);
        }
        return result;
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

        JSONObject earDiscovery = new JSONObject();
        earDiscovery.put("mode", rabbitEarDiscoveryMode);
        JSONObject parameterHits = new JSONObject();
        for (Map.Entry<String, Set<String>> entry : rabbitEarDiscoveryHits.entrySet()) {
            parameterHits.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        earDiscovery.put("parameter_hits", parameterHits);
        earDiscovery.put("manual_mirror", false);
        earDiscovery.put("side_split", "neutral_model_x_largest_gap");
        earDiscovery.put("screen_left", drawableIdsForFilter(earFinScreenLeftFilter));
        earDiscovery.put("screen_right", drawableIdsForFilter(earFinScreenRightFilter));
        root.put("rabbit_ear_discovery", earDiscovery);

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
        root.put("carrier_anchors", buildCarrierInventory());
        return root;
    }

    JSONObject buildCarrierInventory() throws JSONException {
        return new JSONObject()
                .put("role", compositeRole.name().toLowerCase(java.util.Locale.ROOT))
                .put("head", headCarrierFrame == null
                        ? JSONObject.NULL : headCarrierFrame.toJson())
                .put("body", bodyCarrierFrame == null
                        ? JSONObject.NULL : bodyCarrierFrame.toJson())
                .put("ahoge_top_hair", ahogeHairCarrierFrame == null
                        ? JSONObject.NULL : ahogeHairCarrierFrame.toJson())
                .put("ahoge_head_pin", ahogeHeadPinFrame == null
                        ? JSONObject.NULL : ahogeHeadPinFrame.toJson())
                .put("screen_left_ear_head_pin", screenLeftEarHeadPinFrame == null
                        ? JSONObject.NULL : screenLeftEarHeadPinFrame.toJson())
                .put("screen_right_ear_head_pin", screenRightEarHeadPinFrame == null
                        ? JSONObject.NULL : screenRightEarHeadPinFrame.toJson())
                .put("screen_left_bow", screenLeftBowCarrierFrame == null
                        ? JSONObject.NULL : screenLeftBowCarrierFrame.toJson())
                .put("screen_right_bow", screenRightBowCarrierFrame == null
                        ? JSONObject.NULL : screenRightBowCarrierFrame.toJson());
    }

    JSONObject buildLayerCalibrationInventory(OverlayCalibration calibration)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put("policy", "skinning_material_sections_merge_equivalent_color_branches");
        root.put("source_granularity", "author_skinning_part_approximately_one_source_material_piece");
        root.put("texture_atlas_note", "texture_00_to_03_are_packed_atlases_not_source_png_layers");
        root.put("direction", new JSONObject()
                .put("positive", "往前（更少遮挡）")
                .put("negative", "往后（更容易被遮挡）"));
        JSONArray groups = new JSONArray();
        for (MaidLayerGroup group : maidLayerGroups) groups.put(group.toJson(model));
        root.put("logical_part_groups", groups);
        JSONArray slots = new JSONArray();
        for (int i = 0; i < maidLayerSlots.size(); i++) {
            MaidLayerSlot slot = maidLayerSlots.get(i);
            slots.put(slot.toJson(i));
        }
        root.put("slots_back_to_front", slots);
        JSONObject resolved = new JSONObject();
        int ahogeOffset = calibration.getLayerOffset(CompositeOverlayGroup.AHOGE, true);
        int leftOffset = calibration.getLayerOffset(CompositeOverlayGroup.EAR_FINS, true);
        int rightOffset = calibration.getLayerOffset(CompositeOverlayGroup.EAR_FINS, false);
        resolved.put("ahoge", resolvedLayerJson(
                CompositeOverlayGroup.AHOGE, true, ahogeOffset));
        resolved.put("ear_fins_screen_left", resolvedLayerJson(
                CompositeOverlayGroup.EAR_FINS, true, leftOffset));
        resolved.put("ear_fins_screen_right", resolvedLayerJson(
                CompositeOverlayGroup.EAR_FINS, false, rightOffset));
        root.put("resolved", resolved);
        return root;
    }

    private JSONObject resolvedLayerJson(CompositeOverlayGroup group,
                                         boolean screenLeft, int offset)
            throws JSONException {
        MaidLayerSlot slot = resolveMaidLayerSlot(group, screenLeft, offset);
        MeshAnchorFrame carrier = headPinFrame(group, screenLeft);
        int base = group == CompositeOverlayGroup.AHOGE
                ? defaultAhogeLayerSlotIndex : defaultEarLayerSlotIndex;
        int index = slot == null ? -1 : maidLayerSlots.indexOf(slot);
        return new JSONObject()
                .put("requested_offset", offset)
                .put("base_slot_index", base)
                .put("resolved_slot_index", index)
                .put("threshold", slot == null ? JSONObject.NULL : slot.threshold)
                .put("slot_label", slot == null ? "未解析" : slot.label)
                .put("carrier_rule", "绘制插层与运动载体完全解耦；局部定位点+稳定头部转角")
                .put("carrier_compatibility_zero", true)
                .put("carrier_group", group == CompositeOverlayGroup.AHOGE
                        ? "女仆脸部网格_顶部中央定位点"
                        : (screenLeft ? "女仆脸部网格_画面左定位点"
                        : "女仆脸部网格_画面右定位点"))
                .put("carrier_anchor", carrier == null
                        ? JSONObject.NULL : carrier.toJson());
    }

    private JSONArray drawableIdsForFilter(boolean[] filter) {
        JSONArray result = new JSONArray();
        if (model == null || filter == null) return result;
        int count = Math.min(filter.length, model.getDrawableCount());
        for (int i = 0; i < count; i++) if (filter[i]) {
            result.put(model.getDrawableId(i).getString());
        }
        return result;
    }

    private boolean isSelectedAccessoryDrawable(int index) {
        for (boolean[] filter : compositeGroupFilters.values()) {
            if (filter != null && index >= 0 && index < filter.length && filter[index]) return true;
        }
        return false;
    }

    private void captureNeutralAttachmentPoints() {
        if (model == null) return;
        String[] headParts = compositeRole == CompositeModelRole.MAID_PRIMARY
                ? MAID_HEAD_CARRIER_PART_IDS : SEN_HEAD_CARRIER_PART_IDS;
        String[] bodyParts = compositeRole == CompositeModelRole.MAID_PRIMARY
                ? MAID_BODY_CARRIER_PART_IDS : SEN_BODY_CARRIER_PART_IDS;
        headCarrierFrame = MeshAnchorFrame.fromLargestStableTriangle(
                model, collectChildDrawables(headParts));
        bodyCarrierFrame = MeshAnchorFrame.fromLargestStableTriangle(
                model, collectChildDrawables(bodyParts));
        if (compositeRole == CompositeModelRole.MAID_PRIMARY) {
            // Draw order and motion attachment are separate concerns. All three head accessories
            // are pinned to different places on the maid's large face mesh, whose deformation is
            // reliable during authored head turns. Local hair material sections are still used
            // for occlusion only; several of them barely move or belong to only one screen side.
            if (headCarrierFrame != null) {
                ahogeHeadPinFrame = MeshAnchorFrame.fromTriangleNearNormalizedPoint(
                        model, headCarrierFrame.drawableIndex, .50f, .86f);
                screenLeftEarHeadPinFrame = MeshAnchorFrame.fromTriangleNearNormalizedPoint(
                        model, headCarrierFrame.drawableIndex, .12f, .76f);
                screenRightEarHeadPinFrame = MeshAnchorFrame.fromTriangleNearNormalizedPoint(
                        model, headCarrierFrame.drawableIndex, .88f, .76f);
            }
            ahogeHairCarrierFrame = MeshAnchorFrame.fromLargestStableTriangle(
                    model, collectChildDrawables(MAID_TOP_HAIR_PART_IDS));
            MeshAnchorFrame firstBow = MeshAnchorFrame.fromLargestStableTriangle(
                    model, collectChildDrawables(new String[]{MAID_SIDE_BOW_PART_IDS[0]}));
            MeshAnchorFrame secondBow = MeshAnchorFrame.fromLargestStableTriangle(
                    model, collectChildDrawables(new String[]{MAID_SIDE_BOW_PART_IDS[1]}));
            if (firstBow != null && secondBow != null
                    && firstBow.neutralCenterX() <= secondBow.neutralCenterX()) {
                screenLeftBowCarrierFrame = firstBow;
                screenRightBowCarrierFrame = secondBow;
            } else {
                screenLeftBowCarrierFrame = secondBow;
                screenRightBowCarrierFrame = firstBow;
            }
            for (MaidLayerGroup group : maidLayerGroups) group.captureFrames(model);
        }
        neutralAhogeRoot = compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR
                ? currentAhogeRootPoint() : null;
        neutralAhogeDirection = compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR
                ? currentAhogeDirectionPoint() : null;
        neutralLeftEarBounds = null;
        neutralRightEarBounds = null;
        if (compositeRole == CompositeModelRole.SEN_ACCESSORY_DONOR) {
            neutralLeftEarBounds = earModelBounds(true);
            neutralRightEarBounds = earModelBounds(false);
        }
        appendAppearanceDetail("固定三角载体：头 "
                + (headCarrierFrame == null ? "缺失" : headCarrierFrame.drawableId)
                + " · 身体 "
                + (bodyCarrierFrame == null ? "缺失" : bodyCarrierFrame.drawableId)
                + (ahogeHairCarrierFrame == null ? "" : " · 呆毛顶部头发 "
                + ahogeHairCarrierFrame.drawableId)
                + (ahogeHeadPinFrame == null ? "" : " · 呆毛头部定位点 "
                + ahogeHeadPinFrame.drawableId)
                + (screenLeftEarHeadPinFrame == null ? "" : " · 左耳鳍头部定位点 "
                + screenLeftEarHeadPinFrame.drawableId)
                + (screenRightEarHeadPinFrame == null ? "" : " · 右耳鳍头部定位点 "
                + screenRightEarHeadPinFrame.drawableId)
                + (screenLeftBowCarrierFrame == null ? "" : " · 画面左蝴蝶结 "
                + screenLeftBowCarrierFrame.drawableId)
                + (screenRightBowCarrierFrame == null ? "" : " · 画面右蝴蝶结 "
                + screenRightBowCarrierFrame.drawableId)
                + (neutralAhogeRoot == null ? "" : " · 呆毛根部已保留"));
    }

    float[] currentCarrierTriangle(CompositeOverlayGroup group) {
        MeshAnchorFrame frame = group == CompositeOverlayGroup.TAIL ? bodyCarrierFrame
                : group == CompositeOverlayGroup.AHOGE && ahogeHairCarrierFrame != null
                ? ahogeHairCarrierFrame : headCarrierFrame;
        return frame == null ? null : frame.currentTriangle(model);
    }

    float[] neutralCarrierTriangle(CompositeOverlayGroup group) {
        MeshAnchorFrame frame = group == CompositeOverlayGroup.TAIL ? bodyCarrierFrame
                : group == CompositeOverlayGroup.AHOGE && ahogeHairCarrierFrame != null
                ? ahogeHairCarrierFrame : headCarrierFrame;
        return frame == null ? null : frame.neutralTriangle();
    }

    float[] currentEarCarrierTriangle(boolean screenLeft) {
        MeshAnchorFrame frame = screenLeft
                ? screenLeftBowCarrierFrame : screenRightBowCarrierFrame;
        return frame == null ? currentCarrierTriangle(CompositeOverlayGroup.EAR_FINS)
                : frame.currentTriangle(model);
    }

    float[] neutralEarCarrierTriangle(boolean screenLeft) {
        MeshAnchorFrame frame = screenLeft
                ? screenLeftBowCarrierFrame : screenRightBowCarrierFrame;
        return frame == null ? neutralCarrierTriangle(CompositeOverlayGroup.EAR_FINS)
                : frame.neutralTriangle();
    }

    float[] currentHeadPinTriangle(CompositeOverlayGroup group, boolean screenLeft) {
        MeshAnchorFrame frame = headPinFrame(group, screenLeft);
        return frame == null ? currentCarrierTriangle(group) : frame.currentTriangle(model);
    }

    float[] neutralHeadPinTriangle(CompositeOverlayGroup group, boolean screenLeft) {
        MeshAnchorFrame frame = headPinFrame(group, screenLeft);
        return frame == null ? neutralCarrierTriangle(group) : frame.neutralTriangle();
    }

    private MeshAnchorFrame headPinFrame(CompositeOverlayGroup group, boolean screenLeft) {
        if (group == CompositeOverlayGroup.AHOGE) return ahogeHeadPinFrame;
        if (group == CompositeOverlayGroup.EAR_FINS) {
            return screenLeft ? screenLeftEarHeadPinFrame : screenRightEarHeadPinFrame;
        }
        return null;
    }

    float[] currentLayerCarrierTriangle(CompositeOverlayGroup group,
                                        boolean screenLeft, int layerOffset) {
        MeshAnchorFrame frame = carrierFrameForLayer(group, screenLeft, layerOffset);
        return frame == null ? null : frame.currentTriangle(model);
    }

    float[] neutralLayerCarrierTriangle(CompositeOverlayGroup group,
                                        boolean screenLeft, int layerOffset) {
        MeshAnchorFrame frame = carrierFrameForLayer(group, screenLeft, layerOffset);
        return frame == null ? null : frame.neutralTriangle();
    }

    private MeshAnchorFrame carrierFrameForLayer(CompositeOverlayGroup group,
                                                  boolean screenLeft, int layerOffset) {
        // Ahoge draw order and motion carrier are intentionally independent. Its root grows from
        // the top-hair mesh even while it is drawn immediately in front of the headwear.
        if (group == CompositeOverlayGroup.AHOGE) {
            return ahogeHairCarrierFrame == null ? headCarrierFrame : ahogeHairCarrierFrame;
        }
        // Ear offset zero remains the accepted v0.1.13 side-bow carrier. Non-zero calibration
        // selects a material-sized hair section and uses its corresponding screen-side triangle.
        if (layerOffset == 0) {
            if (group == CompositeOverlayGroup.EAR_FINS) {
                MeshAnchorFrame bow = screenLeft
                        ? screenLeftBowCarrierFrame : screenRightBowCarrierFrame;
                return bow == null ? headCarrierFrame : bow;
            }
        }
        MaidLayerSlot slot = resolveMaidLayerSlot(group, screenLeft, layerOffset);
        if (slot == null) return headCarrierFrame;
        MaidLayerGroup carrierGroup = slot.front == null ? slot.behind : slot.front;
        if (carrierGroup == null) return headCarrierFrame;
        MeshAnchorFrame frame = group == CompositeOverlayGroup.EAR_FINS
                ? carrierGroup.sideFrame(screenLeft) : carrierGroup.centerFrame;
        return frame == null ? headCarrierFrame : frame;
    }

    float[] currentAhogeRootPoint() {
        if (model == null || ahogeRootAnchor == null) return null;
        float[] value = ahogeRootAnchor.currentPoint(model);
        return value == null ? null : value.clone();
    }

    /** The maid attachment is a point on a known top-hair drawable, not a guessed triangle center. */
    float[] currentMaidHairPoint() {
        return model == null || selectedMaidHairPoint == null ? null
                : selectedMaidHairPoint.currentPoint(model);
    }

    JSONObject selectedMaidHairPointJson() throws JSONException {
        if (selectedMaidHairPoint == null) return null;
        return selectedMaidHairPoint.toJson();
    }

    void restoreMaidHairPoint(String json) {
        selectedMaidHairPoint = null;
        if (model == null || compositeRole != CompositeModelRole.MAID_PRIMARY
                || json == null || json.isEmpty()) return;
        try {
            AhogeAnchorPoint point = AhogeAnchorPoint.fromJson(new JSONObject(json), model);
            if (point != null && collectChildDrawables(MAID_TOP_HAIR_PART_IDS)
                    .contains(point.drawableIndex)) selectedMaidHairPoint = point;
        } catch (JSONException ignored) { }
    }

    /** Pick on the *drawn* top-hair triangles; store their IDs and weights, never screen pixels. */
    JSONObject pickMaidHairPoint(CubismMatrix44 projection, float clipX, float clipY,
                                float maximumClipDistance) throws JSONException {
        if (model == null || compositeRole != CompositeModelRole.MAID_PRIMARY) return null;
        copyMvpMatrix(projection, interactionAnchorMvp);
        float[] matrix = interactionAnchorMvp.getArray();
        AhogeAnchorPoint best = null;
        float bestDistance = maximumClipDistance * maximumClipDistance;
        for (int index : collectChildDrawables(MAID_TOP_HAIR_PART_IDS)) {
            if (model.getDrawableOpacity(index) < .001f
                    || !model.getDrawableDynamicFlagIsVisible(index)) continue;
            float[] vertices = model.getDrawableVertices(index);
            short[] triangles = model.getDrawableVertexIndices(index);
            if (vertices == null || triangles == null) continue;
            for (int i = 0; i + 2 < triangles.length; i += 3) {
                int a = triangles[i] & 0xffff, b = triangles[i + 1] & 0xffff;
                int c = triangles[i + 2] & 0xffff;
                if (!validVertex(vertices, a) || !validVertex(vertices, b)
                        || !validVertex(vertices, c)) continue;
                float ax = matrix[0] * vertices[a * 2] + matrix[4] * vertices[a * 2 + 1] + matrix[12];
                float ay = matrix[1] * vertices[a * 2] + matrix[5] * vertices[a * 2 + 1] + matrix[13];
                float bx = matrix[0] * vertices[b * 2] + matrix[4] * vertices[b * 2 + 1] + matrix[12];
                float by = matrix[1] * vertices[b * 2] + matrix[5] * vertices[b * 2 + 1] + matrix[13];
                float cx = matrix[0] * vertices[c * 2] + matrix[4] * vertices[c * 2 + 1] + matrix[12];
                float cy = matrix[1] * vertices[c * 2] + matrix[5] * vertices[c * 2 + 1] + matrix[13];
                float det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
                if (Math.abs(det) < 1e-9f) continue;
                float w1 = ((by - cy) * (clipX - cx) + (cx - bx) * (clipY - cy)) / det;
                float w2 = ((cy - ay) * (clipX - cx) + (ax - cx) * (clipY - cy)) / det;
                float w3 = 1f - w1 - w2;
                // Clamp to the triangle so a near-edge tap can still land on visible hair.
                w1 = Math.max(0f, w1); w2 = Math.max(0f, w2); w3 = Math.max(0f, w3);
                float sum = w1 + w2 + w3;
                w1 /= sum; w2 /= sum; w3 /= sum;
                float px = w1 * ax + w2 * bx + w3 * cx;
                float py = w1 * ay + w2 * by + w3 * cy;
                float distance = (px - clipX) * (px - clipX) + (py - clipY) * (py - clipY);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = new AhogeAnchorPoint(index, model.getDrawableId(index).getString(),
                            a, b, c, w1, w2, w3);
                }
            }
        }
        if (best == null) return null;
        selectedMaidHairPoint = best;
        return best.toJson();
    }

    private final CubismMatrix44 interactionAnchorMvp = CubismMatrix44.create();

    float[] neutralAhogeRootPoint() {
        return neutralAhogeRoot == null ? null : neutralAhogeRoot.clone();
    }

    float[] currentAhogeDirectionPoint() {
        if (model == null || ahogeDirectionAnchor == null) return null;
        float[] value = ahogeDirectionAnchor.currentPoint(model);
        return value == null ? null : value.clone();
    }

    float[] neutralAhogeDirectionPoint() {
        return neutralAhogeDirection == null ? null : neutralAhogeDirection.clone();
    }

    float horizontalHeadTurnMagnitude() {
        return Math.abs(horizontalHeadTurnSigned());
    }

    float horizontalHeadTurnSigned() {
        if (model == null) return 0f;
        int index = findParameterIndex("ParamAngleX3");
        if (index < 0) return 0f;
        float neutral = model.getParameterDefaultValue(index);
        float value = model.getModel().getParameterViews()[index].getValue();
        float range = value >= neutral
                ? model.getParameterMaximumValue(index) - neutral
                : neutral - model.getParameterMinimumValue(index);
        return range < 1e-5f ? 0f
                : Math.max(-1f, Math.min(1f, (value - neutral) / range));
    }

    /** Clip-space bounds of the actual drawable vertices, after this side's draw transform. */
    float[] currentEarClipBounds(CubismMatrix44 projection, boolean screenLeft) {
        if (model == null) return null;
        boolean[] filter = screenLeft ? earFinScreenLeftFilter : earFinScreenRightFilter;
        if (filter == null) return null;
        copyMvpMatrix(projection, earBoundsMvpMatrix);
        float[] transform = earBoundsMvpMatrix.getArray();
        float[] bounds = emptyBounds();
        for (int i = 0; i < Math.min(filter.length, model.getDrawableCount()); i++) {
            if (!filter[i] || !isDrawableVisible(i)) continue;
            float[] vertices = model.getDrawableVertices(i);
            for (int v = 0; v + 1 < vertices.length; v += 2) {
                addClipPoint(bounds, transform, vertices[v], vertices[v + 1]);
            }
        }
        return Float.isFinite(bounds[0]) ? bounds : null;
    }

    /** The currently rendered six-segment ahoge silhouette, including native secondary motion. */
    float[] currentAhogeClipBounds(CubismMatrix44 projection) {
        if (model == null) return null;
        boolean[] filter = compositeGroupFilters.get(CompositeOverlayGroup.AHOGE);
        if (filter == null) return null;
        copyMvpMatrix(projection, earBoundsMvpMatrix);
        float[] transform = earBoundsMvpMatrix.getArray();
        float[] bounds = emptyBounds();
        for (int i = 0; i < Math.min(filter.length, model.getDrawableCount()); i++) {
            if (!filter[i] || !isDrawableVisible(i)) continue;
            float[] vertices = model.getDrawableVertices(i);
            for (int v = 0; v + 1 < vertices.length; v += 2) {
                addClipPoint(bounds, transform, vertices[v], vertices[v + 1]);
            }
        }
        return Float.isFinite(bounds[0]) ? bounds : null;
    }

    float[] neutralEarClipBounds(CubismMatrix44 projection, boolean screenLeft) {
        float[] neutral = screenLeft ? neutralLeftEarBounds : neutralRightEarBounds;
        if (neutral == null) return null;
        copyMvpMatrix(projection, earBoundsMvpMatrix);
        float[] matrix = earBoundsMvpMatrix.getArray();
        float[] bounds = emptyBounds();
        for (float x : new float[]{neutral[0], neutral[2]}) {
            for (float y : new float[]{neutral[1], neutral[3]}) {
                addClipPoint(bounds, matrix, x, y);
            }
        }
        return bounds;
    }

    private float[] earModelBounds(boolean screenLeft) {
        boolean[] filter = screenLeft ? earFinScreenLeftFilter : earFinScreenRightFilter;
        if (filter == null) return null;
        float[] bounds = emptyBounds();
        for (int i = 0; i < Math.min(filter.length, model.getDrawableCount()); i++) {
            if (!filter[i] || !isDrawableVisible(i)) continue;
            float[] vertices = model.getDrawableVertices(i);
            for (int v = 0; v + 1 < vertices.length; v += 2) {
                bounds[0] = Math.min(bounds[0], vertices[v]);
                bounds[1] = Math.min(bounds[1], vertices[v + 1]);
                bounds[2] = Math.max(bounds[2], vertices[v]);
                bounds[3] = Math.max(bounds[3], vertices[v + 1]);
            }
        }
        return Float.isFinite(bounds[0]) ? bounds : null;
    }

    private static float[] emptyBounds() {
        return new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    }

    private static void addClipPoint(float[] bounds, float[] matrix, float x, float y) {
        float clipX = matrix[0] * x + matrix[4] * y + matrix[12];
        float clipY = matrix[1] * x + matrix[5] * y + matrix[13];
        bounds[0] = Math.min(bounds[0], clipX);
        bounds[1] = Math.min(bounds[1], clipY);
        bounds[2] = Math.max(bounds[2], clipX);
        bounds[3] = Math.max(bounds[3], clipY);
    }

    float[] currentCompositeGroupCenter(CompositeOverlayGroup group) {
        boolean[] filter = compositeGroupFilters.get(group);
        if (filter == null) return null;
        Set<Integer> indices = new LinkedHashSet<>();
        for (int i = 0; i < filter.length; i++) if (filter[i]) indices.add(i);
        return centerOfVisibleOrAll(indices);
    }

    float[] currentEarSideCenter(boolean screenLeft) {
        boolean[] filter = screenLeft ? earFinScreenLeftFilter : earFinScreenRightFilter;
        if (filter == null) return null;
        Set<Integer> indices = new LinkedHashSet<>();
        for (int i = 0; i < filter.length; i++) if (filter[i]) indices.add(i);
        return centerOfVisibleOrAll(indices);
    }

    private float[] centerOfVisibleOrAll(Set<Integer> indices) {
        if (indices == null || indices.isEmpty()) return null;
        Set<Integer> visible = new LinkedHashSet<>();
        for (int index : indices) {
            if (index >= 0 && index < model.getDrawableCount()
                    && model.getDrawableOpacity(index) > .001f) {
                visible.add(index);
            }
        }
        float[] result = centerOf(visible);
        return result == null ? centerOf(indices) : result;
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

    /** Appends a final clip-space transform after model layout and projection. */
    void applyClipTransform(CubismMatrix44 projection, float[] clipTransform) {
        if (projection == null || clipTransform == null
                || clipTransform.length != 16) return;
        // Cubism draws modelMatrix * projection. Right-multiplication therefore yields
        // modelMatrix * projection * clipTransform, so every model and every filtered pass shares
        // exactly the same final stage transform. The previous left-multiplication/conjugation
        // changed the transform for each model layout and caused accessories to fly apart.
        CubismMatrix44.multiply(projection.getArray(), clipTransform,
                projection.getArray());
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
            if ("1生气".equals(name)) angryMouthGuardSeconds = 0f;
            if ("变小".equals(name)) {
                stopExpressionIfActive("2插手");
                stopExpressionIfActive("1生气");
            }
            return;
        }
        if ("变小".equals(name)) {
            // Only selecting the small form establishes its starting pose. The three ZIP
            // switches retain their normal independent toggles afterwards.
            if (!activeExpressionNames.contains("2插手")) setExpression("2插手");
            if (!activeExpressionNames.contains("1生气")) setExpression("1生气");
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
                if ("1生气".equals(activeName)) angryMouthGuardSeconds = 0f;
            }
        }
        manager.startMotionPriority(motion, 3);
        activeExpressionNames.add(name);
        if ("1生气".equals(name)) angryMouthGuardSeconds = 0.5f;
    }

    private void stopExpressionIfActive(String name) {
        if (!activeExpressionNames.remove(name)) return;
        CubismExpressionMotionManager manager = expressionManagers.get(name);
        if (manager != null) manager.stopAllMotions();
        if ("1生气".equals(name)) angryMouthGuardSeconds = 0f;
    }

    void resetNativePresets() {
        for (CubismExpressionMotionManager manager : expressionManagers.values()) {
            manager.stopAllMotions();
        }
        activeExpressionNames.clear();
        angryMouthGuardSeconds = 0f;
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
                if ("1生气".equals(name)) {
                    // Param148 switches the authored face. Its half-second intermediate shape
                    // briefly exposes an open mouth; enter the closed-mouth keyform directly.
                    motion.setFadeInTime(0f);
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
        Set<Integer> ahogeDrawables = collectExistingDrawables(AHOGE_DRAWABLE_IDS);
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
        float lengthScale = overall * SenRenderOptions.AHOGE_LENGTH_PERCENT / 100.0f
                * ahogeShape.height;
        float widthScale = overall * SenRenderOptions.AHOGE_WIDTH_PERCENT / 100.0f
                * ahogeShape.width;
        double radians = Math.toRadians(SenRenderOptions.AHOGE_ROTATION_DEGREES
                + ahogeShape.rotation);
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

    /**
     * Adds secondary motion after the native model has updated. The confirmed root and its nearby
     * vertices receive zero weight; influence rises smoothly towards the tip, so the renderer can
     * lock the root exactly while the rest of the six-mesh ahoge bends and trails behind motion.
     * The next Cubism update recreates native vertices, so this never accumulates frame to frame.
     */
    void applyAhogeSecondaryMotion(float offsetX, float offsetY, float angleRadians) {
        if (!hasCompleteAhogeAnchor() || model == null) return;
        float[] root = ahogeRootAnchor.currentPoint(model);
        if (root == null) return;
        Set<Integer> candidates = collectExistingDrawables(AHOGE_DRAWABLE_IDS);
        float maximumDistance = 0f;
        for (int index : candidates) {
            if (!isDrawableVisible(index)) continue;
            float[] vertices = model.getDrawableVertices(index);
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                maximumDistance = Math.max(maximumDistance, (float) Math.hypot(
                        vertices[i] - root[0], vertices[i + 1] - root[1]));
            }
        }
        if (maximumDistance < 1e-5f) return;

        for (int index : candidates) {
            if (!isDrawableVisible(index)) continue;
            float[] vertices = model.getDrawableVertices(index);
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                float dx = vertices[i] - root[0];
                float dy = vertices[i + 1] - root[1];
                float distance = (float) Math.hypot(dx, dy);
                // Only the point itself is fixed. Motion grows continuously from the root,
                // without a frozen lower segment or a seam between the six native meshes.
                float t = Math.max(0f, Math.min(1f, distance / maximumDistance));
                float weight = t * t * (3f - 2f * t);
                if (weight <= 0f) continue;
                float angle = angleRadians * weight;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                vertices[i] = root[0] + dx * cos - dy * sin + offsetX * weight;
                vertices[i + 1] = root[1] + dx * sin + dy * cos + offsetY * weight;
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

    private Set<Integer> collectExistingDrawables(String[] drawableIds) {
        Set<Integer> result = new LinkedHashSet<>();
        for (String drawableId : drawableIds) {
            int index = findExistingDrawableIndex(drawableId);
            if (index >= 0) result.add(index);
        }
        return result;
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

    private static final class MaidLayerGroupSpec {
        final String label;
        final String[] partIds;

        MaidLayerGroupSpec(String label, String[] partIds) {
            this.label = label;
            this.partIds = partIds;
        }
    }

    private static final class MaidLayerGroup {
        final String label;
        final String[] partIds;
        final Set<Integer> drawables;
        final int minimumRenderOrder;
        final int maximumRenderOrder;
        MeshAnchorFrame centerFrame;
        MeshAnchorFrame screenLeftFrame;
        MeshAnchorFrame screenRightFrame;

        MaidLayerGroup(String label, String[] partIds, Set<Integer> drawables,
                       int minimumRenderOrder, int maximumRenderOrder) {
            this.label = label;
            this.partIds = partIds.clone();
            this.drawables = new LinkedHashSet<>(drawables);
            this.minimumRenderOrder = minimumRenderOrder;
            this.maximumRenderOrder = maximumRenderOrder;
        }

        void captureFrames(com.live2d.sdk.cubism.framework.model.CubismModel target) {
            centerFrame = MeshAnchorFrame.fromLargestStableTriangle(target, drawables);
            Set<Integer> left = new LinkedHashSet<>();
            Set<Integer> right = new LinkedHashSet<>();
            List<Float> centers = new ArrayList<>();
            Map<Integer, Float> centerByDrawable = new LinkedHashMap<>();
            for (int drawable : drawables) {
                float center = drawableCenterX(target, drawable);
                if (Float.isNaN(center)) continue;
                centers.add(center);
                centerByDrawable.put(drawable, center);
            }
            Collections.sort(centers);
            float midpoint = centers.isEmpty() ? 0f
                    : (centers.get((centers.size() - 1) / 2)
                    + centers.get(centers.size() / 2)) * .5f;
            for (Map.Entry<Integer, Float> entry : centerByDrawable.entrySet()) {
                if (entry.getValue() <= midpoint) left.add(entry.getKey());
                if (entry.getValue() >= midpoint) right.add(entry.getKey());
            }
            screenLeftFrame = MeshAnchorFrame.fromLargestStableTriangle(
                    target, left.isEmpty() ? drawables : left);
            screenRightFrame = MeshAnchorFrame.fromLargestStableTriangle(
                    target, right.isEmpty() ? drawables : right);
        }

        MeshAnchorFrame sideFrame(boolean screenLeft) {
            MeshAnchorFrame result = screenLeft ? screenLeftFrame : screenRightFrame;
            return result == null ? centerFrame : result;
        }

        JSONObject toJson(com.live2d.sdk.cubism.framework.model.CubismModel target)
                throws JSONException {
            JSONArray drawableIds = new JSONArray();
            JSONArray drawableDetails = new JSONArray();
            if (target != null) for (int drawable : drawables) {
                if (drawable >= 0 && drawable < target.getDrawableCount()) {
                    String drawableId = target.getDrawableId(drawable).getString();
                    drawableIds.put(drawableId);
                    int parentIndex = target.getDrawableParentPartIndex(drawable);
                    JSONObject detail = new JSONObject()
                            .put("id", drawableId)
                            .put("render_order", target.getRenderOrders()[drawable])
                            .put("texture_index", target.getDrawableTextureIndex(drawable))
                            .put("direct_parent_part_id", parentIndex >= 0
                                    && parentIndex < target.getPartCount()
                                    ? target.getPartId(parentIndex).getString()
                                    : JSONObject.NULL);
                    float[] uv = target.getDrawableVertexUvs(drawable);
                    if (uv != null && uv.length >= 2) {
                        float minU = Float.POSITIVE_INFINITY;
                        float minV = Float.POSITIVE_INFINITY;
                        float maxU = Float.NEGATIVE_INFINITY;
                        float maxV = Float.NEGATIVE_INFINITY;
                        for (int i = 0; i + 1 < uv.length; i += 2) {
                            minU = Math.min(minU, uv[i]);
                            minV = Math.min(minV, uv[i + 1]);
                            maxU = Math.max(maxU, uv[i]);
                            maxV = Math.max(maxV, uv[i + 1]);
                        }
                        detail.put("uv_bounds", new JSONArray(Arrays.asList(
                                minU, minV, maxU, maxV)));
                    }
                    drawableDetails.put(detail);
                }
            }
            return new JSONObject()
                    .put("label", label)
                    .put("part_ids", new JSONArray(Arrays.asList(partIds)))
                    .put("minimum_render_order", minimumRenderOrder)
                    .put("maximum_render_order", maximumRenderOrder)
                    .put("drawable_ids", drawableIds)
                    .put("drawable_details", drawableDetails)
                    .put("center_anchor", centerFrame == null
                            ? JSONObject.NULL : centerFrame.toJson())
                    .put("screen_left_anchor", screenLeftFrame == null
                            ? JSONObject.NULL : screenLeftFrame.toJson())
                    .put("screen_right_anchor", screenRightFrame == null
                            ? JSONObject.NULL : screenRightFrame.toJson());
        }

        private static float drawableCenterX(
                com.live2d.sdk.cubism.framework.model.CubismModel target, int drawable) {
            if (target == null || drawable < 0 || drawable >= target.getDrawableCount()) {
                return Float.NaN;
            }
            float[] vertices = target.getDrawableVertices(drawable);
            if (vertices == null || vertices.length < 2) return Float.NaN;
            float minimum = Float.POSITIVE_INFINITY;
            float maximum = Float.NEGATIVE_INFINITY;
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                minimum = Math.min(minimum, vertices[i]);
                maximum = Math.max(maximum, vertices[i]);
            }
            return (minimum + maximum) * .5f;
        }
    }

    private static final class MaidLayerSlot {
        final int threshold;
        String label = "";
        MaidLayerGroup behind;
        MaidLayerGroup front;

        MaidLayerSlot(int threshold) {
            this.threshold = threshold;
        }

        JSONObject toJson(int index) throws JSONException {
            return new JSONObject()
                    .put("index", index)
                    .put("threshold", threshold)
                    .put("label", label)
                    .put("behind_group", behind == null ? JSONObject.NULL : behind.label)
                    .put("front_group", front == null ? JSONObject.NULL : front.label);
        }
    }

    /** A fixed drawable triangle used as a deformation carrier across model frames. */
    private static final class MeshAnchorFrame {
        final int drawableIndex;
        final String drawableId;
        final int vertex1;
        final int vertex2;
        final int vertex3;
        final float[] neutral;

        MeshAnchorFrame(int drawableIndex, String drawableId,
                        int vertex1, int vertex2, int vertex3, float[] neutral) {
            this.drawableIndex = drawableIndex;
            this.drawableId = drawableId;
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            this.vertex3 = vertex3;
            this.neutral = neutral;
        }

        float[] currentTriangle(
                com.live2d.sdk.cubism.framework.model.CubismModel target) {
            if (target == null || drawableIndex < 0
                    || drawableIndex >= target.getDrawableCount()) return null;
            float[] vertices = target.getDrawableVertices(drawableIndex);
            if (!validVertex(vertices, vertex1) || !validVertex(vertices, vertex2)
                    || !validVertex(vertices, vertex3)) return null;
            return new float[]{
                    vertices[vertex1 * 2], vertices[vertex1 * 2 + 1],
                    vertices[vertex2 * 2], vertices[vertex2 * 2 + 1],
                    vertices[vertex3 * 2], vertices[vertex3 * 2 + 1]
            };
        }

        float[] neutralTriangle() {
            return neutral.clone();
        }

        float neutralCenterX() {
            return (neutral[0] + neutral[2] + neutral[4]) / 3f;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("drawable_id", drawableId)
                    .put("drawable_index", drawableIndex)
                    .put("triangle_vertex_ids", new JSONArray(
                            Arrays.asList(vertex1, vertex2, vertex3)))
                    .put("neutral_triangle", new JSONArray(Arrays.asList(
                            neutral[0], neutral[1], neutral[2], neutral[3],
                            neutral[4], neutral[5])));
        }

        static MeshAnchorFrame fromLargestStableTriangle(
                com.live2d.sdk.cubism.framework.model.CubismModel target,
                Set<Integer> candidates) {
            if (target == null || candidates == null || candidates.isEmpty()) return null;
            MeshAnchorFrame bestVisible = null;
            MeshAnchorFrame bestAny = null;
            float bestVisibleScore = -1f;
            float bestAnyScore = -1f;
            for (int drawable : candidates) {
                if (drawable < 0 || drawable >= target.getDrawableCount()) continue;
                float[] vertices = target.getDrawableVertices(drawable);
                short[] indices = target.getDrawableVertexIndices(drawable);
                if (vertices == null || indices == null) continue;
                boolean visible = target.getDrawableOpacity(drawable) > .001f
                        && target.getDrawableDynamicFlagIsVisible(drawable);
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    int v1 = indices[i] & 0xffff;
                    int v2 = indices[i + 1] & 0xffff;
                    int v3 = indices[i + 2] & 0xffff;
                    if (!validVertex(vertices, v1) || !validVertex(vertices, v2)
                            || !validVertex(vertices, v3)) continue;
                    float x1 = vertices[v1 * 2];
                    float y1 = vertices[v1 * 2 + 1];
                    float x2 = vertices[v2 * 2];
                    float y2 = vertices[v2 * 2 + 1];
                    float x3 = vertices[v3 * 2];
                    float y3 = vertices[v3 * 2 + 1];
                    float area2 = Math.abs((x2 - x1) * (y3 - y1)
                            - (y2 - y1) * (x3 - x1));
                    float longestEdge2 = Math.max(
                            squaredDistance(x1, y1, x2, y2),
                            Math.max(squaredDistance(x2, y2, x3, y3),
                                    squaredDistance(x3, y3, x1, y1)));
                    // A large, well-shaped triangle is less sensitive to local expression noise
                    // than a thin triangle, while vertex IDs remain fixed for the model lifetime.
                    float score = area2 * Math.max(area2, .000001f)
                            / Math.max(longestEdge2, .000001f);
                    MeshAnchorFrame candidate = new MeshAnchorFrame(drawable,
                            target.getDrawableId(drawable).getString(), v1, v2, v3,
                            new float[]{x1, y1, x2, y2, x3, y3});
                    if (score > bestAnyScore) {
                        bestAnyScore = score;
                        bestAny = candidate;
                    }
                    if (visible && score > bestVisibleScore) {
                        bestVisibleScore = score;
                        bestVisible = candidate;
                    }
                }
            }
            return bestVisible != null ? bestVisible : bestAny;
        }

        /**
         * Selects a fixed triangle near a normalized point on one known stable drawable. The
         * triangle is used only as a positional pin; rotation and scale come from the large head
         * carrier, avoiding noisy rotation from small facial triangles.
         */
        static MeshAnchorFrame fromTriangleNearNormalizedPoint(
                com.live2d.sdk.cubism.framework.model.CubismModel target,
                int drawable, float normalizedX, float normalizedY) {
            if (target == null || drawable < 0 || drawable >= target.getDrawableCount()) {
                return null;
            }
            float[] vertices = target.getDrawableVertices(drawable);
            short[] indices = target.getDrawableVertexIndices(drawable);
            if (vertices == null || vertices.length < 6 || indices == null) return null;
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i + 1 < vertices.length; i += 2) {
                minX = Math.min(minX, vertices[i]);
                maxX = Math.max(maxX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                maxY = Math.max(maxY, vertices[i + 1]);
            }
            float width = Math.max(1e-6f, maxX - minX);
            float height = Math.max(1e-6f, maxY - minY);
            float targetX = minX + width * Math.max(0f, Math.min(1f, normalizedX));
            float targetY = minY + height * Math.max(0f, Math.min(1f, normalizedY));
            MeshAnchorFrame best = null;
            float bestScore = Float.POSITIVE_INFINITY;
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int v1 = indices[i] & 0xffff;
                int v2 = indices[i + 1] & 0xffff;
                int v3 = indices[i + 2] & 0xffff;
                if (!validVertex(vertices, v1) || !validVertex(vertices, v2)
                        || !validVertex(vertices, v3)) continue;
                float x1 = vertices[v1 * 2];
                float y1 = vertices[v1 * 2 + 1];
                float x2 = vertices[v2 * 2];
                float y2 = vertices[v2 * 2 + 1];
                float x3 = vertices[v3 * 2];
                float y3 = vertices[v3 * 2 + 1];
                float area2 = Math.abs((x2 - x1) * (y3 - y1)
                        - (y2 - y1) * (x3 - x1));
                if (area2 < 1e-8f) continue;
                float centerX = (x1 + x2 + x3) / 3f;
                float centerY = (y1 + y2 + y3) / 3f;
                float dx = (centerX - targetX) / width;
                float dy = (centerY - targetY) / height;
                float longestEdge2 = Math.max(
                        squaredDistance(x1, y1, x2, y2),
                        Math.max(squaredDistance(x2, y2, x3, y3),
                                squaredDistance(x3, y3, x1, y1)));
                float shapeQuality = area2 * area2
                        / Math.max(longestEdge2 * longestEdge2, 1e-12f);
                float score = dx * dx + dy * dy + .0025f / Math.max(shapeQuality, .01f);
                if (score < bestScore) {
                    bestScore = score;
                    best = new MeshAnchorFrame(drawable,
                            target.getDrawableId(drawable).getString(), v1, v2, v3,
                            new float[]{x1, y1, x2, y2, x3, y3});
                }
            }
            return best;
        }

        private static float squaredDistance(float x1, float y1, float x2, float y2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            return dx * dx + dy * dy;
        }
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

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("drawableId", drawableId)
                    .put("triangleVertexIds", new JSONArray(Arrays.asList(vertex1, vertex2, vertex3)))
                    .put("barycentricWeights", new JSONArray(Arrays.asList(weight1, weight2, weight3)));
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

    /** Compact geometry fingerprint used only during the one-time rabbit-ear dependency scan. */
    private static final class DrawableSignature {
        private static final float GEOMETRY_EPSILON = 0.00001f;
        private static final float OPACITY_EPSILON = 0.0001f;

        final float opacity;
        final float minimumX;
        final float maximumX;
        final float minimumY;
        final float maximumY;
        final float sumX;
        final float sumY;
        final float sumSquares;

        DrawableSignature(float opacity, float minimumX, float maximumX,
                          float minimumY, float maximumY, float sumX, float sumY,
                          float sumSquares) {
            this.opacity = opacity;
            this.minimumX = minimumX;
            this.maximumX = maximumX;
            this.minimumY = minimumY;
            this.maximumY = maximumY;
            this.sumX = sumX;
            this.sumY = sumY;
            this.sumSquares = sumSquares;
        }

        static DrawableSignature capture(
                com.live2d.sdk.cubism.framework.model.CubismModel model, int drawable) {
            float[] vertices = model.getDrawableVertices(drawable);
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float sumX = 0f;
            float sumY = 0f;
            float sumSquares = 0f;
            if (vertices != null) for (int i = 0; i + 1 < vertices.length; i += 2) {
                float x = vertices[i];
                float y = vertices[i + 1];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sumX += x;
                sumY += y;
                sumSquares += x * x + y * y;
            }
            if (!Float.isFinite(minX)) minX = maxX = minY = maxY = 0f;
            return new DrawableSignature(model.getDrawableOpacity(drawable),
                    minX, maxX, minY, maxY, sumX, sumY, sumSquares);
        }

        boolean differsFrom(DrawableSignature other) {
            return other == null
                    || Math.abs(opacity - other.opacity) > OPACITY_EPSILON
                    || Math.abs(minimumX - other.minimumX) > GEOMETRY_EPSILON
                    || Math.abs(maximumX - other.maximumX) > GEOMETRY_EPSILON
                    || Math.abs(minimumY - other.minimumY) > GEOMETRY_EPSILON
                    || Math.abs(maximumY - other.maximumY) > GEOMETRY_EPSILON
                    || Math.abs(sumX - other.sumX) > GEOMETRY_EPSILON
                    || Math.abs(sumY - other.sumY) > GEOMETRY_EPSILON
                    || Math.abs(sumSquares - other.sumSquares) > GEOMETRY_EPSILON;
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
        if (compositeRole == CompositeModelRole.MAID_PRIMARY) {
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
