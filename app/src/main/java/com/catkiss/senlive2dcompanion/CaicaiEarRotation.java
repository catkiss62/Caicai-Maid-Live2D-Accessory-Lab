package com.catkiss.senlive2dcompanion;

/** Convert a clip-space ear rotation into a pixel-isotropic rotation.
 * Keeps its pivot and existing calibration translation unchanged. The logical
 * scene height (not the keyboard-clipped surface) is the projection metric. */
final class CaicaiEarRotation {
    static void correct(float[] matrix, float width, float height, float x, float y) {
        if (!(width > 0) || !(height > 0) || !Float.isFinite(width) || !Float.isFinite(height)) return;
        float aspect = width / height;
        float oldB = matrix[1], oldC = matrix[4];
        matrix[1] = oldB * aspect;
        matrix[4] = oldC / aspect;
        matrix[12] += (oldC - matrix[4]) * y;
        matrix[13] += (oldB - matrix[1]) * x;
    }
}
