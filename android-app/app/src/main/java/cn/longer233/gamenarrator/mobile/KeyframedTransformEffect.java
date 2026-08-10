package cn.longer233.gamenarrator.mobile;

import android.graphics.Matrix;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;
import java.util.List;

@UnstableApi
public final class KeyframedTransformEffect implements MatrixTransformation {
    private final List<ProjectRepository.KeyframeInfo> scales,rotations,xPositions,yPositions;private final float baseScale,baseRotation;
    public KeyframedTransformEffect(List<ProjectRepository.KeyframeInfo> scales,List<ProjectRepository.KeyframeInfo> rotations,List<ProjectRepository.KeyframeInfo> xPositions,List<ProjectRepository.KeyframeInfo> yPositions,float baseScale,float baseRotation){this.scales=scales;this.rotations=rotations;this.xPositions=xPositions;this.yPositions=yPositions;this.baseScale=baseScale;this.baseRotation=baseRotation;}
    @Override public Size configure(int inputWidth,int inputHeight){return new Size(inputWidth,inputHeight);}
    @Override public Matrix getMatrix(long presentationTimeUs){long ms=Math.max(0,presentationTimeUs/1000);float scale=KeyframeInterpolator.valueAt(scales,ms,baseScale),rotation=KeyframeInterpolator.valueAt(rotations,ms,baseRotation),x=KeyframeInterpolator.valueAt(xPositions,ms,0),y=KeyframeInterpolator.valueAt(yPositions,ms,0);Matrix matrix=new Matrix();matrix.postScale(scale,scale);matrix.postRotate(rotation);matrix.postTranslate(x,y);return matrix;}
    @Override public boolean isNoOp(int inputWidth,int inputHeight){return scales.isEmpty()&&rotations.isEmpty()&&xPositions.isEmpty()&&yPositions.isEmpty()&&Math.abs(baseScale-1f)<.001f&&Math.abs(baseRotation)<.001f;}
}
