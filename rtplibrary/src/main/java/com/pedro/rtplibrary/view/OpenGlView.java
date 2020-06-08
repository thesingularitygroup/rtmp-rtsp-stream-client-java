package com.pedro.rtplibrary.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.rtplibrary.R;

/**
 * Created by pedro on 9/09/17.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlView extends OpenGlViewBase {

  private ManagerRender managerRender = null;
  private boolean loadAA = false;

  private boolean AAEnabled = false;
  private boolean keepAspectRatio = false;
  private boolean isFlipHorizontal = false, isFlipVertical = false;
  private boolean isOutputFlipHorizontal = false, isOutputFlipVertical = false;

  public OpenGlView(Context context) {
    super(context);
  }

  public OpenGlView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.OpenGlView);
    try {
      keepAspectRatio = typedArray.getBoolean(R.styleable.OpenGlView_keepAspectRatio, false);
      AAEnabled = typedArray.getBoolean(R.styleable.OpenGlView_AAEnabled, false);
      ManagerRender.numFilters = typedArray.getInt(R.styleable.OpenGlView_numFilters, 1);
      isFlipHorizontal = typedArray.getBoolean(R.styleable.OpenGlView_isFlipHorizontal, false);
      isFlipVertical = typedArray.getBoolean(R.styleable.OpenGlView_isFlipVertical, false);
    } finally {
      typedArray.recycle();
    }
  }

  @Override
  public void init() {
    if (!initialized) managerRender = new ManagerRender();
    managerRender.setCameraFlip(isFlipHorizontal, isFlipVertical);
    initialized = true;
  }

  @Override
  public SurfaceTexture getSurfaceTexture() {
    return managerRender.getSurfaceTexture();
  }

  @Override
  public Surface getSurface() {
    return managerRender.getSurface();
  }

  @Override
  public void setFilter(int filterPosition, BaseFilterRender baseFilterRender) {
    filterQueue.add(new Filter(filterPosition, baseFilterRender));
  }

  @Override
  public void setFilter(BaseFilterRender baseFilterRender) {
    setFilter(0, baseFilterRender);
  }

  @Override
  public void enableAA(boolean AAEnabled) {
    this.AAEnabled = AAEnabled;
    loadAA = true;
  }

  @Override
  public void setRotation(int rotation) {
    managerRender.setCameraRotation(rotation);
  }

  public boolean isKeepAspectRatio() {
    return keepAspectRatio;
  }

  public void setKeepAspectRatio(boolean keepAspectRatio) {
    this.keepAspectRatio = keepAspectRatio;
  }

  /**
   * Set flip of the camera feed.
   * This will change how the preview looks and also the streamed/recorded video.
   */
  public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    managerRender.setCameraFlip(isFlipHorizontal, isFlipVertical);
  }

  /**
   * Set flip of the output video.
   * This will change how the streamed/recorded video will look like.
   * The preview on screen will not be affected.
   */
  public void setOutputFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    this.isOutputFlipHorizontal = isFlipHorizontal;
    this.isOutputFlipVertical = isFlipVertical;
  }

  @Override
  public boolean isAAEnabled() {
    return managerRender != null && managerRender.isAAEnabled();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    Log.i(TAG, "size: " + width + "x" + height);
    this.previewWidth = width;
    this.previewHeight = height;
    if (managerRender != null) managerRender.setPreviewSize(previewWidth, previewHeight);
  }

  @Override
  public void run() {
    releaseSurfaceManager();
    surfaceManager = new SurfaceManager(getHolder().getSurface());
    surfaceManager.makeCurrent();
    managerRender.initGl(getContext(), cameraRenderWidth, cameraRenderHeight, previewWidth, previewHeight);
    managerRender.getSurfaceTexture().setOnFrameAvailableListener(this);
    semaphore.release();
    try {
      while (running) {
        if (frameAvailable) {
          frameAvailable = false;
          surfaceManager.makeCurrent();
          managerRender.updateFrame();
          // draw camera at preview size, then draw filters at encoder size
          managerRender.drawOffScreen();
          // draw the the last filter (which contains result of all filters)
          // will be scaled correctly to fit the preview size
          managerRender.drawScreen(previewWidth, previewHeight, keepAspectRatio); //PREVIEW
          surfaceManager.swapBuffer();
          if (takePhotoCallback != null) {
            takePhotoCallback.onTakePhoto(
                GlUtil.getBitmap(previewWidth, previewHeight, encoderWidth, encoderHeight));
            takePhotoCallback = null;
          }
          synchronized (sync) {
            if (surfaceManagerEncoder != null  && !fpsLimiter.limitFPS()) {
              surfaceManagerEncoder.makeCurrent();
              // flip the output as specified by setOutputFlip
              managerRender.setScreenFlip(isOutputFlipHorizontal, isOutputFlipVertical);
              // draw to output video
              managerRender.drawScreen(encoderWidth, encoderHeight, false); //OUTPUT (currently flipped)
              // disable flipping (so to not affect the preview)
              managerRender.setScreenFlip(false, false);
              surfaceManagerEncoder.swapBuffer();
            }
          }
          if (!filterQueue.isEmpty()) {
            Filter filter = filterQueue.take();
            managerRender.setFilter(filter.getPosition(), filter.getBaseFilterRender());
          } else if (loadAA) {
            managerRender.enableAA(AAEnabled);
            loadAA = false;
          }
        }
      }
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    } finally {
      managerRender.release();
      releaseSurfaceManager();
    }
  }
}