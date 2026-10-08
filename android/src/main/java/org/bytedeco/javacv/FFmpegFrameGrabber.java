package org.bytedeco.javacv;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.Image;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Android replacement for javacv's FFmpegFrameGrabber, backed by
 * MediaExtractor + MediaCodec. Implements the members upstream VideoPlayer
 * uses with the same semantics:
 * <ul>
 *   <li>grabFrame() returns the next decoded frame (audio or video); audio
 *       frames carry an interleaved PCM16 ShortBuffer in samples[0], video
 *       frames a tightly packed RGB24 ByteBuffer in image[0].</li>
 *   <li>grabImage() returns the next decoded video frame only.</li>
 *   <li>setFrameNumber(0) rewinds to the start (seek + codec flush).</li>
 * </ul>
 * Two extractors are used so the audio and video streams can be drained
 * independently.
 */
public class FFmpegFrameGrabber {
  static {
    // libscopus also carries the YUV->RGB helper (native: Java-side per-pixel
    // conversion over direct buffers is far too slow for 720p)
    System.loadLibrary("scopus");
  }

  private static native void yuv2rgb(
      ByteBuffer yBuf, int yRow, int yPix,
      ByteBuffer uBuf, int uRow, int uPix,
      ByteBuffer vBuf, int vRow, int vPix,
      ByteBuffer out, int width, int height);

  public static class Exception extends IOException {
    public Exception(final String message) {
      super(message);
    }

    public Exception(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  private static final long TIMEOUT_US = 10_000;

  private final File file;

  private MediaExtractor videoExtractor;
  private MediaExtractor audioExtractor;
  private MediaCodec videoDecoder;
  private MediaCodec audioDecoder;

  private int imageWidth;
  private int imageHeight;
  private int sampleRate;
  private int audioChannels;
  private long lengthInTime;
  private int pixelFormat;
  private boolean closeInputStream;

  private final MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
  private final MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
  private boolean videoInputDone;
  private boolean videoOutputDone;
  private boolean audioInputDone;
  private boolean audioOutputDone;

  /** Reused RGB24 output buffer (width*height*3, tightly packed). */
  private ByteBuffer rgbBuffer;
  /** Reused audio staging buffer. */
  private ByteBuffer audioBuffer;

  public FFmpegFrameGrabber(final File file) {
    this.file = file;
  }

  public void setPixelFormat(final int pixelFormat) {
    this.pixelFormat = pixelFormat;
  }

  public void setCloseInputStream(final boolean closeInputStream) {
    this.closeInputStream = closeInputStream;
  }

  public void start() throws Exception {
    try {
      // File.getAbsolutePath() resolves against user.dir ("/"), not the real cwd
      final String path = legend.game.android.JdkCompat.absolute(this.file.toPath()).toString();
      this.videoExtractor = new MediaExtractor();
      this.videoExtractor.setDataSource(path);
      this.audioExtractor = new MediaExtractor();
      this.audioExtractor.setDataSource(path);

      final int videoTrack = findTrack(this.videoExtractor, "video/");
      final int audioTrack = findTrack(this.audioExtractor, "audio/");

      if(videoTrack < 0) {
        throw new Exception("No video track in " + this.file);
      }

      this.videoExtractor.selectTrack(videoTrack);
      final MediaFormat videoFormat = this.videoExtractor.getTrackFormat(videoTrack);
      this.imageWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
      this.imageHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
      if(videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
        this.lengthInTime = videoFormat.getLong(MediaFormat.KEY_DURATION);
      }

      this.videoDecoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
      this.videoDecoder.configure(videoFormat, null, null, 0);
      this.videoDecoder.start();

      if(audioTrack >= 0) {
        this.audioExtractor.selectTrack(audioTrack);
        final MediaFormat audioFormat = this.audioExtractor.getTrackFormat(audioTrack);
        this.sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        this.audioChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if(audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
          this.lengthInTime = Math.max(this.lengthInTime, audioFormat.getLong(MediaFormat.KEY_DURATION));
        }

        this.audioDecoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        this.audioDecoder.configure(audioFormat, null, null, 0);
        this.audioDecoder.start();
      }
    } catch(final IOException e) {
      throw new Exception("Failed to open " + this.file, e);
    }
  }

  private static int findTrack(final MediaExtractor extractor, final String prefix) {
    for(int i = 0; i < extractor.getTrackCount(); i++) {
      if(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith(prefix)) {
        return i;
      }
    }
    return -1;
  }

  public int getImageWidth() {
    return this.imageWidth;
  }

  public int getImageHeight() {
    return this.imageHeight;
  }

  public int getSampleRate() {
    return this.sampleRate;
  }

  public int getAudioChannels() {
    return this.audioChannels;
  }

  public long getLengthInTime() {
    return this.lengthInTime;
  }

  /** Feed one input buffer into the codec from its extractor. */
  private void pumpInput(final MediaCodec codec, final MediaExtractor extractor, final boolean inputDone) throws Exception {
    if(inputDone) {
      return;
    }

    final int index = codec.dequeueInputBuffer(TIMEOUT_US);
    if(index < 0) {
      return;
    }

    final ByteBuffer buffer = codec.getInputBuffer(index);
    final int size = extractor.readSampleData(buffer, 0);
    if(size < 0) {
      codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      if(codec == this.videoDecoder) {
        this.videoInputDone = true;
      } else {
        this.audioInputDone = true;
      }
    } else {
      codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
      extractor.advance();
    }
  }

  /** Returns the next decoded audio frame, or null once the stream is drained. */
  private Frame nextAudioFrame() throws Exception {
    if(this.audioDecoder == null || this.audioOutputDone) {
      return null;
    }

    while(true) {
      this.pumpInput(this.audioDecoder, this.audioExtractor, this.audioInputDone);

      final int index = this.audioDecoder.dequeueOutputBuffer(this.audioInfo, TIMEOUT_US);
      if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        continue;
      }

      if(index == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if(this.audioInputDone) {
          this.audioOutputDone = true;
          return null;
        }
        continue;
      }

      if(index < 0) {
        continue;
      }

      if((this.audioInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        this.audioDecoder.releaseOutputBuffer(index, false);
        this.audioOutputDone = true;
        if(this.audioInfo.size <= 0) {
          return null;
        }
      }

      final ByteBuffer out = this.audioDecoder.getOutputBuffer(index);
      if(out == null || this.audioInfo.size <= 0) {
        this.audioDecoder.releaseOutputBuffer(index, false);
        if(this.audioOutputDone) {
          return null;
        }
        continue;
      }

      final int size = this.audioInfo.size;
      if(this.audioBuffer == null || this.audioBuffer.capacity() < size) {
        this.audioBuffer = ByteBuffer.allocateDirect(size);
      }
      this.audioBuffer.clear();
      this.audioBuffer.put(out);
      this.audioBuffer.flip();
      this.audioDecoder.releaseOutputBuffer(index, false);

      final Frame frame = new Frame();
      frame.timestamp = this.audioInfo.presentationTimeUs;
      this.audioBuffer.order(ByteOrder.LITTLE_ENDIAN);
      frame.samples = new java.nio.Buffer[] { this.audioBuffer.asShortBuffer() };
      return frame;
    }
  }

  /**
   * Returns the next decoded video frame, or null once the stream is drained.
   * When {@code convert} is false the image data is decoded but dropped (the
   * audio-buffering pass doesn't need it), avoiding the RGB conversion cost.
   */
  private Frame nextVideoFrame(final boolean convert) throws Exception {
    if(this.videoDecoder == null || this.videoOutputDone) {
      return null;
    }

    while(true) {
      this.pumpInput(this.videoDecoder, this.videoExtractor, this.videoInputDone);

      final int index = this.videoDecoder.dequeueOutputBuffer(this.videoInfo, TIMEOUT_US);
      if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        continue;
      }

      if(index == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if(this.videoInputDone) {
          this.videoOutputDone = true;
          return null;
        }
        continue;
      }

      if(index < 0) {
        continue;
      }

      if((this.videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        this.videoDecoder.releaseOutputBuffer(index, false);
        this.videoOutputDone = true;
        if(this.videoInfo.size <= 0) {
          return null;
        }
      }

      final Image image = this.videoDecoder.getOutputImage(index);
      try {
        if(image == null) {
          // Codec delivered a non-image (opaque) buffer; skip it.
          if(this.videoOutputDone) {
            return null;
          }
          continue;
        }

        final Frame frame = new Frame();
        frame.timestamp = this.videoInfo.presentationTimeUs;

        if(convert) {
          this.toRgb24(image);
          frame.image = new java.nio.Buffer[] { this.rgbBuffer };
        }

        return frame;
      } finally {
        this.videoDecoder.releaseOutputBuffer(index, false);
      }
    }
  }

  /** YUV_420_888 -> tightly packed RGB24 via the native helper. */
  private void toRgb24(final Image image) {
    final int width = image.getWidth();
    final int height = image.getHeight();

    if(this.rgbBuffer == null || this.rgbBuffer.capacity() < width * height * 3) {
      this.rgbBuffer = ByteBuffer.allocateDirect(width * height * 3);
    }

    final Image.Plane[] planes = image.getPlanes();
    yuv2rgb(
      planes[0].getBuffer(), planes[0].getRowStride(), planes[0].getPixelStride(),
      planes[1].getBuffer(), planes[1].getRowStride(), planes[1].getPixelStride(),
      planes[2].getBuffer(), planes[2].getRowStride(), planes[2].getPixelStride(),
      this.rgbBuffer, width, height);
  }

  /**
   * Grabs the next decoded frame. Audio is drained first (matching javacv's
   * packet-order behaviour closely enough for upstream's audio-buffering
   * pass), then video frames. Returns null at end of stream.
   */
  public Frame grabFrame() throws Exception {
    final Frame audio = this.nextAudioFrame();
    if(audio != null) {
      return audio;
    }
    return this.nextVideoFrame(false);
  }

  /** Grabs the next decoded video frame only. */
  public Frame grabImage() throws Exception {
    return this.nextVideoFrame(true);
  }

  public void setFrameNumber(final int frameNumber) throws Exception {
    // Upstream only seeks back to the start; seek to the closest sync point
    // before the target timestamp either way.
    this.videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    this.videoDecoder.flush();
    this.videoInputDone = false;
    this.videoOutputDone = false;

    if(this.audioDecoder != null) {
      this.audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
      this.audioDecoder.flush();
      this.audioInputDone = false;
      this.audioOutputDone = false;
    }
  }

  public void stop() throws Exception {
    if(this.videoDecoder != null) {
      try {
        this.videoDecoder.stop();
      } catch(final IllegalStateException ignored) {
      }
    }

    if(this.audioDecoder != null) {
      try {
        this.audioDecoder.stop();
      } catch(final IllegalStateException ignored) {
      }
    }
  }

  public void release() {
    if(this.videoDecoder != null) {
      this.videoDecoder.release();
      this.videoDecoder = null;
    }

    if(this.audioDecoder != null) {
      this.audioDecoder.release();
      this.audioDecoder = null;
    }

    if(this.videoExtractor != null) {
      this.videoExtractor.release();
      this.videoExtractor = null;
    }

    if(this.audioExtractor != null) {
      this.audioExtractor.release();
      this.audioExtractor = null;
    }
  }
}
