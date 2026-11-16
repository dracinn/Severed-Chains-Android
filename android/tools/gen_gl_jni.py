#!/usr/bin/env python3
"""Generates GlNative.java + gl_jni.cpp — the unified GL/EGL JNI dispatch layer.

Every GLES entry point the engine touches is dispatched through a function
pointer table resolved at init from either the system libGLESv2.so or the
bundled ANGLE libGLESv2.so (GLES-over-Vulkan). This is required because an
ANGLE context cannot serve calls made through android.opengl (system driver).

Spec codes:
  i int | f float | z bool | l long(ptr) | b Buffer(+off) | s String
  ai/af/as/ab = in arrays | Ai = out int array
Ret codes: V void | I int | S const-char* -> jstring
"""

SPECS = [
  # GLES20
  ("glActiveTexture", "V", "glActiveTexture", [("i","texture")]),
  ("glAttachShader", "V", "glAttachShader", [("i","program"),("i","shader")]),
  ("glBindBuffer", "V", "glBindBuffer", [("i","target"),("i","buffer")]),
  ("glBindFramebuffer", "V", "glBindFramebuffer", [("i","target"),("i","fb")]),
  ("glBindTexture", "V", "glBindTexture", [("i","target"),("i","tex")]),
  ("glBindBufferBase", "V", "glBindBufferBase", [("i","t"),("i","idx"),("i","b")]),
  ("glBindVertexArray", "V", "glBindVertexArray", [("i","a")]),
  ("glBlendEquation", "V", "glBlendEquation", [("i","m")]),
  ("glBlendFunc", "V", "glBlendFunc", [("i","s"),("i","d")]),
  ("glBufferData", "V", "glBufferData", [("i","t"),("i","sz"),("b","d"),("i","u")]),
  ("glBufferDataF", "V", "glBufferData", [("i","t"),("i","sz"),("af","d"),("i","u")]),
  ("glBufferDataI", "V", "glBufferData", [("i","t"),("i","sz"),("ai","d"),("i","u")]),
  ("glBufferDataS", "V", "glBufferData", [("i","t"),("i","sz"),("as","d"),("i","u")]),
  ("glBufferDataB", "V", "glBufferData", [("i","t"),("i","sz"),("ab","d"),("i","u")]),
  ("glBufferSubData", "V", "glBufferSubData", [("i","t"),("i","o"),("i","sz"),("b","d")]),
  ("glBufferSubDataF", "V", "glBufferSubData", [("i","t"),("i","o"),("i","sz"),("af","d")]),
  ("glBufferSubDataI", "V", "glBufferSubData", [("i","t"),("i","o"),("i","sz"),("ai","d")]),
  ("glCheckFramebufferStatus", "I", "glCheckFramebufferStatus", [("i","t")]),
  ("glClear", "V", "glClear", [("i","m")]),
  ("glClearColor", "V", "glClearColor", [("f","r"),("f","g"),("f","b2"),("f","a")]),
  ("glCompileShader", "V", "glCompileShader", [("i","s")]),
  ("glCreateProgram", "I", "glCreateProgram", []),
  ("glCreateShader", "I", "glCreateShader", [("i","t")]),
  ("glDeleteBuffers", "V", "glDeleteBuffers", [("i","n"),("b","d")]),
  ("glDeleteBuffersA", "V", "glDeleteBuffers", [("i","n"),("ai","d")]),
  ("glDeleteFramebuffersA", "V", "glDeleteFramebuffers", [("i","n"),("ai","d")]),
  ("glDeleteProgram", "V", "glDeleteProgram", [("i","p")]),
  ("glDeleteShader", "V", "glDeleteShader", [("i","s")]),
  ("glDeleteTextures", "V", "glDeleteTextures", [("i","n"),("b","d")]),
  ("glDeleteTexturesA", "V", "glDeleteTextures", [("i","n"),("ai","d")]),
  ("glDeleteVertexArraysA", "V", "glDeleteVertexArrays", [("i","n"),("ai","d")]),
  ("glDeleteRenderbuffersA", "V", "glDeleteRenderbuffers", [("i","n"),("ai","d")]),
  ("glDepthFunc", "V", "glDepthFunc", [("i","f")]),
  ("glDepthMask", "V", "glDepthMask", [("z","f")]),
  ("glDisable", "V", "glDisable", [("i","c")]),
  ("glDrawArrays", "V", "glDrawArrays", [("i","m"),("i","f"),("i","c")]),
  ("glDrawElements", "V", "glDrawElements", [("i","m"),("i","c"),("i","t"),("l","p")]),
  ("glDrawElementsB", "V", "glDrawElements", [("i","m"),("i","c"),("i","t"),("b","d")]),
  ("glDrawRangeElements", "V", "glDrawRangeElements", [("i","m"),("i","s"),("i","e"),("i","c"),("i","t"),("l","p")]),
  ("glDrawRangeElementsB", "V", "glDrawRangeElements", [("i","m"),("i","s"),("i","e"),("i","c"),("i","t"),("b","d")]),
  ("glEnable", "V", "glEnable", [("i","c")]),
  ("glEnableVertexAttribArray", "V", "glEnableVertexAttribArray", [("i","i")]),
  ("glDisableVertexAttribArray", "V", "glDisableVertexAttribArray", [("i","i")]),
  ("glCopyTexSubImage2D", "V", "glCopyTexSubImage2D", [("i","t"),("i","l"),("i","xo"),("i","yo"),("i","x"),("i","y"),("i","w"),("i","h")]),
  ("glFramebufferTexture2D", "V", "glFramebufferTexture2D", [("i","t"),("i","a"),("i","tt"),("i","tx"),("i","l")]),
  ("glFramebufferRenderbuffer", "V", "glFramebufferRenderbuffer", [("i","t"),("i","a"),("i","rt"),("i","rb")]),
  ("glFramebufferTexture", "V", "glFramebufferTexture", [("i","t"),("i","a"),("i","tx"),("i","l")]),
  ("glGenBuffers", "V", "glGenBuffers", [("i","n"),("b","d")]),
  ("glGenBuffersA", "V", "glGenBuffers", [("i","n"),("Ai","d")]),
  ("glGenFramebuffers", "V", "glGenFramebuffers", [("i","n"),("b","d")]),
  ("glGenFramebuffersA", "V", "glGenFramebuffers", [("i","n"),("Ai","d")]),
  ("glGenTextures", "V", "glGenTextures", [("i","n"),("b","d")]),
  ("glGenTexturesA", "V", "glGenTextures", [("i","n"),("Ai","d")]),
  ("glGenVertexArrays", "V", "glGenVertexArrays", [("i","n"),("b","d")]),
  ("glGenVertexArraysA", "V", "glGenVertexArrays", [("i","n"),("Ai","d")]),
  ("glGenRenderbuffersA", "V", "glGenRenderbuffers", [("i","n"),("Ai","d")]),
  ("glBindRenderbuffer", "V", "glBindRenderbuffer", [("i","t"),("i","rb")]),
  ("glRenderbufferStorage", "V", "glRenderbufferStorage", [("i","t"),("i","if_"),("i","w"),("i","h")]),
  ("glGetError", "I", "glGetError", []),
  ("glGetIntegerv", "V", "glGetIntegerv", [("i","p"),("b","d")]),
  ("glGetIntegervA", "V", "glGetIntegerv", [("i","p"),("Ai","d")]),
  ("glGetProgramivA", "V", "glGetProgramiv", [("i","p"),("i","pn"),("Ai","d")]),
  ("glGetShaderivA", "V", "glGetShaderiv", [("i","s"),("i","pn"),("Ai","d")]),
  ("glGetFramebufferAttachmentParameterivA", "V", "glGetFramebufferAttachmentParameteriv", [("i","t"),("i","a"),("i","pn"),("Ai","d")]),
  ("glGetProgramInfoLog", "S", "glGetProgramInfoLog", [("i","p")], "INFOLOG", "jint, jint, jint*, GLchar*"),
  ("glGetShaderInfoLog", "S", "glGetShaderInfoLog", [("i","s")], "INFOLOG", "jint, jint, jint*, GLchar*"),
  ("glGetString", "S", "glGetString", [("i","n")], "STRING"),
  ("glGetUniformLocation", "I", "glGetUniformLocation", [("i","p"),("s","n")], "glGetUniformLocation(p, n_str)"),
  ("glLineWidth", "V", "glLineWidth", [("f","w")]),
  ("glLinkProgram", "V", "glLinkProgram", [("i","p")]),
  ("glPixelStorei", "V", "glPixelStorei", [("i","p"),("i","v")]),
  ("glReadPixels", "V", "glReadPixels", [("i","x"),("i","y"),("i","w"),("i","h"),("i","f"),("i","t"),("b","d")]),
  ("glScissor", "V", "glScissor", [("i","x"),("i","y"),("i","w"),("i","h")]),
  ("glShaderSource", "V", "glShaderSource", [("i","sh"),("s","src")], "glShaderSource(sh, 1, &src_str, NULL)", "jint, jint, const GLchar* const*, const jint*"),
  ("glTexImage2D", "V", "glTexImage2D", [("i","t"),("i","l"),("i","if_"),("i","w"),("i","h"),("i","b2"),("i","f"),("i","ty"),("b","d")]),
  ("glTexSubImage2D", "V", "glTexSubImage2D", [("i","t"),("i","l"),("i","xo"),("i","yo"),("i","w"),("i","h"),("i","f"),("i","ty"),("b","d")]),
  ("glTexSubImage2DI", "V", "glTexSubImage2D", [("i","t"),("i","l"),("i","xo"),("i","yo"),("i","w"),("i","h"),("i","f"),("i","ty"),("ai","d")]),
  ("glTexParameteri", "V", "glTexParameteri", [("i","t"),("i","p"),("i","v")]),
  ("glTexStorage2D", "V", "glTexStorage2D", [("i","t"),("i","l"),("i","if_"),("i","w"),("i","h")]),
  ("glTexStorage2DMultisample", "V", "glTexStorage2DMultisample", [("i","t"),("i","s"),("i","if_"),("i","w"),("i","h"),("z","fx")]),
  ("glUniform1f", "V", "glUniform1f", [("i","l"),("f","v")]),
  ("glUniform1i", "V", "glUniform1i", [("i","l"),("i","v")]),
  ("glUniform2f", "V", "glUniform2f", [("i","l"),("f","v0"),("f","v1")]),
  ("glUniform2fv", "V", "glUniform2fv", [("i","l"),("i","c"),("b","d")]),
  ("glUniform3f", "V", "glUniform3f", [("i","l"),("f","v0"),("f","v1"),("f","v2")]),
  ("glUniform3fv", "V", "glUniform3fv", [("i","l"),("i","c"),("b","d")]),
  ("glUniform4f", "V", "glUniform4f", [("i","l"),("f","v0"),("f","v1"),("f","v2"),("f","v3")]),
  ("glUniform4fv", "V", "glUniform4fv", [("i","l"),("i","c"),("b","d")]),
  ("glUniformMatrix4fv", "V", "glUniformMatrix4fv", [("i","l"),("i","c"),("z","t"),("b","d")]),
  ("glUseProgram", "V", "glUseProgram", [("i","p")]),
  ("glVertexAttribPointer", "V", "glVertexAttribPointer", [("i","idx"),("i","sz"),("i","t"),("z","n"),("i","st"),("l","p")]),
  ("glVertexAttribPointerB", "V", "glVertexAttribPointer", [("i","idx"),("i","sz"),("i","t"),("z","n"),("i","st"),("b","d")]),
  ("glViewport", "V", "glViewport", [("i","x"),("i","y"),("i","w"),("i","h")]),
  # GLES30
  ("glDrawBuffersA", "V", "glDrawBuffers", [("i","n"),("ai","d")]),
  ("glDrawBuffers", "V", "glDrawBuffers", [("i","n"),("b","d")]),
  ("glBlitFramebuffer", "V", "glBlitFramebuffer", [("i","a"),("i","b2"),("i","c"),("i","d"),("i","e"),("i","f"),("i","g"),("i","h"),("i","m"),("i","fl")]),
  ("glReadBuffer", "V", "glReadBuffer", [("i","s")]),
  ("glGetUniformBlockIndex", "I", "glGetUniformBlockIndex", [("i","p"),("s","n")], "glGetUniformBlockIndex(p, n_str)"),
  ("glUniformBlockBinding", "V", "glUniformBlockBinding", [("i","p"),("i","idx"),("i","b")]),
  ("glProgramParameteri", "V", "glProgramParameteri", [("i","p"),("i","pn"),("i","v")]),
  ("glProgramBinary", "V", "glProgramBinary", [("i","p"),("i","bf"),("b","d"),("i","len")]),
  ("glGetProgramBinary", "V", "glGetProgramBinary", [("i","p"),("i","sz"),("Ai","len"),("Ai","fmt"),("b","d")], None, "jint, jint, jint*, jint*, void*"),
  # GLES32 / KHR_debug
  ("glDebugMessageControl", "V", "glDebugMessageControl", [("i","so"),("i","ty"),("i","sv"),("ai","ids"),("z","e")], "glDebugMessageControl(so, ty, sv, ids_len, ids_e, e)", "jint, jint, jint, jint, const jint*, GLboolean"),
  ("glObjectLabel", "V", "glObjectLabel", [("i","id"),("i","nm"),("s","l")], "glObjectLabel(id, nm, l_len, l_str)", "jint, jint, jint, const GLchar*"),
]

IMPORTS = {
  "i": "int", "f": "float", "z": "boolean", "l": "long",
  "b": ("java.nio.Buffer", "int"),
  "s": "String",
  "ai": "int[]", "Ai": "int[]", "af": "float[]", "as": "short[]", "ab": "byte[]",
}
JTYPE = {"i":"jint","f":"jfloat","z":"jboolean","l":"jlong","b":"jobject","s":"jstring","ai":"jintArray","Ai":"jintArray","af":"jfloatArray","as":"jshortArray","ab":"jbyteArray"}
CTYPE = {"i":"jint","f":"jfloat","z":"GLboolean","l":"const GLvoid*","b":"const void*","s":"const char*","ai":"const jint*","Ai":"jint*","af":"const jfloat*","as":"const jshort*","ab":"const jbyte*"}
GET = {"ai":"jint","Ai":"jint","af":"jfloat","as":"jshort","ab":"jbyte"}


def java_sig(spec):
  parts = []
  for code, name in spec:
    t = IMPORTS[code]
    if code == "b":
      parts.append(f"java.nio.Buffer {name}, int {name}Off")
    elif code in ("ai","Ai","af","as","ab"):
      parts.append(f"{t} {name}, int {name}Off")
    else:
      parts.append(f"{t} {name}")
  return ", ".join(parts)


def jni_sig(spec):
  parts = []
  for code, name in spec:
    if code == "b":
      parts.append(f"jobject {name}, jint {name}Off")
    elif code in ("ai","Ai","af","as","ab"):
      parts.append(f"{JTYPE[code]} {name}, jint {name}Off")
    else:
      parts.append(f"{JTYPE[code]} {name}")
  return ", ".join(parts)


def locals_for(spec):
  # No JNI calls are allowed while a critical pin is held, so all
  # GetArrayLength / GetDirectBufferAddress / GetStringUTFChars happen
  # first, then all GetPrimitiveArrayCritical pins.
  out = []
  for code, name in spec:
    if code == "b":
      out.append(f"  void* {name}_p = {name} != NULL ? (void*)((char*)env->GetDirectBufferAddress({name}) + {name}Off) : NULL;")
    elif code == "s":
      out.append(f"  const char* {name}_str = env->GetStringUTFChars({name}, NULL);")
      out.append(f"  const jsize {name}_len = env->GetStringUTFLength({name});")
    elif code in GET:
      out.append(f"  const jsize {name}_len = env->GetArrayLength({name}) - {name}Off;")
  for code, name in spec:
    if code in GET:
      out.append(f"  {GET[code]}* {name}_c = ({GET[code]}*)env->GetPrimitiveArrayCritical({name}, NULL);")
      out.append(f"  {GET[code]}* {name}_e = {name}_c + {name}Off;")
  return out


def releases_for(spec):
  # Critical pins are released first (still inside the critical region no
  # other JNI calls may run), then strings.
  out = []
  for code, name in spec:
    if code == "Ai":
      out.append(f"  env->ReleasePrimitiveArrayCritical({name}, {name}_c, 0);")
    elif code in GET:
      out.append(f"  env->ReleasePrimitiveArrayCritical({name}, {name}_c, JNI_ABORT);")
  for code, name in spec:
    if code == "s":
      out.append(f"  env->ReleaseStringUTFChars({name}, {name}_str);")
  return out


def c_call_args(spec, call=None):
  if call:
    return call
  args = []
  for code, name in spec:
    if code == "b":
      args.append(f"{name}_p")
    elif code == "s":
      args.append(f"{name}_str")
    elif code in GET:
      args.append(f"{name}_e")
    elif code == "z":
      args.append(name)
    elif code == "l":
      args.append(f"(const GLvoid*)(uintptr_t){name}")
    else:
      args.append(name)
  return ", ".join(args)


def c_args(spec):
  return ", ".join(CTYPE[c] for c, _ in spec)


forbidden = {"b2": "border_"}  # avoid C keyword collisions


def main():
  java, impl, table_fields, resolvers = [], [], [], []
  seen_pfn = set()
  spec_sig, table_idx = {}, {}

  for entry in SPECS:
    jname, ret, glname, params = entry[0], entry[1], entry[2], entry[3]
    call = entry[4] if len(entry) > 4 else None
    pfn_override = entry[5] if len(entry) > 5 else None
    jret = {"V": "void", "I": "int", "S": "String"}[ret]
    java.append(f"  public static native {jret} {jname}({java_sig(params)});")

    # PFN typedef once per real GL symbol — Buffer params (void*) satisfy
    # both buffer and array call sites, so prefer those when a GL name is
    # shared between variants.
    if glname not in seen_pfn or pfn_override or any(c == "b" for c, _ in params):
      if glname not in seen_pfn or (not any(cc == "b" for cc, _ in spec_sig[glname]) and (any(cc == "b" for cc, _ in params) or pfn_override)):
        spec_sig[glname] = params
        seen_pfn.add(glname)
        fret = "const GLchar*" if ret == "S" else ("GLint" if ret == "I" else "void")
        if glname in table_idx:
          table_fields[table_idx[glname]] = f"  {fret} (APIENTRY *{glname})({pfn_override if pfn_override else (c_args(params) if params else 'void')});"
        else:
          table_idx[glname] = len(table_fields)
          table_fields.append(f"  {fret} (APIENTRY *{glname})({pfn_override if pfn_override else (c_args(params) if params else 'void')});")
          resolvers.append(f'  RESOLVE({glname});')

    if call is None:
      call = f"{glname}({c_call_args(params)})"
    locals_ = "\n".join(locals_for(params))
    rels = "\n".join(releases_for(params))
    body_pre = locals_ + ("\n" if locals_ else "")
    body_post = ("\n" + rels if rels else "")

    if ret == "V":
      call_code = f"  g_fn.{call};"
      ret_stmt = ""
      if rels:
        call_code = f"  g_fn.{call};"
    elif ret == "I":
      call_code = f"  const jint r = (jint)g_fn.{call};"
      ret_stmt = "\n  return r;"
    else:
      if call == "STRING":
        call_code = f"  const GLchar* s = g_fn.glGetString({c_call_args(params)});"
        ret_stmt = "\n  return s != NULL ? env->NewStringUTF((const char*)s) : env->NewStringUTF(\"\");"
      elif call == "INFOLOG":
        # query length, allocate, fetch
        pn = params[0][1]
        getiv = "glGetProgramiv" if "Program" in glname else "glGetShaderiv"
        call_code = (
          f"  GLint len = 0;\n"
          f"  g_fn.{getiv}({pn}, GL_INFO_LOG_LENGTH, &len);\n"
          f"  if(len <= 0) {{ return env->NewStringUTF(\"\"); }}\n"
          f"  char* log = (char*)malloc((size_t)len + 1);\n"
          f"  g_fn.{glname}({pn}, len, NULL, log);\n"
          f"  log[len] = 0;\n"
          f"  jstring out = env->NewStringUTF(log);\n"
          f"  free(log);"
        )
        ret_stmt = "\n  return out;"
      else:
        call_code = f"  const GLchar* s = g_fn.{call};"
        ret_stmt = "\n  return s != NULL ? env->NewStringUTF((const char*)s) : env->NewStringUTF(\"\");"

    impl.append(
      f"extern \"C\" JNIEXPORT {'jint' if ret == 'I' else ('jstring' if ret == 'S' else 'void')} JNICALL\n"
      f"Java_legend_game_android_GlNative_{jname}(JNIEnv* env, jclass cls{', ' if params else ''}{jni_sig(params)}) {{\n"
      f"{body_pre}{call_code}{body_post}{ret_stmt}\n}}\n"
    )

  table = "struct GlTable {\n" + "\n".join(table_fields) + "\n};"
  resolve_block = "\n".join(resolvers)

  java_out = f"""package legend.game.android;

import android.view.Surface;

/** Unified GL dispatch + ANGLE EGL bridge — generated by android/tools/gen_gl_jni.py. */
public final class GlNative {{
  private GlNative() {{ }}

  /** True while the ANGLE (GLES-over-Vulkan) backend owns the GL table. */
  public static boolean angle;

  /**
   * Resolve the GL/EGL function table. useAngle=true expects angleDir to hold
   * extracted ANGLE libEGL.so/libGLESv2.so; false binds the system GLES driver.
   * Returns null on success or an error description.
   */
  public static native String init(String angleDir, boolean useAngle);

  /** True while the resolved backend has an EGL context current on this thread. */
  public static native boolean eglContextCurrent();
  public static native long eglGetProcAddress(String name);

  // ---- ANGLE EGL bridge (used only when angle=true) ----
  public static native int eglInitAngle(boolean debug);
  public static native boolean eglCreateWindowSurface(Surface surface);
  public static native void eglDestroyWindowSurface();
  public static native void eglMakeCurrentPbuffer();
  public static native boolean eglMakeCurrentWindow();
  public static native boolean eglSwapBuffers();
  public static native void eglSwapInterval(int interval);
  public static native String eglQueryString(int name);
  public static native void eglTerminate();

  // ---- GL entry points (dispatch through the selected backend) ----
{chr(10).join(java)}
}}
"""

  cpp_out = f"""// Unified GL dispatch + ANGLE EGL bridge — generated by android/tools/gen_gl_jni.py.
//
// All GLES calls dispatch through g_fn, resolved at init from the system
// libGLESv2.so or the extracted ANGLE libGLESv2.so (GLES-over-Vulkan). ANGLE
// EGL objects live in statics here (single-window app).
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <stdint.h>
#include <GLES3/gl32.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/native_window_jni.h>
#include <android/log.h>

#define TAG "SC-GL"
#define APIENTRY GL_APIENTRY

{table}
static GlTable g_fn;

// EGL function table (ANGLE path)
typedef EGLDisplay (APIENTRY *PFNEGLGETPLATFORMDISPLAY)(EGLenum, void*, const EGLint*);
typedef EGLBoolean (APIENTRY *PFNEGLINIT)(EGLDisplay, EGLint*, EGLint*);
typedef EGLBoolean (APIENTRY *PFNEGLCHOOSECONFIG)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
typedef EGLContext (APIENTRY *PFNEGLCREATECONTEXT)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
typedef EGLSurface (APIENTRY *PFNEGLCREATEWINDOWSURFACE)(EGLDisplay, EGLConfig, ANativeWindow*, const EGLint*);
typedef EGLSurface (APIENTRY *PFNEGLCREATEPBUFFERSURFACE)(EGLDisplay, EGLConfig, const EGLint*);
typedef EGLBoolean (APIENTRY *PFNEGLMAKECURRENT)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
typedef EGLBoolean (APIENTRY *PFNEGLSWAPBUFFERS)(EGLDisplay, EGLSurface);
typedef EGLBoolean (APIENTRY *PFNEGLSWAPINTERVAL)(EGLDisplay, EGLint);
typedef EGLBoolean (APIENTRY *PFNEGLDESTROYSURFACE)(EGLDisplay, EGLSurface);
typedef EGLBoolean (APIENTRY *PFNEGLDESTROYCONTEXT)(EGLDisplay, EGLContext);
typedef EGLBoolean (APIENTRY *PFNEGLTERMINATE)(EGLDisplay);
typedef const char* (APIENTRY *PFNEGLQUERYSTRING)(EGLDisplay, EGLint);
typedef EGLint (APIENTRY *PFNEGLGETERROR)();
typedef EGLContext (APIENTRY *PFNEGLGETCURRENTCONTEXT)();
typedef void* (APIENTRY *PFNEGLGETPROCADDRESS)(const char*);

static void* g_egl_lib;
static void* g_gles_lib;
static PFNEGLGETPROCADDRESS g_getProcAddress;
static PFNEGLGETCURRENTCONTEXT g_getCurrentContext;

static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLContext g_context = EGL_NO_CONTEXT;
static EGLConfig g_config;
static EGLSurface g_surface = EGL_NO_SURFACE;
static EGLSurface g_pbuffer = EGL_NO_SURFACE;
static PFNEGLINIT eglInitialize_p;
static PFNEGLCHOOSECONFIG eglChooseConfig_p;
static PFNEGLCREATECONTEXT eglCreateContext_p;
static PFNEGLCREATEWINDOWSURFACE eglCreateWindowSurface_p;
static PFNEGLCREATEPBUFFERSURFACE eglCreatePbufferSurface_p;
static PFNEGLMAKECURRENT eglMakeCurrent_p;
static PFNEGLSWAPBUFFERS eglSwapBuffers_p;
static PFNEGLSWAPINTERVAL eglSwapInterval_p;
static PFNEGLDESTROYSURFACE eglDestroySurface_p;
static PFNEGLDESTROYCONTEXT eglDestroyContext_p;
static PFNEGLTERMINATE eglTerminate_p;
static PFNEGLQUERYSTRING eglQueryString_p;
static PFNEGLGETERROR eglGetError_p;

#define EGL_PLATFORM_ANGLE_ANGLE              0x3202
#define EGL_PLATFORM_ANGLE_TYPE_ANGLE         0x3203
#define EGL_PLATFORM_ANGLE_TYPE_VULKAN_ANGLE  0x3450
#define EGL_PLATFORM_ANGLE_DEBUG_LAYERS_ENABLED_ANGLE 0x3451

#define RESOLVE(fn) \\
  g_fn.fn = (__typeof__(g_fn.fn))(g_getProcAddress != NULL ? g_getProcAddress(#fn) : NULL); \\
  if(g_fn.fn == NULL) g_fn.fn = (__typeof__(g_fn.fn))dlsym(g_gles_lib, #fn); \\
  if(g_fn.fn == NULL) __android_log_write(ANDROID_LOG_WARN, TAG, "unresolved " #fn)

static const char* err;

extern "C" JNIEXPORT jstring JNICALL
Java_legend_game_android_GlNative_init(JNIEnv* env, jclass cls, jstring angleDir, jboolean useAngle) {{
  memset(&g_fn, 0, sizeof(g_fn));
  g_egl_lib = NULL;
  g_gles_lib = NULL;
  g_getProcAddress = NULL;
  g_getCurrentContext = NULL;

  char dir[512];
  const char* d = env->GetStringUTFChars(angleDir, NULL);
  snprintf(dir, sizeof(dir), "%s", d);
  env->ReleaseStringUTFChars(angleDir, d);

  char egl_path[600], gles_path[600];
  if(useAngle) {{
    snprintf(egl_path, sizeof(egl_path), "%s/libEGL.so", dir);
    snprintf(gles_path, sizeof(gles_path), "%s/libGLESv2.so", dir);
  }} else {{
    snprintf(egl_path, sizeof(egl_path), "libEGL.so");
    snprintf(gles_path, sizeof(gles_path), "libGLESv2.so");
  }}

  g_egl_lib = dlopen(egl_path, RTLD_NOW | RTLD_LOCAL);
  if(g_egl_lib == NULL) {{ return env->NewStringUTF(dlerror()); }}
  g_gles_lib = dlopen(gles_path, RTLD_NOW | RTLD_LOCAL);
  if(g_gles_lib == NULL) {{ return env->NewStringUTF(dlerror()); }}

  g_getProcAddress = (PFNEGLGETPROCADDRESS)dlsym(g_egl_lib, "eglGetProcAddress");
  g_getCurrentContext = (PFNEGLGETCURRENTCONTEXT)dlsym(g_egl_lib, "eglGetCurrentContext");

{resolve_block}

  if(g_fn.glGetString == NULL || g_fn.glClear == NULL) {{
    return env->NewStringUTF("core GLES entry points missing");
  }}

  jfieldID f = env->GetStaticFieldID(cls, "angle", "Z");
  env->SetStaticBooleanField(cls, f, useAngle);
  return NULL;
}}

extern "C" JNIEXPORT jboolean JNICALL
Java_legend_game_android_GlNative_eglContextCurrent(JNIEnv* env, jclass cls) {{
  return g_getCurrentContext != NULL && g_getCurrentContext() != EGL_NO_CONTEXT ? JNI_TRUE : JNI_FALSE;
}}

extern "C" JNIEXPORT jlong JNICALL
Java_legend_game_android_GlNative_eglGetProcAddress(JNIEnv* env, jclass cls, jstring name) {{
  const char* n = env->GetStringUTFChars(name, NULL);
  void* p = g_getProcAddress != NULL ? g_getProcAddress(n) : NULL;
  if(p == NULL) {{ p = dlsym(g_gles_lib, n); }}
  env->ReleaseStringUTFChars(name, n);
  return (jlong)(uintptr_t)p;
}}

extern "C" JNIEXPORT jint JNICALL
Java_legend_game_android_GlNative_eglInitAngle(JNIEnv* env, jclass cls, jboolean debug) {{
  PFNEGLGETPLATFORMDISPLAY getPlatformDisplay = (PFNEGLGETPLATFORMDISPLAY)dlsym(g_egl_lib, "eglGetPlatformDisplay");
  if(getPlatformDisplay == NULL) {{ return -1; }}

  eglInitialize_p = (PFNEGLINIT)dlsym(g_egl_lib, "eglInitialize");
  eglChooseConfig_p = (PFNEGLCHOOSECONFIG)dlsym(g_egl_lib, "eglChooseConfig");
  eglCreateContext_p = (PFNEGLCREATECONTEXT)dlsym(g_egl_lib, "eglCreateContext");
  eglCreateWindowSurface_p = (PFNEGLCREATEWINDOWSURFACE)dlsym(g_egl_lib, "eglCreateWindowSurface");
  eglCreatePbufferSurface_p = (PFNEGLCREATEPBUFFERSURFACE)dlsym(g_egl_lib, "eglCreatePbufferSurface");
  eglMakeCurrent_p = (PFNEGLMAKECURRENT)dlsym(g_egl_lib, "eglMakeCurrent");
  eglSwapBuffers_p = (PFNEGLSWAPBUFFERS)dlsym(g_egl_lib, "eglSwapBuffers");
  eglSwapInterval_p = (PFNEGLSWAPINTERVAL)dlsym(g_egl_lib, "eglSwapInterval");
  eglDestroySurface_p = (PFNEGLDESTROYSURFACE)dlsym(g_egl_lib, "eglDestroySurface");
  eglDestroyContext_p = (PFNEGLDESTROYCONTEXT)dlsym(g_egl_lib, "eglDestroyContext");
  eglTerminate_p = (PFNEGLTERMINATE)dlsym(g_egl_lib, "eglTerminate");
  eglQueryString_p = (PFNEGLQUERYSTRING)dlsym(g_egl_lib, "eglQueryString");
  eglGetError_p = (PFNEGLGETERROR)dlsym(g_egl_lib, "eglGetError");

  const EGLint platformAttribs[] = {{
    EGL_PLATFORM_ANGLE_TYPE_ANGLE, EGL_PLATFORM_ANGLE_TYPE_VULKAN_ANGLE,
    EGL_PLATFORM_ANGLE_DEBUG_LAYERS_ENABLED_ANGLE, debug ? EGL_TRUE : EGL_FALSE,
    EGL_NONE,
  }};
  g_display = getPlatformDisplay(EGL_PLATFORM_ANGLE_ANGLE, (void*)EGL_DEFAULT_DISPLAY, platformAttribs);
  if(g_display == EGL_NO_DISPLAY) {{ return -2; }}
  if(!eglInitialize_p(g_display, NULL, NULL)) {{ return eglGetError_p != NULL ? eglGetError_p() : -3; }}

  const EGLint cfg[] = {{
    EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT_KHR */,
    EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
    EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8,
    EGL_NONE,
  }};
  EGLint num = 0;
  if(!eglChooseConfig_p(g_display, cfg, &g_config, 1, &num) || num == 0) {{ return -4; }}

  const EGLint ctx[] = {{ EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE }};
  g_context = eglCreateContext_p(g_display, g_config, EGL_NO_CONTEXT, ctx);
  if(g_context == EGL_NO_CONTEXT) {{ return eglGetError_p != NULL ? eglGetError_p() : -5; }}

  const EGLint pb[] = {{ EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE }};
  g_pbuffer = eglCreatePbufferSurface_p(g_display, g_config, pb);
  if(g_pbuffer == EGL_NO_SURFACE) {{ return eglGetError_p != NULL ? eglGetError_p() : -6; }}
  return 0;
}}

extern "C" JNIEXPORT jboolean JNICALL
Java_legend_game_android_GlNative_eglCreateWindowSurface(JNIEnv* env, jclass cls, jobject surface) {{
  ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
  if(win == NULL) {{ return JNI_FALSE; }}
  const EGLint attribs[] = {{ EGL_NONE }};
  g_surface = eglCreateWindowSurface_p(g_display, g_config, win, attribs);
  ANativeWindow_release(win);
  return g_surface != EGL_NO_SURFACE ? JNI_TRUE : JNI_FALSE;
}}

extern "C" JNIEXPORT void JNICALL
Java_legend_game_android_GlNative_eglDestroyWindowSurface(JNIEnv* env, jclass cls) {{
  if(g_surface != EGL_NO_SURFACE) {{
    eglDestroySurface_p(g_display, g_surface);
    g_surface = EGL_NO_SURFACE;
  }}
}}

extern "C" JNIEXPORT void JNICALL
Java_legend_game_android_GlNative_eglMakeCurrentPbuffer(JNIEnv* env, jclass cls) {{
  eglMakeCurrent_p(g_display, g_pbuffer, g_pbuffer, g_context);
}}

extern "C" JNIEXPORT jboolean JNICALL
Java_legend_game_android_GlNative_eglMakeCurrentWindow(JNIEnv* env, jclass cls) {{
  return eglMakeCurrent_p(g_display, g_surface, g_surface, g_context) ? JNI_TRUE : JNI_FALSE;
}}

extern "C" JNIEXPORT jboolean JNICALL
Java_legend_game_android_GlNative_eglSwapBuffers(JNIEnv* env, jclass cls) {{
  return eglSwapBuffers_p(g_display, g_surface) ? JNI_TRUE : JNI_FALSE;
}}

extern "C" JNIEXPORT void JNICALL
Java_legend_game_android_GlNative_eglSwapInterval(JNIEnv* env, jclass cls, jint interval) {{
  eglSwapInterval_p(g_display, interval);
}}

extern "C" JNIEXPORT jstring JNICALL
Java_legend_game_android_GlNative_eglQueryString(JNIEnv* env, jclass cls, jint name) {{
  const char* s = eglQueryString_p != NULL ? eglQueryString_p(g_display, name) : NULL;
  return s != NULL ? env->NewStringUTF(s) : env->NewStringUTF("");
}}

extern "C" JNIEXPORT void JNICALL
Java_legend_game_android_GlNative_eglTerminate(JNIEnv* env, jclass cls) {{
  if(eglMakeCurrent_p == NULL || eglTerminate_p == NULL) {{ g_display = EGL_NO_DISPLAY; return; }}
  eglMakeCurrent_p(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
  if(g_surface != EGL_NO_SURFACE) {{ eglDestroySurface_p(g_display, g_surface); g_surface = EGL_NO_SURFACE; }}
  if(g_pbuffer != EGL_NO_SURFACE) {{ eglDestroySurface_p(g_display, g_pbuffer); g_pbuffer = EGL_NO_SURFACE; }}
  if(g_context != EGL_NO_CONTEXT) {{ eglDestroyContext_p(g_display, g_context); g_context = EGL_NO_CONTEXT; }}
  eglTerminate_p(g_display);
  g_display = EGL_NO_DISPLAY;
}}

{chr(10).join(impl)}
"""

  import pathlib
  root = pathlib.Path(__file__).resolve().parents[2]
  (root / "android/src/main/java/legend/game/android/GlNative.java").write_text(java_out)
  (root / "android/src/main/cpp/gl_jni.cpp").write_text(cpp_out)
  print(f"wrote GlNative.java ({len(java)} natives) + gl_jni.cpp")


if __name__ == "__main__":
  main()
