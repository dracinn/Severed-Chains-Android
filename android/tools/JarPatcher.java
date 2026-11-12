import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Host-side bytecode patcher for third-party jars that call JDK APIs missing
 * on the Android runtime. Runs during the build (patchDependencyJars), not on
 * device. Each rewrite swaps an invokevirtual against a java.* type for an
 * invokestatic against a legend.game.android.JdkReflect helper with the
 * receiver prepended, so operand stack depth and frames stay valid.
 *
 * Currently patches mod-loader and script-recompiler:
 *   Method.canAccess(obj)            -> JdkReflect.canAccess(member, obj)
 *   Class.getProtectionDomain()      -> JdkReflect.protectionDomain(clazz)
 *   ProtectionDomain.getCodeSource() -> JdkReflect.codeSource(domain)
 *   CodeSource.getLocation()         -> JdkReflect.codeSourceLocation(src)
 *   LogManager.getLogger()           -> JdkReflect.getLogger()
 *   LogManager.getFormatterLogger()  -> JdkReflect.getFormatterLogger()
 *   Locale.of(lang[, country])       -> JdkCompat.localeOf(...)
 *   StringBuilder.repeat(...)        -> JdkCompat.sbRepeat(sb, ...)
 *   String.formatted(args)           -> String.format(str, args)
 *   Math.fma(a,b,c)                  -> JdkCompat.fma(a, b, c)
 *   ByteArrayOutputStream.writeBytes -> JdkCompat.writeBytes(baos, b)
 *   String.lines()                   -> JdkCompat.lines(str)
 *   CompletableFuture.completeAsync  -> JdkCompat.completeAsync(cf, ...)
 *   InputStream.readAllBytes()       -> JdkCompat.readAllBytes(in)
 *   Random.nextInt(origin, bound)    -> JdkCompat.nextInt(rnd, ...)
 *   BigInteger.longValueExact()      -> JdkCompat.longValueExact(bi)
 *   new FileReader(name, charset)    -> new FileReader(name)
 *   XBuffer.get(i) / put(i, ...)     -> JdkCompat.get/put(buf, i, ...)
 *   List.getFirst()/removeLast()/... -> JdkCompat.first/removeLast/...(list)
 */
public final class JarPatcher {
  private JarPatcher() { }

  private static final String JDK_REFLECT = "legend/game/android/JdkReflect";

  // List-typed owners whose Java 21 SequencedCollection calls reroute to
  // JdkCompat. Deque/ArrayDeque stay out: their first/last methods are
  // native on ART and their receivers are not Lists.
  private static final java.util.Set<String> SEQ_LIST_OWNERS = java.util.Set.of(
      "java/util/List", "java/util/ArrayList", "java/util/LinkedList",
      "java/util/Vector", "java/util/Stack", "java/util/AbstractList",
      "java/util/AbstractSequentialList", "java/util/CopyOnWriteArrayList");

  public static void main(final String[] args) throws Exception {
    if(args.length != 2) {
      throw new IllegalArgumentException("usage: JarPatcher <in.jar> <out.jar>");
    }

    int patched = 0;

    try(final JarFile in = new JarFile(args[0]); final JarOutputStream out = new JarOutputStream(new FileOutputStream(args[1]))) {
      final var entries = in.entries();
      while(entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        out.putNextEntry(new JarEntry(entry.getName()));

        final byte[] data;
        try(final InputStream stream = in.getInputStream(entry)) {
          data = stream.readAllBytes();
        }

        // JdkReflect/JdkCompat implement the very APIs being redirected;
        // rewriting their calls routes each helper through itself and
        // recurses forever.
        if(entry.getName().endsWith(".class") && !entry.getName().startsWith("legend/game/android/JdkReflect") && !entry.getName().startsWith("legend/game/android/JdkCompat")) {
          final byte[] rewritten = rewrite(data);
          if(rewritten != data) {
            patched++;
          }
          out.write(rewritten);
        } else {
          out.write(data);
        }

        out.closeEntry();
      }
    }

    System.out.println("JarPatcher: rewrote " + patched + " class(es) in " + args[0]);
  }

  private static byte[] rewrite(final byte[] classBytes) {
    final boolean[] changed = {false};

    final ClassReader reader = new ClassReader(classBytes);
    final ClassWriter writer = new ClassWriter(reader, 0);
    reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
      @Override
      public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
          @Override
          public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor, final boolean isInterface) {
            // ART (Android <= 14) lacks AccessibleObject.canAccess on Method
            if(opcode == Opcodes.INVOKEVIRTUAL && name.equals("canAccess") && descriptor.equals("(Ljava/lang/Object;)Z") && owner.startsWith("java/lang/reflect/")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, JDK_REFLECT, "canAccess", "(Ljava/lang/reflect/AccessibleObject;Ljava/lang/Object;)Z", false);
              changed[0] = true;
              return;
            }

            // ART stubs out ProtectionDomain: app classes report null domains
            // and even a constructed ProtectionDomain can yield a null
            // CodeSource. Route the whole chain through null-safe helpers.
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class") && name.equals("getProtectionDomain") && descriptor.equals("()Ljava/security/ProtectionDomain;")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, JDK_REFLECT, "protectionDomain", "(Ljava/lang/Class;)Ljava/security/ProtectionDomain;", false);
              changed[0] = true;
              return;
            }

            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/security/ProtectionDomain") && name.equals("getCodeSource") && descriptor.equals("()Ljava/security/CodeSource;")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, JDK_REFLECT, "codeSource", "(Ljava/security/ProtectionDomain;)Ljava/security/CodeSource;", false);
              changed[0] = true;
              return;
            }

            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/security/CodeSource") && name.equals("getLocation") && descriptor.equals("()Ljava/net/URL;")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, JDK_REFLECT, "codeSourceLocation", "(Ljava/security/CodeSource;)Ljava/net/URL;", false);
              changed[0] = true;
              return;
            }

            // ART (Android < 14) lacks Locale.of (JDK 19+). Redirect to the
            // equivalent constructor through a helper; identical descriptor
            // keeps the operand stack untouched.
            if(opcode == Opcodes.INVOKESTATIC && owner.equals("java/util/Locale") && name.equals("of") && (descriptor.equals("(Ljava/lang/String;)Ljava/util/Locale;") || descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Locale;"))) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "localeOf", descriptor, false);
              changed[0] = true;
              return;
            }

            // ART lacks StringBuilder.repeat (JDK 21). Receiver becomes the
            // first arg; result type already matches the call site.
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/StringBuilder") && name.equals("repeat")) {
              if(descriptor.equals("(Ljava/lang/CharSequence;I)Ljava/lang/StringBuilder;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "sbRepeat", "(Ljava/lang/StringBuilder;Ljava/lang/CharSequence;I)Ljava/lang/StringBuilder;", false);
                changed[0] = true;
                return;
              }
              if(descriptor.equals("(II)Ljava/lang/StringBuilder;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "sbRepeat", "(Ljava/lang/StringBuilder;II)Ljava/lang/StringBuilder;", false);
                changed[0] = true;
                return;
              }
            }

            // ART (Android < 14) lacks the absolute indexed get/put
            // overloads on the typed nio buffers (declared on the concrete
            // classes, not Buffer, so they can't resolve upward). Route each
            // through a JdkCompat twin; receiver becomes the first arg.
            //   XBuffer.get(i)          -> JdkCompat.get(buf, i)
            //   XBuffer.put(i, v|a|s..) -> JdkCompat.put(buf, i, ...)
            // "(I" also matches relative IntBuffer.put(int) - "(I)L..." -
            // so require a second argument type after the index.
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.startsWith("java/nio/") && owner.endsWith("Buffer") && !owner.equals("java/nio/Buffer")
                && ((name.equals("get") && descriptor.startsWith("(I)")) || (name.equals("put") && descriptor.startsWith("(I") && descriptor.charAt(2) != ')'))) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", name, "(L" + owner + ";" + descriptor.substring(1), false);
              changed[0] = true;
              return;
            }

            // ART lacks the Java 21 SequencedCollection defaults invoked on
            // List-typed receivers (list.getFirst(), list.removeLast(), ...).
            // Deque-owned calls are native since API 1 and stay untouched.
            // Owners are limited to List-family types so the prepended
            // (List) arg always verifies; JdkCompat dispatches to Deque
            // when the runtime object is one.
            if((opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL) && SEQ_LIST_OWNERS.contains(owner)
                && (((name.equals("getFirst") || name.equals("getLast") || name.equals("removeFirst") || name.equals("removeLast")) && descriptor.equals("()Ljava/lang/Object;"))
                    || ((name.equals("addFirst") || name.equals("addLast")) && descriptor.equals("(Ljava/lang/Object;)V")))) {
              final String helper = name.startsWith("get") ? Character.toLowerCase(name.charAt(3)) + name.substring(4) : name;
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", helper, "(Ljava/util/List;" + descriptor.substring(1), false);
              changed[0] = true;
              return;
            }

            // ART (Android < 14) lacks String.formatted (JDK 15). String is
            // final, so the invokevirtual always targets String itself;
            // format(String, Object...) has the same stack shape with the
            // receiver as the first arg.
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/String") && name.equals("formatted") && descriptor.equals("([Ljava/lang/Object;)Ljava/lang/String;")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
              changed[0] = true;
              return;
            }

            // ART lacks Math.fma (API 33). Plain a*b+c in JdkCompat; same
            // fallback JOML itself applies when fma is unavailable.
            if(opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/Math") && name.equals("fma") && (descriptor.equals("(FFF)F") || descriptor.equals("(DDD)D"))) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "fma", descriptor, false);
              changed[0] = true;
              return;
            }

            // ART lacks ByteArrayOutputStream.writeBytes (API 33).
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/io/ByteArrayOutputStream") && name.equals("writeBytes") && descriptor.equals("([B)V")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "writeBytes", "(Ljava/io/ByteArrayOutputStream;[B)V", false);
              changed[0] = true;
              return;
            }

            // ART lacks String.lines (API 33); String is final so the owner
            // is always java/lang/String.
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/String") && name.equals("lines") && descriptor.equals("()Ljava/util/stream/Stream;")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "lines", "(Ljava/lang/String;)Ljava/util/stream/Stream;", false);
              changed[0] = true;
              return;
            }

            // ART lacks CompletableFuture.completeAsync (API 31).
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/util/concurrent/CompletableFuture") && name.equals("completeAsync")) {
              if(descriptor.equals("(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "completeAsync", "(Ljava/util/concurrent/CompletableFuture;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;", false);
                changed[0] = true;
                return;
              }
              if(descriptor.equals("(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "completeAsync", "(Ljava/util/concurrent/CompletableFuture;Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", false);
                changed[0] = true;
                return;
              }
            }

            // ART lacks InputStream.readAllBytes (API 33).
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/io/InputStream") && name.equals("readAllBytes") && descriptor.equals("()[B")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "readAllBytes", "(Ljava/io/InputStream;)[B", false);
              changed[0] = true;
              return;
            }

            // ART lacks Random.nextInt(origin, bound) (API 35). Restricted to
            // owners assignable to java.util.Random so the widened JdkCompat
            // parameter verifies (SplittableRandom implements only
            // RandomGenerator and is intentionally not matched).
            if(opcode == Opcodes.INVOKEVIRTUAL && name.equals("nextInt") && descriptor.equals("(II)I")
                && (owner.equals("java/util/Random") || owner.equals("java/security/SecureRandom") || owner.equals("java/util/concurrent/ThreadLocalRandom"))) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "nextInt", "(Ljava/util/Random;II)I", false);
              changed[0] = true;
              return;
            }

            // ART lacks BigInteger.longValueExact (API 31).
            if(opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/math/BigInteger") && name.equals("longValueExact") && descriptor.equals("()J")) {
              super.visitMethodInsn(Opcodes.INVOKESTATIC, "legend/game/android/JdkCompat", "longValueExact", "(Ljava/math/BigInteger;)J", false);
              changed[0] = true;
              return;
            }

            // ART lacks the FileReader(String, Charset) constructor (API 33).
            // Stack is [uninit, String, Charset]: drop the charset (callers
            // pass UTF-8 and ART's default charset is UTF-8) and delegate to
            // the single-arg constructor.
            if(opcode == Opcodes.INVOKESPECIAL && owner.equals("java/io/FileReader") && name.equals("<init>") && descriptor.equals("(Ljava/lang/String;Ljava/nio/charset/Charset;)V")) {
              super.visitInsn(Opcodes.POP);
              super.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "(Ljava/lang/String;)V", false);
              changed[0] = true;
              return;
            }

            // No-arg logger factories infer the caller via StackWalker, which
            // ART lacks. Redirect to a helper that resolves the caller from
            // Thread.getStackTrace instead.
            if(opcode == Opcodes.INVOKESTATIC && owner.equals("org/apache/logging/log4j/LogManager")) {
              if(name.equals("getFormatterLogger") && descriptor.equals("()Lorg/apache/logging/log4j/Logger;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, JDK_REFLECT, "getFormatterLogger", "()Lorg/apache/logging/log4j/Logger;", false);
                changed[0] = true;
                return;
              }
              if(name.equals("getLogger") && descriptor.equals("()Lorg/apache/logging/log4j/Logger;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, JDK_REFLECT, "getLogger", "()Lorg/apache/logging/log4j/Logger;", false);
                changed[0] = true;
                return;
              }
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
          }
        };
      }
    }, 0);

    return changed[0] ? writer.toByteArray() : classBytes;
  }
}
