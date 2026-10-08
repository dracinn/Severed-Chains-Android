// KHR_debug bridge: android.opengl.GLES32.glDebugMessageCallback is an
// unimplemented framework stub on many devices, so register the callback
// directly through eglGetProcAddress.
#include <jni.h>
#include <string.h>
#include <GLES3/gl32.h>
#include <EGL/egl.h>
#include <android/log.h>

#define TAG "SC-GLDBG"

static JavaVM* gJvm;
static jclass gCls;
static jmethodID gOnMessage;

static void GL_APIENTRY onGlDebugMessage(const GLenum source, const GLenum type, const GLuint id, const GLenum severity, const GLsizei length, const GLchar* message, const void* userParam) {
  (void)length;
  (void)userParam;

  JNIEnv* env;
  if((*gJvm)->GetEnv(gJvm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
    return;
  }

  jstring str = (*env)->NewStringUTF(env, message != NULL ? message : "");
  (*env)->CallStaticVoidMethod(env, gCls, gOnMessage, (jint)source, (jint)type, (jint)id, (jint)severity, str);
  (*env)->DeleteLocalRef(env, str);
  if((*env)->ExceptionCheck(env)) {
    (*env)->ExceptionClear(env);
  }
}

JNIEXPORT jboolean JNICALL Java_legend_game_android_DebugGl_install(JNIEnv* env, const jclass cls) {
  (*env)->GetJavaVM(env, &gJvm);
  if(gCls != NULL) {
    (*env)->DeleteGlobalRef(env, gCls);
  }
  gCls = (*env)->NewGlobalRef(env, cls);
  gOnMessage = (*env)->GetStaticMethodID(env, cls, "onMessage", "(IIIILjava/lang/String;)V");
  if(gOnMessage == NULL) {
    return JNI_FALSE;
  }

  typedef void (GL_APIENTRY *DebugCallbackFn)(GLDEBUGPROC callback, const void* userParam);
  DebugCallbackFn pfn = (DebugCallbackFn)eglGetProcAddress("glDebugMessageCallbackKHR");
  if(pfn == NULL) {
    pfn = (DebugCallbackFn)eglGetProcAddress("glDebugMessageCallback");
  }
  if(pfn == NULL) {
    __android_log_write(ANDROID_LOG_WARN, TAG, "no glDebugMessageCallback entry point");
    return JNI_FALSE;
  }

  pfn(onGlDebugMessage, NULL);

  // Synchronous delivery keeps the callback on the GL thread, where the JVM
  // is already attached.
  glEnable(GL_DEBUG_OUTPUT);
  glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
  return JNI_TRUE;
}
