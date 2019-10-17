package com.pedro.encoder.video;

import android.media.MediaCodec;

import android.media.MediaFormat;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 20/01/17.
 */

public interface GetVideoData {

  void onSpsPps(ByteBuffer sps, ByteBuffer pps);

  void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

  /**
   * @param encodedVideoBuffer h264 buffer or other encoded video data buffer
   */
  void getVideoData(ByteBuffer encodedVideoBuffer, MediaCodec.BufferInfo info);

  void onVideoFormat(MediaFormat mediaFormat);
}
