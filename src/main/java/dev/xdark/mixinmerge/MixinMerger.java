package dev.xdark.mixinmerge;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class MixinMerger {

  // temporarily store side argument
  public static String side;

  public static void main(String[] args) throws IOException {
    OptionParser parser = new OptionParser();
    parser.allowsUnrecognizedOptions();
    OptionSpec<File> inputSpec =
        parser.accepts("input").withRequiredArg().ofType(File.class).required();
    OptionSpec<File> outputSpec =
        parser.accepts("output").withRequiredArg().ofType(File.class).required();
    OptionSpec<String> sideSpec = parser.accepts("side").withRequiredArg().ofType(String.class);
    OptionSpec<String> nonOptionsSpec = parser.nonOptions();
    OptionSet options = parser.parse(args);
    side = options.valueOf(sideSpec);
    File[] files =
        options.valuesOf(inputSpec).stream()
            .flatMap(f -> f.isDirectory() ? Arrays.stream(f.listFiles()) : Stream.of(f))
            .toArray(File[]::new);
    try {
      Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      m.setAccessible(true);
      ClassLoader loader = MixinMerger.class.getClassLoader();
      for (File f : files) {
        m.invoke(loader, f.toURI().toURL());
      }
    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException ex) {
      throw new RuntimeException(ex);
    }
    File output = options.valueOf(outputSpec);
    output.mkdir();
    MixinHooks.start();
    MixinHooks.doInit(options.valuesOf(nonOptionsSpec));
    MixinHooks.inject();
    MixinHooks.initPhase();
    MixinHooks mixinHooks;
    try {
      mixinHooks = new MixinHooks();
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to create mixin hooks", ex);
    }
    File tmp = new File("mixinmerge.tmp");
    tmp.delete();
    byte[] bytes = new byte[16384];
    ByteArrayOutputStream $result = new ByteArrayOutputStream();
    for (File file : files) {
      tmp.createNewFile();
      try (ZipInputStream in = new ZipInputStream(new FileInputStream(file));
          ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmp))) {
        ZipEntry entry;
        while ((entry = in.getNextEntry()) != null) {
          String name = entry.getName();
          $result.reset();
          int r;
          while ((r = in.read(bytes)) != -1) {
            $result.write(bytes, 0, r);
          }
          byte[] data = $result.toByteArray();
          if (name.endsWith(".class")) {
            String normal = name.replace('/', '.').substring(0, name.length() - 6);
            ClassNode node = new ClassNode();
            new ClassReader(data).accept(node, ClassReader.SKIP_CODE);
            if (!MixinHooks.isMixinClass(node)) {
              data = mixinHooks.transformClass(normal, data);
            }
          }
          out.putNextEntry(new ZipEntry(name));
          out.write(data);
          out.closeEntry();
          in.closeEntry();
        }
      }
      tmp.renameTo(new File(output, file.getName()));
    }
  }
}
