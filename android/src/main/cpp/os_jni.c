// Process-level OS bridge: chdir so upstream's relative paths resolve
// against the app's files dir. Also installs a native crash handler that
// records a marker file on fatal signals; the next launch picks it up and
// turns it into a shared crash log (Java's uncaught-exception handler can't
// see native crashes).
#include <jni.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <signal.h>
#include <fcntl.h>
#include <stdlib.h>

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

// ---- native crash marker ----
// On a fatal signal we write "sig=<n> addr=<ptr>\n" to the marker path using
// only async-signal-safe calls, then re-raise the default handler so the
// system still produces its tombstone. Java picks the marker up next launch.

static char crash_marker_path[4096];
static struct sigaction prev_handlers[6];
static const int handled_sigs[] = {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP};

static void write_int(const int fd, const unsigned long v) {
  char buf[24];
  int i = 23;
  buf[i] = '\0';
  unsigned long n = v;
  if(n == 0) {
    buf[--i] = '0';
  }
  while(n > 0 && i > 0) {
    const int d = n % 16;
    buf[--i] = d < 10 ? '0' + d : 'a' + d - 10;
  }
  write(fd, buf + i, 23 - i);
}

static void crash_handler(const int sig, siginfo_t* const info, void* const ctx) {
  (void)ctx;
  if(crash_marker_path[0] != '\0') {
    const int fd = open(crash_marker_path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if(fd >= 0) {
      write(fd, "sig=", 4);
      write_int(fd, (unsigned long)sig);
      write(fd, " addr=0x", 8);
      write_int(fd, (unsigned long)(info != NULL ? info->si_addr : NULL));
      write(fd, "\n", 1);
      close(fd);
    }
  }

  for(unsigned int i = 0; i < sizeof(handled_sigs) / sizeof(handled_sigs[0]); i++) {
    if(handled_sigs[i] == sig) {
      sigaction(sig, &prev_handlers[i], NULL);
      break;
    }
  }
  raise(sig);
}

JNIEXPORT void JNICALL Java_legend_game_android_NativeOs_installCrashHandler(JNIEnv* env, const jclass cls, const jstring markerPath) {
  (void)cls;

  const char* cpath = (*env)->GetStringUTFChars(env, markerPath, NULL);
  if(cpath == NULL) {
    return;
  }
  strncpy(crash_marker_path, cpath, sizeof(crash_marker_path) - 1);
  (*env)->ReleaseStringUTFChars(env, markerPath, cpath);

  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_sigaction = crash_handler;
  sa.sa_flags = SA_SIGINFO | SA_RESETHAND;
  sigemptyset(&sa.sa_mask);

  for(unsigned int i = 0; i < sizeof(handled_sigs) / sizeof(handled_sigs[0]); i++) {
    sigaction(handled_sigs[i], &sa, &prev_handlers[i]);
  }
}
