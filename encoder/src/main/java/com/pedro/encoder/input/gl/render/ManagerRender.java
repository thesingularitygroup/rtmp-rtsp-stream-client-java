package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pedro on 27/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ManagerRender {

  //Increase it to render more than 1 filter and set filter by position.
  // You must modify it before create your rtmp or rtsp object.
  public static int numFilters = 1;

  private CameraRender cameraRender;
  private List<BaseFilterRender> baseFilterRender = new ArrayList<>(numFilters);
  private ScreenRender screenRender;

  private int width;
  private int height;
  private int previewWidth;
  private int previewHeight;
  private Context context;

  public ManagerRender() {
    cameraRender = new CameraRender();
    for (int i = 0; i < numFilters; i++) baseFilterRender.add(new NoFilterRender());
    screenRender = new ScreenRender();
  }

  /**
   * @param cameraWidth internal rendering width of the camera's surface texture
   * @param cameraHeight internal rendering height of the camera's surface texture
   */
  public void initGl(Context context, int cameraWidth, int cameraHeight, int previewWidth,
                     int previewHeight) {
    this.context = context;
    this.width = cameraWidth;
    this.height = cameraHeight;
    this.previewWidth = previewWidth;
    this.previewHeight = previewHeight;
    cameraRender.initGl(width, height, context, previewWidth, previewHeight);
    for (int i = 0; i < numFilters; i++) {
      int textId = i == 0 ? cameraRender.getTexId() : baseFilterRender.get(i - 1).getTexId();
      baseFilterRender.get(i).setPreviousTexId(textId);
      baseFilterRender.get(i).initGl(width, height, context, previewWidth, previewHeight);
      baseFilterRender.get(i).initFBOLink();
    }
    // set size as rendered by drawOffScreen
    // screenRender is always drawn after drawOffScreen() - this is used to calculate scaling
    screenRender.setOffScreenSize(cameraWidth, cameraHeight);
    screenRender.setTexId(baseFilterRender.get(numFilters - 1).getTexId());
    screenRender.initGl(context);
  }

  public void drawOffScreen() {
    cameraRender.draw();
    for (BaseFilterRender baseFilterRender : baseFilterRender) baseFilterRender.draw();
  }

  public void drawScreen(int width, int height, boolean keepAspectRatio) {
    screenRender.draw(width, height, keepAspectRatio);
  }

  public void release() {
    cameraRender.release();
    for (int i = 0; i < this.baseFilterRender.size(); i++) {
      this.baseFilterRender.get(i).release();
      this.baseFilterRender.set(i, new NoFilterRender());
    }
    screenRender.release();
  }

  public void enableAA(boolean AAEnabled) {
    screenRender.setAAEnabled(AAEnabled);
  }

  public boolean isAAEnabled() {
    return screenRender.isAAEnabled();
  }

  public void updateFrame() {
    cameraRender.updateTexImage();
  }

  public SurfaceTexture getSurfaceTexture() {
    return cameraRender.getSurfaceTexture();
  }

  public Surface getSurface() {
    return cameraRender.getSurface();
  }

  public void setFilter(int position, BaseFilterRender baseFilterRender) {
    final int id = this.baseFilterRender.get(position).getPreviousTexId();
    final RenderHandler renderHandler = this.baseFilterRender.get(position).getRenderHandler();
    this.baseFilterRender.get(position).release();
    this.baseFilterRender.set(position, baseFilterRender);
    this.baseFilterRender.get(position).setPreviousTexId(id);
    this.baseFilterRender.get(position).initGl(width, height, context, previewWidth, previewHeight);
    this.baseFilterRender.get(position).setRenderHandler(renderHandler);
  }

  public void setCameraRotation(int rotation) {
    cameraRender.setRotation(rotation);
  }

  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    cameraRender.setFlip(isFlipHorizontal, isFlipVertical);
  }

  public void setPreviewSize(int previewWidth, int previewHeight) {
    for (int i = 0; i < this.baseFilterRender.size(); i++) {
      this.baseFilterRender.get(i).setPreviewSize(previewWidth, previewHeight);
    }
  }
}
