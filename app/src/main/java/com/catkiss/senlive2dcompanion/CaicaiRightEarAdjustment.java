package com.catkiss.senlive2dcompanion;

/** Accepted screen-right ear placement for every drawable in the ear pass.
 * Calibrated on device in +332; offsets use the unadjusted ear width and follow
 * stage/form scaling. Applied after pair constraints, before the common root. */
final class CaicaiRightEarAdjustment {
    static final float X = -.075f, Y = .04f, ROTATION_DEGREES = 9.45f;
    static float[] matrix(float[] bounds, float width, float height) {
        float[] m = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        if (bounds == null || bounds.length < 4 || !(width > 0) || !(height > 0)) return m;
        float span = bounds[2] - bounds[0];
        float x = (bounds[0] + bounds[2]) * .5f, y = (bounds[1] + bounds[3]) * .5f;
        if (!(span > 0) || !Float.isFinite(span) || !Float.isFinite(x) || !Float.isFinite(y)) return m;
        double angle = Math.toRadians(ROTATION_DEGREES);
        float c = (float)Math.cos(angle), s = (float)Math.sin(angle);
        m[0] = c; m[1] = s; m[4] = -s; m[5] = c;
        m[12] = x-c*x+s*y+X*span;
        m[13] = y-s*x-c*y+Y*span*width/height;
        CaicaiEarRotation.correct(m,width,height,x,y);
        return m;
    }
}
