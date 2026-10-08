package org.lwjgl.openal;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAL 1.0 implemented on top of android.media.AudioTrack.
 *
 * Each source owns one AudioTrack (MODE_STREAM) plus a writer thread that
 * performs blocking writes of queued buffers in order. A buffer counts as
 * "processed" once the track has accepted it; the blocking write() keeps the
 * producer paced, so effective buffering is the upstream ring plus the track
 * buffer.
 */
public final class AL10 {
  private static final String TAG = "SC-AL";

  public static final int AL_NONE = 0;
  public static final int AL_FORMAT_MONO8 = 0x1100;
  public static final int AL_FORMAT_MONO16 = 0x1101;
  public static final int AL_FORMAT_STEREO8 = 0x1102;
  public static final int AL_FORMAT_STEREO16 = 0x1103;
  public static final int AL_FORMAT_STEREO_FLOAT32 = 0x10011;
  public static final int AL_SOURCE_STATE = 0x1010;
  public static final int AL_INITIAL = 0x1011;
  public static final int AL_PLAYING = 0x1012;
  public static final int AL_STOPPED = 0x1014;
  public static final int AL_BUFFERS_PROCESSED = 0x1016;

  private AL10() { }

  private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
  private static final Map<Integer, AlSource> sources = new HashMap<>();
  private static final Map<Integer, AlBuffer> buffers = new HashMap<>();

  /** All live AudioTracks, for process-wide pause/resume. */
  private static final Map<AudioTrack, AlSource> tracks = new WeakHashMap<>();

  private static final class AlBuffer {
    final int id;
    int format;
    int rate;
    byte[] data;
    /** frames per buffer = bytes / bytesPerFrame */
    int frames;

    AlBuffer(final int id) {
      this.id = id;
    }
  }

  private static final class AlSource implements Runnable {
    final int id;

    /** Buffers queued by the app, not yet fully written to the track. */
    private final ArrayDeque<AlBuffer> queue = new ArrayDeque<>();
    /** Buffers fully written; entry order, each with its end-of-buffer frame. */
    private final ArrayDeque<Completed> completed = new ArrayDeque<>();

    private AudioTrack track;
    private int trackFormat;
    private int trackRate;
    private int bytesPerFrame = 1;

    /** Cumulative frames handed to the track (sum of completed buffers). */
    private long writtenFrames;
    /** endFrame of the last buffer popped by alSourceUnqueueBuffers. */
    private long unqueuedFrames;
    private boolean playing;
    private boolean destroyed;
    private Thread writer;
    private long lastUnderrunLog;
    private int lastLoggedUnderruns;
    /** Bumped whenever the stream is reset; the writer drops stale writes. */
    private long trackGeneration;

    private static final class Completed {
      final int id;
      final int frames;

      Completed(final int id, final int frames) {
        this.id = id;
        this.frames = frames;
      }
    }

    AlSource(final int id) {
      this.id = id;
    }

    synchronized void queueBuffer(final AlBuffer buffer) {
      this.ensureTrack(buffer.format, buffer.rate);
      this.queue.add(buffer);
      this.notifyAll();
    }

    private void ensureTrack(final int format, final int rate) {
      if(this.track != null && this.trackFormat == format && this.trackRate == rate) {
        return;
      }

      this.resetStream();

      this.trackFormat = format;
      this.trackRate = rate;

      final int encoding;
      final int channels;
      switch(format) {
        case AL_FORMAT_MONO8 -> {
          encoding = AudioFormat.ENCODING_PCM_8BIT;
          channels = 1;
          this.bytesPerFrame = 1;
        }
        case AL_FORMAT_STEREO8 -> {
          encoding = AudioFormat.ENCODING_PCM_8BIT;
          channels = 2;
          this.bytesPerFrame = 2;
        }
        case AL_FORMAT_MONO16 -> {
          encoding = AudioFormat.ENCODING_PCM_16BIT;
          channels = 1;
          this.bytesPerFrame = 2;
        }
        case AL_FORMAT_STEREO16 -> {
          encoding = AudioFormat.ENCODING_PCM_16BIT;
          channels = 2;
          this.bytesPerFrame = 4;
        }
        case AL_FORMAT_STEREO_FLOAT32 -> {
          // Float tracks misbehave on this device (server removes the track
          // and getPlaybackHeadPosition never advances), so float buffers are
          // converted to 16-bit PCM on upload and played back as stereo16.
          encoding = AudioFormat.ENCODING_PCM_16BIT;
          channels = 2;
          this.bytesPerFrame = 4;
        }
        default -> {
          Log.w(TAG, "Unknown AL format " + format + "; assuming stereo16");
          encoding = AudioFormat.ENCODING_PCM_16BIT;
          channels = 2;
          this.bytesPerFrame = 4;
        }
      }

      final int channelMask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
      final int minSize = AudioTrack.getMinBufferSize(rate, channelMask, encoding);
      final int bufferSize = Math.max(minSize * 2, rate * this.bytesPerFrame * 60 / 1000);

      this.track = new AudioTrack.Builder()
        .setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_GAME)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build())
        .setAudioFormat(new AudioFormat.Builder()
          .setEncoding(encoding)
          .setSampleRate(rate)
          .setChannelMask(channelMask)
          .build())
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build();

      // Start playback once ~10 ms is buffered rather than requiring a full
      // track buffer; otherwise short/quiet sources never leave head 0.
      if(android.os.Build.VERSION.SDK_INT >= 31) {
        final int capacity = bufferSize / this.bytesPerFrame;
        final int threshold = Math.max(1, Math.min(rate / 100, capacity));
        this.track.setStartThresholdInFrames(threshold);
      }

      synchronized(AL10.class) {
        tracks.put(this.track, this);
      }

      // If the source was told to play while it had no track, start now.
      if(this.playing) {
        this.track.play();
      }

      if(this.writer == null) {
        this.writer = new Thread(this, "AL-Source-" + this.id);
        this.writer.setDaemon(true);
        this.writer.start();
      }
    }

    @Override
    public void run() {
      while(true) {
        final AlBuffer buffer;
        final AudioTrack track;
        final long generation;
        synchronized(this) {
          while(!this.destroyed && (this.queue.isEmpty() || this.track == null)) {
            try {
              this.wait();
            } catch(final InterruptedException ignored) { }
          }

          if(this.destroyed) {
            return;
          }

          buffer = this.queue.peek();
          track = this.track;
          generation = this.trackGeneration;
        }

        if(buffer.data.length > 0) {
          try {
            track.write(buffer.data, 0, buffer.data.length);
          } catch(final IllegalStateException e) {
            // Track was released under us; drop the write.
          }
        }

        synchronized(this) {
          // Commit only if the stream wasn't reset mid-write and this buffer
          // is still at the head of the queue.
          if(generation == this.trackGeneration && this.queue.peek() == buffer) {
            this.queue.poll();
            this.writtenFrames += buffer.frames;
            // A buffer counts as processed as soon as the track has accepted
            // it (blocking write() still paces the producer); head-based
            // processing starves the small upstream buffer ring because the
            // playback head advances in coarse mixer bursts.
            this.completed.add(new Completed(buffer.id, buffer.frames));
          }
        }
      }
    }

    synchronized void play() {
      this.playing = true;
      if(this.track != null) {
        try {
          this.track.play();
        } catch(final IllegalStateException e) {
          Log.w(TAG, "AudioTrack.play failed", e);
        }
      }
      this.notifyAll();
    }

    synchronized void stop() {
      this.playing = false;
      this.resetStream();
    }

    /**
     * Tear the stream down to a clean timeline: the track is released (a fresh
     * one is built lazily on the next queue), every queued buffer is moved to
     * completed (immediately processed), and the generation is bumped so an
     * in-flight writer drops its result.
     */
    private void resetStream() {
      this.trackGeneration++;

      AlBuffer buffer;
      while((buffer = this.queue.poll()) != null) {
        this.completed.add(new Completed(buffer.id, buffer.frames));
      }

      this.releaseTrack();
      this.writtenFrames = 0;
      this.unqueuedFrames = 0;
      this.notifyAll();
    }

    synchronized void destroy() {
      this.destroyed = true;
      this.playing = false;
      this.trackGeneration++;
      this.releaseTrack();
      this.notifyAll();
    }

    private void releaseTrack() {
      if(this.track != null) {
        synchronized(AL10.class) {
          tracks.remove(this.track);
        }
        this.track.release();
        this.track = null;
      }
    }

    private long headPosition() {
      return this.track != null ? Integer.toUnsignedLong(this.track.getPlaybackHeadPosition()) : this.writtenFrames;
    }

    synchronized int getState() {
      if(this.playing) {
        // Still playing while unplayed audio remains
        if(!this.queue.isEmpty() || this.headPosition() < this.writtenFrames) {
          return AL_PLAYING;
        }
      }

      return this.queue.isEmpty() && this.completed.isEmpty() ? AL_INITIAL : AL_STOPPED;
    }

    synchronized int getProcessedCount() {
      final long now = System.currentTimeMillis();
      if(this.track != null && this.playing && now - this.lastUnderrunLog >= 5000) {
        this.lastUnderrunLog = now;
        final int underruns = this.track.getUnderrunCount();
        final String msg = "src " + this.id + " rate " + this.trackRate + " underruns " + underruns
          + " state " + this.track.getPlayState() + " head " + this.headPosition() + '/' + this.writtenFrames;
        if(underruns > this.lastLoggedUnderruns) {
          this.lastLoggedUnderruns = underruns;
          Log.d(TAG, msg);
        } else {
          Log.v(TAG, msg);
        }
        // Watchdog: recover a track left paused by a pause/resume cycle that
        // silently failed. play() is a no-op on a track that is already
        // playing, so only call it when the play state says otherwise.
        if(this.track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
          try {
            this.track.play();
          } catch(final IllegalStateException ignored) { }
        }
      }

      return this.completed.size();
    }

    synchronized int unqueueBuffer() {
      final Completed c = this.completed.poll();
      if(c == null) {
        return 0;
      }

      this.unqueuedFrames += c.frames;
      return c.id;
    }

    synchronized float getSecOffset() {
      final long played = Math.max(0, this.headPosition() - this.unqueuedFrames);
      final int rate = this.trackRate > 0 ? this.trackRate : 1;
      return Math.max(0.0f, (float)played / rate);
    }
  }

  // ------------------------------------------------------------------
  // ALC-independent pause/resume for the whole process
  // ------------------------------------------------------------------

  public static void pauseAll() {
    final List<AudioTrack> snapshot;
    synchronized(AL10.class) {
      snapshot = new ArrayList<>(tracks.keySet());
    }

    for(final AudioTrack track : snapshot) {
      try {
        track.pause();
      } catch(final IllegalStateException e) {
        Log.w(TAG, "pauseAll failed", e);
      }
    }
    Log.d(TAG, "pauseAll: paused " + snapshot.size() + " tracks");
  }

  public static void resumeAll() {
    final List<Map.Entry<AudioTrack, AlSource>> snapshot;
    synchronized(AL10.class) {
      snapshot = new ArrayList<>(tracks.entrySet());
    }

    int resumed = 0;
    for(final Map.Entry<AudioTrack, AlSource> entry : snapshot) {
      synchronized(entry.getValue()) {
        if(entry.getValue().playing) {
          try {
            entry.getKey().play();
            resumed++;
          } catch(final IllegalStateException e) {
            Log.w(TAG, "resumeAll play failed for src " + entry.getValue().id, e);
          }
        }
      }
    }
    Log.d(TAG, "resumeAll: resumed " + resumed + " of " + snapshot.size() + " tracks");
  }

  // ------------------------------------------------------------------
  // AL entry points
  // ------------------------------------------------------------------

  public static int alGenSources() {
    final AlSource source = new AlSource(NEXT_ID.getAndIncrement());
    synchronized(AL10.class) {
      sources.put(source.id, source);
    }
    return source.id;
  }

  public static void alGenSources(final IntBuffer out) {
    final int count = out.remaining();
    for(int i = 0; i < count; i++) {
      out.put(alGenSources());
    }
  }

  public static void alDeleteSources(final int source) {
    final AlSource s;
    synchronized(AL10.class) {
      s = sources.remove(source);
    }
    if(s != null) {
      s.destroy();
    }
  }

  public static void alGenBuffers(final int[] out) {
    for(int i = 0; i < out.length; i++) {
      out[i] = alGenBuffer();
    }
  }

  private static int alGenBuffer() {
    final AlBuffer buffer = new AlBuffer(NEXT_ID.getAndIncrement());
    synchronized(AL10.class) {
      buffers.put(buffer.id, buffer);
    }
    return buffer.id;
  }

  public static void alDeleteBuffers(final int buffer) {
    synchronized(AL10.class) {
      buffers.remove(buffer);
    }
  }

  public static void alDeleteBuffers(final int[] buffers) {
    for(final int buffer : buffers) {
      alDeleteBuffers(buffer);
    }
  }

  public static void alBufferData(final int bufferId, final int format, final ByteBuffer data, final int sampleRate) {
    final byte[] copy = new byte[data.remaining()];
    data.get(copy);
    setBufferData(bufferId, format, copy, sampleRate);
  }

  public static void alBufferData(final int bufferId, final int format, final short[] data, final int sampleRate) {
    final ByteBuffer bb = ByteBuffer.allocate(data.length * 2).order(ByteOrder.LITTLE_ENDIAN);
    bb.asShortBuffer().put(data);
    setBufferData(bufferId, format, bb.array(), sampleRate);
  }

  public static void alBufferData(final int bufferId, final int format, final float[] data, final int sampleRate) {
    // See AL_FORMAT_STEREO_FLOAT32 in ensureTrack: floats are converted to
    // little-endian 16-bit PCM.
    final ByteBuffer bb = ByteBuffer.allocate(data.length * 2).order(ByteOrder.LITTLE_ENDIAN);
    for(final float sample : data) {
      final float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
      bb.putShort((short)(clamped * Short.MAX_VALUE));
    }
    setBufferData(bufferId, format, bb.array(), sampleRate);
  }

  private static void setBufferData(final int bufferId, final int format, final byte[] data, final int sampleRate) {
    final AlBuffer buffer;
    synchronized(AL10.class) {
      buffer = buffers.get(bufferId);
    }

    if(buffer == null) {
      return;
    }

    buffer.format = format;
    buffer.rate = sampleRate;
    buffer.data = data;
    buffer.frames = data.length / bytesPerFrame(format);
  }

  private static int bytesPerFrame(final int format) {
    return switch(format) {
      case AL_FORMAT_MONO8 -> 1;
      case AL_FORMAT_STEREO8, AL_FORMAT_MONO16 -> 2;
      case AL_FORMAT_STEREO16, AL_FORMAT_STEREO_FLOAT32 -> 4;
      default -> 4;
    };
  }

  public static void alSourceQueueBuffers(final int sourceId, final int bufferId) {
    final AlSource source;
    final AlBuffer buffer;
    synchronized(AL10.class) {
      source = sources.get(sourceId);
      buffer = buffers.get(bufferId);
    }

    if(source != null && buffer != null) {
      source.queueBuffer(buffer);
    }
  }

  public static int alSourceUnqueueBuffers(final int sourceId) {
    final AlSource source = getSource(sourceId);
    return source != null ? source.unqueueBuffer() : 0;
  }

  public static void alSourcePlay(final int sourceId) {
    final AlSource source = getSource(sourceId);
    if(source != null) {
      source.play();
    }
  }

  public static void alSourceStop(final int sourceId) {
    final AlSource source = getSource(sourceId);
    if(source != null) {
      source.stop();
    }
  }

  public static int alGetSourcei(final int sourceId, final int param) {
    final IntBuffer out = IntBuffer.allocate(1);
    alGetSourcei(sourceId, param, out);
    return out.get(0);
  }

  public static void alGetSourcei(final int sourceId, final int param, final IntBuffer out) {
    final AlSource source = getSource(sourceId);
    final int value;
    if(source == null) {
      value = 0;
    } else {
      value = switch(param) {
        case AL_SOURCE_STATE -> source.getState();
        case AL_BUFFERS_PROCESSED -> source.getProcessedCount();
        default -> 0;
      };
    }
    out.put(0, value);
  }

  public static float alGetSourcef(final int sourceId, final int param) {
    final AlSource source = getSource(sourceId);
    if(source == null) {
      return 0.0f;
    }

    if(param == AL11.AL_SEC_OFFSET) {
      return source.getSecOffset();
    }

    return 0.0f;
  }

  private static AlSource getSource(final int sourceId) {
    synchronized(AL10.class) {
      return sources.get(sourceId);
    }
  }
}
