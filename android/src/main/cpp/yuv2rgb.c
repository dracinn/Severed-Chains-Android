/*
 * YUV_420_888 -> RGB24 conversion for the javacv FFmpegFrameGrabber shim.
 * A Java-side per-pixel loop over direct buffers is far too slow for 720p;
 * this does the same limited-range BT.601 fixed-point conversion in C.
 */
#include <jni.h>
#include <stdint.h>

static uint8_t *directBuf(JNIEnv *env, jobject buf) {
  return (uint8_t *)(*env)->GetDirectBufferAddress(env, buf);
}

static inline uint8_t clamp255(const int v) {
  return v < 0 ? 0 : v > 255 ? 255 : (uint8_t)v;
}

JNIEXPORT void JNICALL
Java_org_bytedeco_javacv_FFmpegFrameGrabber_yuv2rgb(
    JNIEnv *env, jclass clazz,
    jobject yBuf, jint yRow, jint yPix,
    jobject uBuf, jint uRow, jint uPix,
    jobject vBuf, jint vRow, jint vPix,
    jobject out, jint width, jint height) {
  const uint8_t *y = directBuf(env, yBuf);
  const uint8_t *u = directBuf(env, uBuf);
  const uint8_t *v = directBuf(env, vBuf);
  uint8_t *dst = directBuf(env, out);
  if(y == NULL || u == NULL || v == NULL || dst == NULL) {
    return;
  }

  for(jint row = 0; row < height; row++) {
    const uint8_t *yLine = y + (size_t)row * yRow;
    const uint8_t *uLine = u + (size_t)(row >> 1) * uRow;
    const uint8_t *vLine = v + (size_t)(row >> 1) * vRow;
    uint8_t *out = dst + (size_t)row * width * 3;

    for(jint x = 0; x < width; x += 2) {
      const int d = (int)uLine[(size_t)(x >> 1) * uPix] - 128;
      const int e = (int)vLine[(size_t)(x >> 1) * vPix] - 128;
      const int rAdd = 409 * e + 128;
      const int gAdd = -100 * d - 208 * e + 128;
      const int bAdd = 516 * d + 128;

      int c = (int)yLine[(size_t)x * yPix] - 16;
      if(c < 0) {
        c = 0;
      }
      const int lum0 = 298 * c;
      out[0] = clamp255((lum0 + rAdd) >> 8);
      out[1] = clamp255((lum0 + gAdd) >> 8);
      out[2] = clamp255((lum0 + bAdd) >> 8);

      if(x + 1 < width) {
        c = (int)yLine[(size_t)(x + 1) * yPix] - 16;
        if(c < 0) {
          c = 0;
        }
        const int lum1 = 298 * c;
        out[3] = clamp255((lum1 + rAdd) >> 8);
        out[4] = clamp255((lum1 + gAdd) >> 8);
        out[5] = clamp255((lum1 + bAdd) >> 8);
      }

      out += 6;
    }
  }
}
