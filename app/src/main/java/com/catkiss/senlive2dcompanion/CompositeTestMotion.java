package com.catkiss.senlive2dcompanion;

enum CompositeTestMotion {
    LIVE("live", "实时动作"),
    NEUTRAL("neutral", "固定中立"),
    HEAD_SWEEP("head_sweep", "头部摆动"),
    BODY_SWEEP("body_sweep", "身体摆动"),
    ARM_SWEEP("arm_sweep", "双臂摆动"),
    AUTO("auto", "自动巡检");

    final String id;
    final String displayName;

    CompositeTestMotion(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    static CompositeTestMotion fromId(String id) {
        for (CompositeTestMotion value : values()) if (value.id.equals(id)) return value;
        return LIVE;
    }
}
