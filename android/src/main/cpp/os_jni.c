// Process-level OS bridge: chdir so upstream's relative paths resolve
// against the app's files dir.
#include <jni.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>

JNIEXPORT void JNICALL Java_legend_game_android_NativeOs_chdir(JNIEnv* env, const jclass cls, const jstring path) {
  (void)cls;

  const char* cpath = (*env)->GetStringUTFChars(env, path, NULL);
  if(cpath == NULL) {
    return;
  }

  const int rc = chdir(cpath);
  const int err = errno;
  (*env)->ReleaseStringUTFChars(env, path, cpath);

  if(rc != 0) {
    const jclass ex = (*env)->FindClass(env, "java/io/IOException");
    if(ex != NULL) {
      (*env)->ThrowNew(env, ex, strerror(err));
    }
  }
}
