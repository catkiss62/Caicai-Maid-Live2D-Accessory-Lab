package com.catkiss.senlive2dcompanion;

enum CompositeTestMotion {
    LIVE("live", "实时动作"),
    NEUTRAL("neutral", "固定中立"),
    HEAD_SWEEP("head_sweep", "头部摆动"),
    HEAD_X_SWEEP("head_x_sweep", "头部左右大幅"),
    HEAD_Y_SWEEP("head_y_sweep", "头部上下大幅"),
    HEAD_Z_SWEEP("head_z_sweep", "头部歪斜大幅"),
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
