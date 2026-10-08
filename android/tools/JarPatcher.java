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
 */
public final class JarPatcher {
  private JarPatcher() { }

  private static final String JDK_REFLECT = "legend/game/android/JdkReflect";

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

        if(entry.getName().endsWith(".class")) {
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
