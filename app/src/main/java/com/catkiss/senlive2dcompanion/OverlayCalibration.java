package com.catkiss.senlive2dcompanion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Locale;

final class OverlayCalibration {
    static final class EarFineTune {
        final float scale;
        final float x;
        final float y;
        final float rotation;

        EarFineTune(float scale, float x, float y, float rotation) {
            this.scale = clamp(scale, .70f, 1.30f);
            this.x = clamp(x, -.30f, .30f);
            this.y = clamp(y, -.30f, .30f);
            this.rotation = clamp(rotation, -45f, 45f);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("scale", scale).put("x", x).put("y", y)
                    .put("rotation", rotation);
        }
    }

    static final class Transform {
        final float scale;
        final float x;
        final float y;
        final boolean visible;
        final float rotation;
        final float spacing;
        final float pairRotation;

        Transform(float scale, float x, float y, boolean visible,
                  float rotation, float spacing, float pairRotation) {
            this.scale = clamp(scale, .55f, 1.55f);
            this.x = clamp(x, -.75f, .75f);
            this.y = clamp(y, -.90f, .90f);
            this.visible = visible;
            this.rotation = clamp(rotation, -45f, 45f);
            this.spacing = clamp(spacing, -.45f, .45f);
            this.pairRotation = clamp(pairRotation, -45f, 45f);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("scale", scale).put("x", x).put("y", y)
                    .put("visible", visible).put("rotation", rotation)
                    .put("spacing", spacing).put("pair_rotation", pairRotation);
        }
    }

    private final EnumMap<CompositeOverlayGroup, Transform> transforms =
            new EnumMap<>(CompositeOverlayGroup.class);
    private EarFineTune screenLeftEar = defaultEarFineTune();
    private EarFineTune screenRightEar = defaultEarFineTune();
    private int ahogeLayerOffset;
    private int screenLeftEarLayerOffset;
    private int screenRightEarLayerOffset;

    private OverlayCalibration() {
        for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
            transforms.put(group, defaultTransform(group));
        }
    }

    static OverlayCalibration defaults() { return new OverlayCalibration(); }

    static OverlayCalibration fromJson(String raw) {
        OverlayCalibration result = defaults();
        if (raw == null || raw.isBlank()) return result;
        try {
            JSONObject root = new JSONObject(raw);
            for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
                JSONObject item = root.optJSONObject(group.id);
                if (item == null) continue;
                result.transforms.put(group, new Transform(
                        (float) item.optDouble("scale", 1.0),
                        (float) item.optDouble("x", 0.0),
                        (float) item.optDouble("y", 0.0),
                        item.optBoolean("visible", true),
                        (float) item.optDouble("rotation", 0.0),
                        (float) item.optDouble("spacing", 0.0),
                        (float) item.optDouble("pair_rotation", 0.0)));
            }
            result.screenLeftEar = earFineTuneFromJson(
                    root.optJSONObject("ear_fins_screen_left"));
            result.screenRightEar = earFineTuneFromJson(
                    root.optJSONObject("ear_fins_screen_right"));
            JSONObject layers = root.optJSONObject("part_layer_offsets");
            if (layers != null) {
                result.ahogeLayerOffset = clampLayerOffset(layers.optInt("ahoge", 0));
                result.screenLeftEarLayerOffset = clampLayerOffset(
                        layers.optInt("ear_fins_screen_left", 0));
                result.screenRightEarLayerOffset = clampLayerOffset(
                        layers.optInt("ear_fins_screen_right", 0));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    OverlayCalibration withDelta(CompositeOverlayGroup group,
                                 float scaleDelta, float xDelta, float yDelta) {
        OverlayCalibration result = copy();
        Transform old = get(group);
        result.transforms.put(group, new Transform(old.scale + scaleDelta,
                old.x + xDelta, old.y + yDelta, old.visible,
                old.rotation, old.spacing, old.pairRotation));
        return result;
    }

    OverlayCalibration withEarDelta(float rotationDelta, float spacingDelta,
                                    float pairRotationDelta) {
        OverlayCalibration result = copy();
        Transform old = get(CompositeOverlayGroup.EAR_FINS);
        result.transforms.put(CompositeOverlayGroup.EAR_FINS,
                new Transform(old.scale, old.x, old.y, old.visible,
                        old.rotation + rotationDelta, old.spacing + spacingDelta,
                        old.pairRotation + pairRotationDelta));
        return result;
    }

    OverlayCalibration withEarSideDelta(boolean screenLeft,
                                        float scaleDelta, float xDelta,
                                        float yDelta, float rotationDelta) {
        OverlayCalibration result = copy();
        EarFineTune old = getEarFineTune(screenLeft);
        EarFineTune next = new EarFineTune(old.scale + scaleDelta,
                old.x + xDelta, old.y + yDelta, old.rotation + rotationDelta);
        if (screenLeft) result.screenLeftEar = next;
        else result.screenRightEar = next;
        return result;
    }

    OverlayCalibration resetEarSide(boolean screenLeft) {
        OverlayCalibration result = copy();
        if (screenLeft) {
            result.screenLeftEar = defaultEarFineTune();
            result.screenLeftEarLayerOffset = 0;
        } else {
            result.screenRightEar = defaultEarFineTune();
            result.screenRightEarLayerOffset = 0;
        }
        return result;
    }

    EarFineTune getEarFineTune(boolean screenLeft) {
        return screenLeft ? screenLeftEar : screenRightEar;
    }

    OverlayCalibration withLayerOffsetDelta(CompositeOverlayGroup group,
                                            Boolean screenLeft, int delta) {
        OverlayCalibration result = copy();
        if (group == CompositeOverlayGroup.AHOGE) {
            result.ahogeLayerOffset = clampLayerOffset(ahogeLayerOffset + delta);
        } else if (group == CompositeOverlayGroup.EAR_FINS) {
            if (screenLeft == null || screenLeft) {
                result.screenLeftEarLayerOffset = clampLayerOffset(
                        screenLeftEarLayerOffset + delta);
            }
            if (screenLeft == null || !screenLeft) {
                result.screenRightEarLayerOffset = clampLayerOffset(
                        screenRightEarLayerOffset + delta);
            }
        }
        return result;
    }

    int getLayerOffset(CompositeOverlayGroup group, boolean screenLeft) {
        if (group == CompositeOverlayGroup.AHOGE) return ahogeLayerOffset;
        if (group == CompositeOverlayGroup.EAR_FINS) {
            return screenLeft ? screenLeftEarLayerOffset : screenRightEarLayerOffset;
        }
        return 0;
    }

    OverlayCalibration withVisible(CompositeOverlayGroup group, boolean visible) {
        OverlayCalibration result = copy();
        Transform old = get(group);
        result.transforms.put(group, new Transform(old.scale, old.x, old.y, visible,
                old.rotation, old.spacing, old.pairRotation));
        return result;
    }

    OverlayCalibration reset(CompositeOverlayGroup group) {
        OverlayCalibration result = copy();
        result.transforms.put(group, defaultTransform(group));
        if (group == CompositeOverlayGroup.AHOGE) {
            result.ahogeLayerOffset = 0;
        } else if (group == CompositeOverlayGroup.EAR_FINS) {
            result.screenLeftEarLayerOffset = 0;
            result.screenRightEarLayerOffset = 0;
        }
        return result;
    }

    Transform get(CompositeOverlayGroup group) {
        Transform value = transforms.get(group);
        return value == null ? new Transform(1f, 0f, 0f, true, 0f, 0f, 0f) : value;
    }

    float combinedScale(CompositeOverlayGroup group) {
        return get(CompositeOverlayGroup.GLOBAL).scale
                * (group == CompositeOverlayGroup.GLOBAL ? 1f : get(group).scale);
    }

    float combinedX(CompositeOverlayGroup group) {
        return get(CompositeOverlayGroup.GLOBAL).x
                + (group == CompositeOverlayGroup.GLOBAL ? 0f : get(group).x);
    }

    float combinedY(CompositeOverlayGroup group) {
        return get(CompositeOverlayGroup.GLOBAL).y
                + (group == CompositeOverlayGroup.GLOBAL ? 0f : get(group).y);
    }

    boolean isVisible(CompositeOverlayGroup group) {
        return get(CompositeOverlayGroup.GLOBAL).visible
                && (group == CompositeOverlayGroup.GLOBAL || get(group).visible);
    }

    JSONObject toJsonObject() throws JSONException {
        JSONObject root = new JSONObject();
        for (CompositeOverlayGroup group : CompositeOverlayGroup.values()) {
            root.put(group.id, get(group).toJson());
        }
        root.put("ear_fins_screen_left", screenLeftEar.toJson());
        root.put("ear_fins_screen_right", screenRightEar.toJson());
        root.put("part_layer_offsets", new JSONObject()
                .put("ahoge", ahogeLayerOffset)
                .put("ear_fins_screen_left", screenLeftEarLayerOffset)
                .put("ear_fins_screen_right", screenRightEarLayerOffset));
        return root;
    }

    String toPreferenceJson() {
        try { return toJsonObject().toString(); }
        catch (JSONException ignored) { return "{}"; }
    }

    String describe(CompositeOverlayGroup group) {
        Transform value = get(group);
        String base = String.format(Locale.ROOT, "%s：缩放 %.2f · X %+.2f · Y %+.2f · %s",
                group.displayName, value.scale, value.x, value.y,
                value.visible ? "显示" : "隐藏");
        if (group == CompositeOverlayGroup.EAR_FINS) {
            return base + String.format(Locale.ROOT,
                    "\nSen原生双耳 · 整体旋转 %+.1f°"
                            + "\n画面左：缩放 %.2f · X %+.2f · Y %+.2f · 旋转 %+.1f° · 图层 %+d"
                            + "\n画面右：缩放 %.2f · X %+.2f · Y %+.2f · 旋转 %+.1f° · 图层 %+d",
                    value.pairRotation,
                    screenLeftEar.scale, screenLeftEar.x, screenLeftEar.y,
                    screenLeftEar.rotation, screenLeftEarLayerOffset,
                    screenRightEar.scale, screenRightEar.x, screenRightEar.y,
                    screenRightEar.rotation, screenRightEarLayerOffset);
        }
        if (group == CompositeOverlayGroup.AHOGE) {
            return base + String.format(Locale.ROOT,
                    "\n部件图层偏移：%+d", ahogeLayerOffset);
        }
        return base;
    }

    private OverlayCalibration copy() {
        OverlayCalibration result = new OverlayCalibration();
        result.transforms.clear();
        result.transforms.putAll(transforms);
        result.screenLeftEar = screenLeftEar;
        result.screenRightEar = screenRightEar;
        result.ahogeLayerOffset = ahogeLayerOffset;
        result.screenLeftEarLayerOffset = screenLeftEarLayerOffset;
        result.screenRightEarLayerOffset = screenRightEarLayerOffset;
        return result;
    }

    private static EarFineTune earFineTuneFromJson(JSONObject object) {
        if (object == null) return defaultEarFineTune();
        return new EarFineTune((float) object.optDouble("scale", 1.0),
                (float) object.optDouble("x", 0.0),
                (float) object.optDouble("y", 0.0),
                (float) object.optDouble("rotation", 0.0));
    }

    private static EarFineTune defaultEarFineTune() {
        // Identity is essential: splitting the native pair must not move the confirmed v0.1.12
        // neutral placement by even one calibration step.
        return new EarFineTune(1f, 0f, 0f, 0f);
    }

    private static Transform defaultTransform(CompositeOverlayGroup group) {
        // Binding zero pose confirmed by the v0.1.6 on-device report. Existing installs keep their
        // saved values; reset and clean imports reproduce the same independently aligned pose.
        if (group == CompositeOverlayGroup.GLOBAL) {
            return new Transform(1f, 0f, .20f, true, 0f, 0f, 0f);
        }
        if (group == CompositeOverlayGroup.AHOGE) {
            return new Transform(.88f, -.03f, -.05f, true, 0f, 0f, 0f);
        }
        if (group == CompositeOverlayGroup.EAR_FINS) {
            return new Transform(1.24f, -.01f, -.29f, true, 0f, 0f, 5f);
        }
        if (group == CompositeOverlayGroup.TAIL) {
            return new Transform(1.00f, .02f, -.24f, true, 0f, 0f, 0f);
        }
        return new Transform(1f, 0f, 0f, true, 0f, 0f, 0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clampLayerOffset(int value) {
        return Math.max(-8, Math.min(8, value));
    }
}
