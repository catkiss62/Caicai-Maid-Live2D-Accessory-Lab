package com.catkiss.senlive2dcompanion;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaicaiRightEarAdjustmentTest {
    @Test public void acceptedPlacementMovesAllPointsRigidlyAndScalesWithEarWidth() {
        assertEquals(-.075f,CaicaiRightEarAdjustment.X,0);
        assertEquals(.04f,CaicaiRightEarAdjustment.Y,0);
        assertEquals(9.45f,CaicaiRightEarAdjustment.ROTATION_DEGREES,0);
        for(float aspect:new float[]{.45f,1f,1.8f}) for(float size:new float[]{.3f,1f,2f}) {
            float[] bounds={-.2f*size,-.1f*size,.3f*size,.2f*size};
            float[] m=CaicaiRightEarAdjustment.matrix(bounds,1000*aspect,1000);
            float x=(bounds[0]+bounds[2])/2,y=(bounds[1]+bounds[3])/2,span=bounds[2]-bounds[0];
            assertEquals(x-.075f*span,m[0]*x+m[4]*y+m[12],.000001f);
            assertEquals(y+.04f*span*aspect,m[1]*x+m[5]*y+m[13],.000001f);
            for(float[] v:new float[][]{{.12f,0},{0,.08f},{.15f,.07f}})
                assertEquals(Math.hypot(v[0]*aspect,v[1]),Math.hypot((m[0]*v[0]+m[4]*v[1])*aspect,m[1]*v[0]+m[5]*v[1]),.000001);
            float angle=(float)Math.toRadians(9.45);
            assertEquals(Math.cos(angle),m[0],.000001);
        }
    }

    @Test public void acceptedAngleIsCounterclockwiseInScreenPixels() {
        for (float aspect : new float[]{.45f, 1f, 1.8f}) {
            float width = 1000 * aspect, height = 1000;
            float[] m = CaicaiRightEarAdjustment.matrix(new float[]{-.2f,-.1f,.3f,.2f},width,height);
            assertEquals(9.45, Math.toDegrees(Math.atan2(m[1] * height, m[0] * width)), .00001);
        }
    }
    @Test public void missingOrInvalidBoundsDoNotMoveTheEar() {
        float[] identity = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        for (float[] bounds : new float[][]{null, new float[]{0}, new float[]{0,0,0,1},
                new float[]{1,0,0,1}, new float[]{Float.NaN,0,1,1}})
            assertArrayEquals(identity, CaicaiRightEarAdjustment.matrix(bounds,1000,2000),0);
        assertArrayEquals(identity, CaicaiRightEarAdjustment.matrix(new float[]{0,0,1,1},0,2000),0);
        assertArrayEquals(identity, CaicaiRightEarAdjustment.matrix(new float[]{0,0,1,1},1000,0),0);
    }
}
