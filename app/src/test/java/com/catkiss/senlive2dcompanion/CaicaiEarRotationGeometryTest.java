package com.catkiss.senlive2dcompanion;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaicaiEarRotationGeometryTest {
    @Test public void bothEarRotationsPreservePixelLengthsAndPivotAtAllStageRatios() {
        for(float aspect:new float[]{.45f,.58f,.8f,1f,1.8f})
        for(float scale:new float[]{.35f,1f,2.5f})
        for(float angle:new float[]{5,-9,14,19}) {
            float c=(float)Math.cos(Math.toRadians(angle)),s=(float)Math.sin(Math.toRadians(angle));
            float x=.31f,y=-.24f,tx=.04f,ty=-.02f;
            float[] m={c,s,0,0,-s,c,0,0,0,0,1,0,x-c*x+s*y+tx,y-s*x-c*y+ty,0,1};
            CaicaiEarRotation.correct(m,1000*aspect,1000,x,y);
            assertEquals(x+tx,m[0]*x+m[4]*y+m[12],.00001f);
            assertEquals(y+ty,m[1]*x+m[5]*y+m[13],.00001f);
            for(float[] v:new float[][]{{.2f,0},{0,.2f},{.13f,-.09f}}) {
                double before=Math.hypot(v[0]*aspect*scale,v[1]*scale);
                double after=Math.hypot((m[0]*v[0]+m[4]*v[1])*aspect*scale,
                        (m[1]*v[0]+m[5]*v[1])*scale);
                assertEquals(before,after,.000001);
            }
        }
    }
}
