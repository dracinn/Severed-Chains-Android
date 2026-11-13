package legend.game.macos;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLMetal.SDL_Metal_CreateView;
import static org.lwjgl.sdl.SDLMetal.SDL_Metal_DestroyView;
import static org.lwjgl.sdl.SDLMetal.SDL_Metal_GetLayer;
import static org.lwjgl.sdl.SDLVideo.SDL_CreateWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_DestroyWindow;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_HIDDEN;
import static org.lwjgl.sdl.SDLVideo.SDL_WINDOW_RESIZABLE;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Optional GLES-over-ANGLE (Metal) context for platforms where SDL has no
 * EGL driver, e.g. macOS. Enabled by pointing {@code lod.angle.lib} at an
 * ANGLE {@code libEGL.dylib}; its sibling {@code libGLESv2.dylib} must sit
 * next to it. Falls back to null on any failure so the caller can use the
 * normal GL/GLES paths.
 */
public final class AngleContext {
  private static final Logger LOGGER = LogManager.getFormatterLogger(AngleContext.class);

  private static final int EGL_PLATFORM_ANGLE = 0x3202;
  private static final int EGL_PLATFORM_ANGLE_TYPE = 0x3203;
  private static final int EGL_PLATFORM_ANGLE_TYPE_VULKAN = 0x3450;
  private static final int EGL_PLATFORM_ANGLE_TYPE_METAL = 0x3489;
  private static final int EGL_FEATURE_OVERRIDES_ENABLED_ANGLE = 0x3466;
  private static final int EGL_OPENGL_ES_API = 0x30A0;
  private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
  private static final int EGL_SURFACE_TYPE = 0x3033;
  private static final int EGL_WINDOW_BIT = 0x04;
  private static final int EGL_RENDERABLE_TYPE = 0x3040;
  private static final int EGL_OPENGL_ES3_BIT = 0x40;
  private static final int EGL_RED_SIZE = 0x3024;
  private static final int EGL_GREEN_SIZE = 0x3023;
  private static final int EGL_BLUE_SIZE = 0x3022;
  private static final int EGL_ALPHA_SIZE = 0x3021;
  private static final int EGL_DEPTH_SIZE = 0x3025;
  private static final int EGL_STENCIL_SIZE = 0x3026;
  private static final int EGL_NONE = 0x3038;
  private static final int EGL_VENDOR = 0x3053;
  private static final int EGL_VERSION = 0x3054;
  private static final int GL_VERSION = 0x1F02;

  private static final Linker LINKER = Linker.nativeLinker();
  private static final MemorySegment NULL_SEG = MemorySegment.NULL;

  private static volatile String activeBackend;

  /** Renderer name for the window title — "OpenGL" when the ANGLE path isn't in use. */
  public static String rendererName() {
    return activeBackend != null ? activeBackend : "OpenGL";
  }

  /** Reads the Graphics > Renderer selection persisted in config.conf. */
  private static String rendererConfig() {
    final java.util.Properties props = new java.util.Properties();
    try(final java.io.InputStream in = Files.newInputStream(Path.of("config.conf"))) {
      props.load(in);
    } catch(final java.io.IOException e) {
      return "AUTO";
    }
    return props.getProperty("renderer_backend", "AUTO");
  }

  private final long window;
  private final long metalView;
  private final long display;
  private final long surface;
  private final long context;
  private final MethodHandle swapBuffers;
  private final MethodHandle destroySurface;
  private final MethodHandle destroyContext;

  private AngleContext(final long window, final long metalView, final long display, final long surface, final long context,
                       final MethodHandle swapBuffers, final MethodHandle destroySurface, final MethodHandle destroyContext) {
    this.window = window;
    this.metalView = metalView;
    this.display = display;
    this.surface = surface;
    this.context = context;
    this.swapBuffers = swapBuffers;
    this.destroySurface = destroySurface;
    this.destroyContext = destroyContext;
  }

  public static boolean enabled() {
    final String lib = System.getProperty("lod.angle.lib", "");
    return !lib.isEmpty() && !Boolean.getBoolean("lod.angle.disable") && Files.exists(Path.of(lib));
  }

  /** Returns a live ANGLE-Metal context or null if anything fails. */
  public static AngleContext tryCreate(final String title, final int width, final int height) {
    if(!enabled()) {
      return null;
    }

    long window = NULL;
    long metalView = NULL;
    try(final Arena arena = Arena.ofConfined()) {
      final SymbolLookup egl = SymbolLookup.libraryLookup(Path.of(System.getProperty("lod.angle.lib")), Arena.ofAuto());
      final SymbolLookup gles = SymbolLookup.libraryLookup(Path.of(System.getProperty("lod.angle.lib")).resolveSibling("libGLESv2.dylib"), Arena.ofAuto());

      final MethodHandle getPlatformDisplay = down(egl, "eglGetPlatformDisplay",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
      final MethodHandle initialize = down(egl, "eglInitialize",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
      final MethodHandle chooseConfig = down(egl, "eglChooseConfig",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      final MethodHandle bindApi = down(egl, "eglBindAPI", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
      final MethodHandle createContext = down(egl, "eglCreateContext",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
      final MethodHandle createWindowSurface = down(egl, "eglCreateWindowSurface",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
      final MethodHandle makeCurrent = down(egl, "eglMakeCurrent",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
      final MethodHandle swapInterval = down(egl, "eglSwapInterval", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
      final MethodHandle swapBuffers = down(egl, "eglSwapBuffers", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
      final MethodHandle destroySurface = down(egl, "eglDestroySurface", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
      final MethodHandle destroyContext = down(egl, "eglDestroyContext", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
      final MethodHandle queryString = down(egl, "eglQueryString", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
      final MethodHandle getProcAddress = down(egl, "eglGetProcAddress", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

      // Backend preference: -Dlod.angle.backend=vulkan|metal|auto overrides;
      // otherwise the in-game Graphics > Renderer choice persisted to
      // config.conf (SYSTEM_GL skips ANGLE entirely). auto → Metal only:
      // Vulkan reaches ES 3.2 via MoltenVK; Metal self-caps at ES 3.0 and
      // falls back to system GL.
      String backend = System.getProperty("lod.angle.backend");
      if(backend == null) {
        backend = switch(rendererConfig()) {
          case "SYSTEM_GL" -> "none";
          case "ANGLE_VULKAN" -> "vulkan";
          case "ANGLE_METAL" -> "metal";
          default -> "auto";
        };
      }
      if("none".equals(backend)) {
        return null;
      }
      final int[] backends = switch(backend) {
        case "vulkan" -> new int[] {EGL_PLATFORM_ANGLE_TYPE_VULKAN};
        case "metal" -> new int[] {EGL_PLATFORM_ANGLE_TYPE_METAL};
        default -> new int[] {EGL_PLATFORM_ANGLE_TYPE_METAL};
      };

      window = SDL_CreateWindow(title, width, height, SDL_WINDOW_HIDDEN | SDL_WINDOW_RESIZABLE);
      if(window == NULL) {
        throw new IllegalStateException("Failed to create SDL window: " + SDL_GetError());
      }

      metalView = SDL_Metal_CreateView(window);
      if(metalView == NULL) {
        throw new IllegalStateException("SDL_Metal_CreateView failed: " + SDL_GetError());
      }
      final long layer = SDL_Metal_GetLayer(metalView);
      if(layer == NULL) {
        throw new IllegalStateException("SDL_Metal_GetLayer returned null");
      }

      long display = NULL;
      String backendName = null;
      for(final int type : backends) {
        MemorySegment displayAttribs = arena.allocateFrom(ValueLayout.JAVA_LONG, EGL_PLATFORM_ANGLE_TYPE, type, EGL_NONE);
        if(type == EGL_PLATFORM_ANGLE_TYPE_VULKAN) {
          // MoltenVK self-caps ANGLE at ES 2.0 (no VK_EXT_transform_feedback,
          // non-standard sample locations). The engine's 2D workload uses
          // neither, so opt into the ES 3.2 the hardware can run.
          final MemorySegment featureName = arena.allocateFrom("exposeES32ForTesting");
          final MemorySegment featureList = arena.allocate(ValueLayout.ADDRESS, 2);
          featureList.set(ValueLayout.ADDRESS, 0, featureName);
          featureList.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(), NULL_SEG);
          displayAttribs = arena.allocateFrom(ValueLayout.JAVA_LONG,
            EGL_PLATFORM_ANGLE_TYPE, type, EGL_FEATURE_OVERRIDES_ENABLED_ANGLE, featureList.address(), EGL_NONE);
        }
        final long candidate = (long)getPlatformDisplay.invoke(EGL_PLATFORM_ANGLE, 0L, displayAttribs);
        if(candidate != NULL && (int)initialize.invoke(candidate, NULL_SEG, NULL_SEG) != 0) {
          display = candidate;
          backendName = type == EGL_PLATFORM_ANGLE_TYPE_VULKAN ? "Vulkan" : "Metal";
          break;
        }
      }
      if(display == NULL) {
        throw new IllegalStateException("eglGetPlatformDisplay/eglInitialize failed for all backends");
      }

      final long vendor = (long)queryString.invoke(display, EGL_VENDOR);
      final long version = (long)queryString.invoke(display, EGL_VERSION);
      LOGGER.info("ANGLE (%s) EGL vendor %s, version %s", backendName, cstr(vendor), cstr(version));

      final MemorySegment configAttribs = arena.allocateFrom(ValueLayout.JAVA_INT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8, EGL_NONE);
      final MemorySegment configOut = arena.allocateFrom(ValueLayout.JAVA_LONG, 0L);
      final MemorySegment numOut = arena.allocateFrom(ValueLayout.JAVA_INT, 0);
      if((int)chooseConfig.invoke(display, configAttribs, configOut, 1, numOut) == 0 || numOut.get(ValueLayout.JAVA_INT, 0) == 0) {
        throw new IllegalStateException("eglChooseConfig found no ES3 config");
      }

      if((int)bindApi.invoke(EGL_OPENGL_ES_API) == 0) {
        throw new IllegalStateException("eglBindAPI(EGL_OPENGL_ES_API) failed");
      }

      final MemorySegment contextAttribs = arena.allocateFrom(ValueLayout.JAVA_INT, EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE);
      final long context = (long)createContext.invoke(display, configOut.get(ValueLayout.JAVA_LONG, 0), 0L, contextAttribs);
      if(context == NULL) {
        throw new IllegalStateException("eglCreateContext failed");
      }

      final long surface = (long)createWindowSurface.invoke(display, configOut.get(ValueLayout.JAVA_LONG, 0), layer, NULL_SEG);
      if(surface == NULL) {
        throw new IllegalStateException("eglCreateWindowSurface failed");
      }

      if((int)makeCurrent.invoke(display, surface, surface, context) == 0) {
        throw new IllegalStateException("eglMakeCurrent failed");
      }
      swapInterval.invoke(display, 0);

      // Check the context's ES level before touching LWJGL's GLES module —
      // once GLES.create runs on this thread the GL fallback can no longer
      // initialize, so an insufficient context must bail here.
      final MethodHandle getString = LINKER.downcallHandle(gles.find("glGetString").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
      final String glesVersion = cstr((long)getString.invoke(GL_VERSION));
      LOGGER.info("ANGLE context reports: %s", glesVersion);
      if(!glesVersion.contains("ES 3.2") && !glesVersion.contains("ES 3.3") && !glesVersion.contains("ES 3.4")) {
        throw new IllegalStateException("ANGLE provides " + glesVersion + " — OpenGLES 3.2 required");
      }

      // LWJGL eagerly registers a GLES provider bound to the system GL when
      // its module initializes — replace it so all ES entry points resolve
      // through ANGLE instead.
      GLES.destroy();
      GLES.create(providerFrom(getProcAddress, gles));

      final GLESCapabilities caps = GLES.createCapabilities();
      if(!caps.GLES32) {
        throw new IllegalStateException("ANGLE did not provide an OpenGLES 3.2 context");
      }

      // Record GL/EGL caps for crash reports (mirrors AndroidWindow).
      GameLog.logGlInfo(
        cstr((long)getString.invoke(0x1F00)), cstr((long)getString.invoke(0x1F01)),
        glesVersion, cstr((long)getString.invoke(0x8B8C)), cstr((long)getString.invoke(0x1F03)),
        cstr(vendor), cstr(version),
        cstr((long)queryString.invoke(display, 0x308D)), cstr((long)queryString.invoke(display, 0x3055)));

      activeBackend = backendName;
      return new AngleContext(window, metalView, display, surface, context, swapBuffers, destroySurface, destroyContext);
    } catch(final Throwable t) {
      activeBackend = null;
      LOGGER.warn("ANGLE init failed, falling back to system GL: %s", t.toString());
      if(metalView != NULL) {
        SDL_Metal_DestroyView(metalView);
      }
      if(window != NULL) {
        SDL_DestroyWindow(window);
      }
      return null;
    }
  }

  /** ES functions resolve from libGLESv2, falling back to eglGetProcAddress. */
  private static FunctionProvider providerFrom(final MethodHandle getProcAddress, final SymbolLookup gles) {
    return name -> {
      try {
        final String func = MemoryUtil.memASCII(name);
        final long exported = gles.find(func).map(MemorySegment::address).orElse(0L);
        if(exported != 0) {
          return exported;
        }
        return (long)getProcAddress.invoke(Arena.ofAuto().allocateFrom(func));
      } catch(final Throwable t) {
        return 0L;
      }
    };
  }

  private static MethodHandle down(final SymbolLookup lib, final String name, final FunctionDescriptor desc) {
    return LINKER.downcallHandle(lib.find(name).orElseThrow(() -> new IllegalStateException("Missing symbol " + name)), desc);
  }

  private static String cstr(final long address) {
    return address == 0 ? "?" : MemorySegment.ofAddress(address).reinterpret(Long.MAX_VALUE).getString(0);
  }

  public long window() {
    return this.window;
  }

  public void swap() {
    try {
      this.swapBuffers.invoke(this.display, this.surface);
    } catch(final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public void destroy() {
    try {
      this.destroySurface.invoke(this.display, this.surface);
      this.destroyContext.invoke(this.display, this.context);
    } catch(final Throwable ignored) { }
    SDL_Metal_DestroyView(this.metalView);
    SDL_DestroyWindow(this.window);
  }
}
