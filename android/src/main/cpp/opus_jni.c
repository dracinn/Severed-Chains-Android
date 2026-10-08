/*
 * JNI bridge for the org.lwjgl.util.opus.Opus / OpusFile Android shims.
 * Implements the LWJGL surface used by upstream on top of libopus/opusfile,
 * including LWJGL's position/remaining buffer semantics.
 */
#include <jni.h>
#include <string.h>
#include <opus.h>
#include <opus_multistream.h>
#include <opusfile.h>

static jmethodID MID_Buffer_position;
static jmethodID MID_Buffer_limit;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }
  const jclass buffer = (*env)->FindClass(env, "java/nio/Buffer");
  MID_Buffer_position = (*env)->GetMethodID(env, buffer, "position", "()I");
  MID_Buffer_limit = (*env)->GetMethodID(env, buffer, "limit", "()I");
  return JNI_VERSION_1_6;
}

/* Address of a direct buffer's data at its current position. */
static void *ptrAtPosition(JNIEnv *env, jobject buf, jint elemSize) {
  if(buf == NULL) {
    return NULL;
  }
  char *addr = (char *)(*env)->GetDirectBufferAddress(env, buf);
  if(addr == NULL) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                     "buffer is not direct");
    return NULL;
  }
  const jint position = (*env)->CallIntMethod(env, buf, MID_Buffer_position);
  return addr + (size_t)position * elemSize;
}

static jint remaining(JNIEnv *env, jobject buf) {
  const jint position = (*env)->CallIntMethod(env, buf, MID_Buffer_position);
  const jint limit = (*env)->CallIntMethod(env, buf, MID_Buffer_limit);
  return limit - position;
}

static void putError(JNIEnv *env, jobject error, jint value) {
  if(error == NULL) {
    return;
  }
  jint *p = (jint *)ptrAtPosition(env, error, sizeof(jint));
  if(p != NULL) {
    *p = value;
  }
}

/* ------------------------------------------------------------------ */
/* org.lwjgl.util.opus.Opus                                            */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_org_lwjgl_util_opus_Opus_nopus_1encoder_1create(
    JNIEnv *env, jclass clazz, jint Fs, jint channels, jint application,
    jobject error) {
  int err = OPUS_OK;
  OpusEncoder *enc = opus_encoder_create(Fs, channels, application, &err);
  putError(env, error, err);
  return (jlong)(uintptr_t)enc;
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_util_opus_Opus_nopus_1encoder_1ctl(
    JNIEnv *env, jclass clazz, jlong st, jint request) {
  return opus_encoder_ctl((OpusEncoder *)(uintptr_t)st, request);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_util_opus_Opus_nopus_1encoder_1ctl_1i(
    JNIEnv *env, jclass clazz, jlong st, jint request, jint value) {
  return opus_encoder_ctl((OpusEncoder *)(uintptr_t)st, request, (opus_int32)value);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_util_opus_Opus_nopus_1encode(
    JNIEnv *env, jclass clazz, jlong st, jobject pcm, jint frameSize,
    jobject data) {
  opus_int16 *pcmPtr = (opus_int16 *)ptrAtPosition(env, pcm, sizeof(jshort));
  unsigned char *dataPtr = (unsigned char *)ptrAtPosition(env, data, sizeof(jbyte));
  if(pcmPtr == NULL || dataPtr == NULL) {
    return OPUS_BAD_ARG;
  }
  const jint dataCapacity = remaining(env, data);
  return opus_encode((OpusEncoder *)(uintptr_t)st, pcmPtr, frameSize, dataPtr,
                     dataCapacity);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_util_opus_Opus_nopus_1encode_1float(
    JNIEnv *env, jclass clazz, jlong st, jobject pcm, jint frameSize,
    jobject data) {
  float *pcmPtr = (float *)ptrAtPosition(env, pcm, sizeof(jfloat));
  unsigned char *dataPtr = (unsigned char *)ptrAtPosition(env, data, sizeof(jbyte));
  if(pcmPtr == NULL || dataPtr == NULL) {
    return OPUS_BAD_ARG;
  }
  const jint dataCapacity = remaining(env, data);
  return opus_encode_float((OpusEncoder *)(uintptr_t)st, pcmPtr, frameSize,
                           dataPtr, dataCapacity);
}

JNIEXPORT void JNICALL
Java_org_lwjgl_util_opus_Opus_opus_1encoder_1destroy(
    JNIEnv *env, jclass clazz, jlong st) {
  opus_encoder_destroy((OpusEncoder *)(uintptr_t)st);
}

/* ------------------------------------------------------------------ */
/* org.lwjgl.util.opus.OpusFile                                        */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_org_lwjgl_util_opus_OpusFile_nop_1open_1memory(
    JNIEnv *env, jclass clazz, jobject data, jobject error) {
  const unsigned char *dataPtr = (const unsigned char *)ptrAtPosition(env, data, 1);
  if(dataPtr == NULL) {
    putError(env, error, OP_EFAULT);
    return 0;
  }
  const size_t size = (size_t)remaining(env, data);
  int err = 0;
  OggOpusFile *of = op_open_memory(dataPtr, size, &err);
  putError(env, error, err);
  return (jlong)(uintptr_t)of;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_util_opus_OpusFile_op_1free(
    JNIEnv *env, jclass clazz, jlong of) {
  op_free((OggOpusFile *)(uintptr_t)of);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_util_opus_OpusFile_op_1channel_1count(
    JNIEnv *env, jclass clazz, jlong of, jint li) {
  return op_channel_count((OggOpusFile *)(uintptr_t)of, li);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_util_opus_OpusFile_op_1pcm_1total(
    JNIEnv *env, jclass clazz, jlong of, jint li) {
  return (jlong)op_pcm_total((OggOpusFile *)(uintptr_t)of, li);
}

JNIEXPORT jint JNICALL
Java_org_lwjgl_util_opus_OpusFile_nop_1read(
    JNIEnv *env, jclass clazz, jlong of, jobject pcm, jobject li) {
  opus_int16 *pcmPtr = (opus_int16 *)ptrAtPosition(env, pcm, sizeof(jshort));
  if(pcmPtr == NULL) {
    return OP_EFAULT;
  }
  const jint bufSize = remaining(env, pcm);
  jint liValue = 0;
  const int ret = op_read((OggOpusFile *)(uintptr_t)of, pcmPtr, bufSize,
                          li != NULL ? &liValue : NULL);
  if(li != NULL) {
    jint *liPtr = (jint *)ptrAtPosition(env, li, sizeof(jint));
    if(liPtr != NULL) {
      *liPtr = liValue;
    }
  }
  return ret;
}
