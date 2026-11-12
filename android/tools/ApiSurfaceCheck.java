import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedField;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host-side check that every JDK-level member the dexed app references exists
 * on the Android runtime at minSdk. Runs after dexing so core-library
 * desugaring has already rewritten covered calls to their j$ twins; whatever
 * still points at java.* is what ART must resolve on device.
 *
 * Members are looked up in the SDK's api-versions.xml (the same database lint
 * uses): a class/member absent entirely, added after minSdk, or removed before
 * minSdk is a potential NoSuchMethodError/NoClassDefFoundError. Findings are
 * diffed against a checked-in baseline so new upstream API use fails the build
 * without re-flagging reviewed entries.
 *
 * usage: ApiSurfaceCheck <api-versions.xml> <minSdk> <baseline-file|->
 *        [--write-baseline <out-file>] <dex-dir-or-file>...
 */
public final class ApiSurfaceCheck {
  private ApiSurfaceCheck() { }

  /** Type owners that live in the JDK side of ART, not app/library code. */
  private static final Pattern JDK_OWNER = Pattern.compile("^\\[*L(?:java|javax|jdk|sun|com/sun)/");
  private static final Pattern OBJECT_TYPE = Pattern.compile("^\\[*L([^;]+);.*$");

  private static final class ApiClass {
    int since = Integer.MAX_VALUE;
    int removed = Integer.MAX_VALUE;
    final List<String> parents = new ArrayList<>();
    final Map<String, Integer> methods = new HashMap<>(); // name+desc -> since
    final Map<String, Integer> fields = new HashMap<>();  // name -> since
  }

  public static void main(final String[] args) throws Exception {
    int arg = 0;
    final File apiXml = new File(args[arg++]);
    final int minSdk = Integer.parseInt(args[arg++]);
    final File baseline = "-".equals(args[arg]) ? null : new File(args[arg]);
    arg++;
    File baselineOut = null;
    if(arg < args.length && args[arg].equals("--write-baseline")) {
      arg++;
      baselineOut = new File(args[arg++]);
    }

    final Map<String, ApiClass> api = parseApiVersions(apiXml);
    final Set<String> findings = new TreeSet<>();
    final List<DexBackedDexFile> dexes = new ArrayList<>();
    final Set<String> dexTypes = new java.util.HashSet<>();

    for(; arg < args.length; arg++) {
      final File in = new File(args[arg]);
      if(in.isDirectory()) {
        for(final File f : in.listFiles((d, n) -> n.endsWith(".dex"))) {
          if(f.isFile()) {
            dexes.add(DexFileFactory.loadDexFile(f, Opcodes.getDefault()));
          }
        }
        for(final File sub : in.listFiles(File::isDirectory)) {
          for(final File f : sub.listFiles((d, n) -> n.endsWith(".dex"))) {
            if(f.isFile()) {
              dexes.add(DexFileFactory.loadDexFile(f, Opcodes.getDefault()));
            }
          }
        }
      } else if(in.getName().endsWith(".dex")) {
        dexes.add(DexFileFactory.loadDexFile(in, Opcodes.getDefault()));
      }
    }

    // Types shipped in the dex resolve against our own copies (shim stubs,
    // bundled libs), not ART — collect them all before checking references.
    for(final DexBackedDexFile dex : dexes) {
      for(final DexBackedClassDef cls : dex.getClasses()) {
        final Matcher m = OBJECT_TYPE.matcher(cls.getType());
        if(m.matches()) {
          dexTypes.add(m.group(1));
        }
      }
    }

    for(final DexBackedDexFile dex : dexes) {
      scanDex(dex, api, minSdk, dexTypes, findings);
    }

    final Set<String> accepted = baseline == null ? Set.of() : readBaseline(baseline);
    final Set<String> newFindings = new TreeSet<>(findings);
    newFindings.removeAll(accepted);
    final Set<String> stale = new TreeSet<>(accepted);
    stale.removeAll(findings);

    if(baselineOut != null) {
      try(final PrintWriter w = new PrintWriter(baselineOut)) {
        w.println("# JDK members the dex references that are absent at minSdk or");
        w.println("# missing from ART entirely, accepted after review. Format:");
        w.println("#   owner\tname\tdesc-or-field\treason");
        findings.forEach(w::println);
      }
      System.out.println("ApiSurfaceCheck: wrote " + findings.size() + " baseline entries to " + baselineOut);
      return;
    }

    if(!stale.isEmpty()) {
      System.out.println("ApiSurfaceCheck: " + stale.size() + " baseline entries no longer flagged (stale, safe to remove):");
      stale.forEach(s -> System.out.println("  " + s));
    }
    if(!newFindings.isEmpty()) {
      System.out.println("ApiSurfaceCheck: " + newFindings.size() + " JDK references missing at minSdk " + minSdk + ":");
      newFindings.forEach(s -> System.out.println("  " + s));
      System.out.println("Route the call through JdkCompat/JarPatcher, or review and regenerate the baseline.");
      System.exit(1);
    }
    System.out.println("ApiSurfaceCheck: clean — " + findings.size() + " flagged member(s) all in baseline");
  }

  // ---------- API database ----------

  private static Map<String, ApiClass> parseApiVersions(final File xml) throws Exception {
    final Map<String, ApiClass> api = new HashMap<>();
    final var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml);
    for(final var classNode : iterable(doc.getDocumentElement().getElementsByTagName("class"))) {
      final org.w3c.dom.Element el = (org.w3c.dom.Element)classNode;
      final ApiClass cls = new ApiClass();
      cls.since = level(el.getAttribute("since"));
      cls.removed = el.hasAttribute("removed") ? level(el.getAttribute("removed")) : Integer.MAX_VALUE;
      for(final var parent : iterable(el.getElementsByTagName("extends"))) {
        cls.parents.add(((org.w3c.dom.Element)parent).getAttribute("name"));
      }
      for(final var parent : iterable(el.getElementsByTagName("implements"))) {
        cls.parents.add(((org.w3c.dom.Element)parent).getAttribute("name"));
      }
      for(final var methodNode : iterable(el.getElementsByTagName("method"))) {
        final org.w3c.dom.Element m = (org.w3c.dom.Element)methodNode;
        final int since = m.hasAttribute("since") ? level(m.getAttribute("since")) : cls.since;
        cls.methods.merge(m.getAttribute("name"), since, Math::min);
      }
      for(final var fieldNode : iterable(el.getElementsByTagName("field"))) {
        final org.w3c.dom.Element f = (org.w3c.dom.Element)fieldNode;
        final int since = f.hasAttribute("since") ? level(f.getAttribute("since")) : cls.since;
        cls.fields.merge(f.getAttribute("name"), since, Math::min);
      }
      api.put(el.getAttribute("name"), cls);
    }
    return api;
  }

  /** '37.0'-style minor levels compare as their major integer. */
  private static int level(final String attr) {
    if(attr.isEmpty()) {
      return 1;
    }
    final int dot = attr.indexOf('.');
    return Integer.parseInt(dot < 0 ? attr : attr.substring(0, dot));
  }

  private static List<org.w3c.dom.Node> iterable(final org.w3c.dom.NodeList list) {
    final List<org.w3c.dom.Node> out = new ArrayList<>(list.getLength());
    for(int i = 0; i < list.getLength(); i++) {
      if(list.item(i) instanceof org.w3c.dom.Element) {
        out.add(list.item(i));
      }
    }
    return out;
  }

  // ---------- Dex scanning ----------

  private static void scanDex(final DexBackedDexFile dex, final Map<String, ApiClass> api, final int minSdk, final Set<String> dexTypes, final Set<String> findings) {
    for(final DexBackedClassDef cls : dex.getClasses()) {
      checkType(cls.getSuperclass(), api, minSdk, dexTypes, findings);
      for(final String iface : cls.getInterfaces()) {
        checkType(iface, api, minSdk, dexTypes, findings);
      }
      for(final DexBackedField field : cls.getFields()) {
        checkType(field.getType(), api, minSdk, dexTypes, findings);
      }
      for(final DexBackedMethod method : cls.getMethods()) {
        checkType(method.getReturnType(), api, minSdk, dexTypes, findings);
        method.getParameterTypes().forEach(t -> checkType(t.toString(), api, minSdk, dexTypes, findings));
        final var impl = method.getImplementation();
        if(impl == null) {
          continue;
        }
        for(final Instruction insn : impl.getInstructions()) {
          if(!(insn instanceof ReferenceInstruction)) {
            continue;
          }
          final Reference ref = ((ReferenceInstruction)insn).getReference();
          if(ref instanceof MethodReference) {
            checkMethod((MethodReference)ref, api, minSdk, dexTypes, findings);
          } else if(ref instanceof FieldReference) {
            checkField((FieldReference)ref, api, minSdk, dexTypes, findings);
          } else if(ref instanceof TypeReference) {
            checkType(((TypeReference)ref).getType(), api, minSdk, dexTypes, findings);
          }
        }
      }
    }
  }

  private static void checkType(final String type, final Map<String, ApiClass> api, final int minSdk, final Set<String> dexTypes, final Set<String> findings) {
    if(type == null || !JDK_OWNER.matcher(type).lookingAt()) {
      return;
    }
    final Matcher m = OBJECT_TYPE.matcher(type);
    if(!m.matches()) {
      return;
    }
    final String owner = m.group(1);
    if(dexTypes.contains(owner)) {
      return;
    }
    final ApiClass cls = api.get(owner);
    if(cls == null) {
      findings.add(owner + "\t<class>\tnot in ART");
    } else if(cls.since > minSdk || cls.removed <= minSdk) {
      findings.add(owner + "\t<class>\tsince " + cls.since);
    }
  }

  // Member resolution mirrors ART: walk the reference owner's public
  // hierarchy (extends + implements from api-versions). When no public parent
  // declares the member, resolve the real declaring class on the host JDK:
  // a hidden implementation class (AbstractStringBuilder, sun.misc.Unsafe)
  // means the member is inherited onto the ART type and present; a public
  // declaring class whose database entry lacks the member is the genuine
  // NoSuchMethodError candidate.
  private static void checkMethod(final MethodReference ref, final Map<String, ApiClass> api, final int minSdk, final Set<String> dexTypes, final Set<String> findings) {
    final String owner = ownerOf(ref.getDefiningClass());
    if(owner == null || dexTypes.contains(owner)) {
      return;
    }
    final String member = ref.getName() + "(" + String.join("", ref.getParameterTypes()) + ")" + ref.getReturnType();
    if(!ownerAvailable(owner, member, api, minSdk, findings)) {
      return;
    }
    final Set<String> hierarchy = publicHierarchy(owner, api);
    if(memberIn(member, hierarchy, api, minSdk, owner, findings)) {
      return;
    }
    try {
      final Class<?> ownerClass = Class.forName(owner.replace('/', '.'));
      final Class<?> declaring;
      if("<init>".equals(ref.getName())) {
        declaring = ownerClass.getConstructor(paramClasses(ref.getParameterTypes())).getDeclaringClass();
      } else {
        declaring = ownerClass.getMethod(ref.getName(), paramClasses(ref.getParameterTypes())).getDeclaringClass();
      }
      if(hierarchy.contains(declaring.getName().replace('.', '/'))) {
        findings.add(owner + "\t" + member + "\tnot in ART");
      }
      // else: hidden JDK implementation parent supplies it on ART
    } catch(final ReflectiveOperationException e) {
      findings.add(owner + "\t" + member + "\tnot in ART");
    }
  }

  private static void checkField(final FieldReference ref, final Map<String, ApiClass> api, final int minSdk, final Set<String> dexTypes, final Set<String> findings) {
    final String owner = ownerOf(ref.getDefiningClass());
    if(owner == null || dexTypes.contains(owner)) {
      return;
    }
    if(!ownerAvailable(owner, ref.getName(), api, minSdk, findings)) {
      return;
    }
    final Set<String> hierarchy = publicHierarchy(owner, api);
    if(memberIn(ref.getName(), hierarchy, api, minSdk, owner, findings)) {
      return;
    }
    try {
      final Class<?> ownerClass = Class.forName(owner.replace('/', '.'));
      final Class<?> declaring = ownerClass.getField(ref.getName()).getDeclaringClass();
      if(hierarchy.contains(declaring.getName().replace('.', '/'))) {
        findings.add(owner + "\t" + ref.getName() + "\tnot in ART");
      }
    } catch(final ReflectiveOperationException e) {
      findings.add(owner + "\t" + ref.getName() + "\tnot in ART");
    }
  }

  /** true when the reference owner class is present at minSdk, else recorded. */
  private static boolean ownerAvailable(final String owner, final String member, final Map<String, ApiClass> api, final int minSdk, final Set<String> findings) {
    final ApiClass cls = api.get(owner);
    if(cls == null) {
      findings.add(owner + "\t" + member + "\tclass not in ART");
      return false;
    }
    if(cls.since > minSdk || cls.removed <= minSdk) {
      findings.add(owner + "\t" + member + "\tclass since " + cls.since);
      return false;
    }
    return true;
  }

  /** The owner plus every public supertype/interface reachable in the database. */
  private static Set<String> publicHierarchy(final String owner, final Map<String, ApiClass> api) {
    final Set<String> out = new java.util.HashSet<>();
    final java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
    queue.add(owner);
    while(!queue.isEmpty()) {
      final String cur = queue.poll();
      if(!out.add(cur)) {
        continue;
      }
      final ApiClass c = api.get(cur);
      if(c != null) {
        queue.addAll(c.parents);
      }
    }
    return out;
  }

  /**
   * true when the member is declared on some public hierarchy class, recording
   * a finding if its introduction level exceeds minSdk.
   */
  private static boolean memberIn(final String member, final Set<String> hierarchy, final Map<String, ApiClass> api, final int minSdk, final String owner, final Set<String> findings) {
    int best = -1;
    String declaring = null;
    for(final String cls : hierarchy) {
      final ApiClass c = api.get(cls);
      if(c == null) {
        continue;
      }
      Integer since = c.methods.get(member);
      if(since == null) {
        since = c.fields.get(member);
      }
      if(since != null && (best < 0 || since < best)) {
        best = since;
        declaring = cls;
      }
    }
    if(declaring == null) {
      return false;
    }
    if(best > minSdk) {
      findings.add(owner + "\t" + member + "\tsince " + best);
    }
    return true;
  }

  private static Class<?>[] paramClasses(final java.util.List<? extends CharSequence> params) throws ClassNotFoundException {
    final java.util.List<Class<?>> out = new ArrayList<>();
    for(final CharSequence p : params) {
      final int[] pos = {0};
      out.add(typeOf(p.toString(), pos));
    }
    return out.toArray(new Class<?>[0]);
  }

  private static Class<?> typeOf(final String desc, final int[] pos) throws ClassNotFoundException {
    char c = desc.charAt(pos[0]++);
    switch(c) {
      case 'Z': return boolean.class;
      case 'B': return byte.class;
      case 'C': return char.class;
      case 'S': return short.class;
      case 'I': return int.class;
      case 'J': return long.class;
      case 'F': return float.class;
      case 'D': return double.class;
      case 'V': return void.class;
      case 'L': {
        final int end = desc.indexOf(';', pos[0]);
        final String name = desc.substring(pos[0], end);
        pos[0] = end + 1;
        return Class.forName(name.replace('/', '.'));
      }
      case '[': {
        final Class<?> comp = typeOf(desc, pos);
        return java.lang.reflect.Array.newInstance(comp, 0).getClass();
      }
      default: throw new ClassNotFoundException("bad descriptor: " + desc);
    }
  }

  /** Returns the internal owner name for JDK refs, null for app refs. */
  private static String ownerOf(final String definingClass) {
    if(!JDK_OWNER.matcher(definingClass).lookingAt()) {
      return null;
    }
    final Matcher m = OBJECT_TYPE.matcher(definingClass);
    return m.matches() ? m.group(1) : null;
  }

  private static Set<String> readBaseline(final File file) throws Exception {
    final Set<String> out = new TreeSet<>();
    if(!file.exists()) {
      return out;
    }
    java.nio.file.Files.readAllLines(file.toPath()).stream()
        .map(String::trim)
        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
        .forEach(out::add);
    return out;
  }
}
