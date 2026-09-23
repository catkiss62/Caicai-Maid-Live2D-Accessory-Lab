package com.catkiss.senlive2dcompanion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Locale;

final class OverlayCalibration {
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
                    "\nSen原生双耳 · 整体旋转 %+.1f°",
                    value.pairRotation);
        }
        return base;
    }

    private OverlayCalibration copy() {
        OverlayCalibration result = new OverlayCalibration();
        result.transforms.clear();
        result.transforms.putAll(transforms);
        return result;
    }

    private static Transform defaultTransform(CompositeOverlayGroup group) {
        // Raw values confirmed by the v0.1.2 on-device report. Existing installs keep their saved
        // values; reset and clean imports now reproduce the same bind pose.
        if (group == CompositeOverlayGroup.GLOBAL) {
            return new Transform(1f, 0f, .20f, true, 0f, 0f, 0f);
        }
        if (group == CompositeOverlayGroup.AHOGE) {
            return new Transform(.88f, -.02f, -.05f, true, 0f, 0f, 0f);
        }
        if (group == CompositeOverlayGroup.EAR_FINS) {
            // v0.1.2's scale/position and head-tilt compensation remain valid. The old symmetric
            // rotation and spacing values belonged to an App-created mirrored ear and must not be
            // applied to Sen's native two-ear rig.
            return new Transform(1.14f, .01f, -.29f, true, 0f, 0f, -10f);
        }
        if (group == CompositeOverlayGroup.TAIL) {
            return new Transform(.96f, .02f, -.24f, true, 0f, 0f, 0f);
        }
        return new Transform(1f, 0f, 0f, true, 0f, 0f, 0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
