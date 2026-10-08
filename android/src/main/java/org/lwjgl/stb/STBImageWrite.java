package org.lwjgl.stb;

import android.graphics.Bitmap;

import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** STBImageWrite replacement backed by Bitmap PNG encoding. */
public final class STBImageWrite {
  private STBImageWrite() { }

  public static boolean stbi_write_png_to_func(final STBIWriteCallback func, final long context, final int w, final int h, final int comp, final ByteBuffer data, final int stride_in_bytes) {
    final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

    final int[] argb = new int[w * h];
    for(int y = 0; y < h; y++) {
      for(int x = 0; x < w; x++) {
        final int offset = y * stride_in_bytes + x * comp;
        final int r = data.get(offset) & 0xff;
        final int g = comp > 1 ? data.get(offset + 1) & 0xff : r;
        final int b = comp > 2 ? data.get(offset + 2) & 0xff : r;
        final int a = comp > 3 ? data.get(offset + 3) & 0xff : 0xff;
        argb[y * w + x] = a << 24 | r << 16 | g << 8 | b;
      }
    }
    bitmap.setPixels(argb, 0, w, 0, 0, w, h);

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    bitmap.recycle();

    final byte[] png = out.toByteArray();
    final ByteBuffer chunk = MemoryUtil.memAlloc(png.length);
    chunk.put(png).flip();
    func.invoke(context, MemoryUtil.memAddress(chunk), png.length);

    return true;
  }
}
