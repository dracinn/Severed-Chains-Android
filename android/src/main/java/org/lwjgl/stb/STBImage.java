package org.lwjgl.stb;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** STBImage replacement backed by BitmapFactory. Returns RGBA byte buffers. */
public final class STBImage {
  public static final int STBI_default = 0;
  public static final int STBI_grey = 1;
  public static final int STBI_grey_alpha = 2;
  public static final int STBI_rgb = 3;
  public static final int STBI_rgb_alpha = 4;

  private static String failureReason = "";

  private STBImage() { }

  public static ByteBuffer stbi_load_from_memory(final ByteBuffer imageBuffer, final IntBuffer x, final IntBuffer y, final IntBuffer channels_in_file, final int desired_channels) {
    final byte[] encoded = new byte[imageBuffer.remaining()];
    imageBuffer.get(encoded);

    final Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
    if(bitmap == null) {
      failureReason = "BitmapFactory failed to decode image";
      return null;
    }

    final int w = bitmap.getWidth();
    final int h = bitmap.getHeight();
    x.put(0, w);
    y.put(0, h);
    channels_in_file.put(0, 4);

    final int[] argb = new int[w * h];
    bitmap.getPixels(argb, 0, w, 0, 0, w, h);
    bitmap.recycle();

    final ByteBuffer out = BufferUtils.createByteBuffer(w * h * 4);
    for(final int px : argb) {
      out.put((byte)(px >>> 16 & 0xff)); // R
      out.put((byte)(px >>> 8 & 0xff));  // G
      out.put((byte)(px & 0xff));        // B
      out.put((byte)(px >>> 24));        // A
    }
    out.flip();

    return out;
  }

  public static String stbi_failure_reason() {
    return failureReason;
  }
}
