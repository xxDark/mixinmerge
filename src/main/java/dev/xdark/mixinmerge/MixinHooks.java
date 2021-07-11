package dev.xdark.mixinmerge;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.CommandLineOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class MixinHooks {

  public static final String MIXIN = 'L' + Mixin.class.getName().replace('.', '/') + ';';
  private static final String PACKAGE = "org.spongepowered.asm.";
  private static final String BOOTSTRAP = PACKAGE + "launch.MixinBootstrap";
  private static final String TRANSFORMER = PACKAGE + "mixin.transformer.MixinTransformer";

  private final Object transformer;
  private final Method method;

  MixinHooks() throws Exception {
    Class<?> klass = Class.forName(TRANSFORMER);
    Constructor<?> c = klass.getDeclaredConstructor();
    c.setAccessible(true);
    transformer = c.newInstance();
    method =
        klass.getDeclaredMethod("transformClassBytes", String.class, String.class, byte[].class);
    method.setAccessible(true);
  }

  public static boolean isMixinClass(ClassNode node) {
    if (MIXIN.equals(node.name)) {
      return true;
    }
    List<AnnotationNode> annotations = node.invisibleAnnotations;
    return annotations != null && annotations.stream().anyMatch(it -> MIXIN.equals(it.desc));
  }

  public static void start() {
    try {
      Method m = Class.forName(BOOTSTRAP).getDeclaredMethod("start");
      m.setAccessible(true);
      m.invoke(null);
    } catch (ClassNotFoundException
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void doInit(List<String> args) {
    try {
      Method m = Class.forName(BOOTSTRAP).getDeclaredMethod("doInit", CommandLineOptions.class);
      m.setAccessible(true);
      m.invoke(null, CommandLineOptions.of(args));
    } catch (ClassNotFoundException
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void inject() {
    try {
      Method m = Class.forName(BOOTSTRAP).getDeclaredMethod("inject");
      m.setAccessible(true);
      m.invoke(null);
    } catch (ClassNotFoundException
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void initPhase() {
    MixinEnvironment.Phase phase = MixinEnvironment.Phase.DEFAULT;
    try {
      Method m =
          MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
      m.setAccessible(true);
      m.invoke(null, phase);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] transformClass(String name, byte[] bytes) {
    try {
      return (byte[]) method.invoke(transformer, name, name, bytes);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
