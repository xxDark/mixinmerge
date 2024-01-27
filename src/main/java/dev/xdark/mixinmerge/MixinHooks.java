package dev.xdark.mixinmerge;

import lombok.SneakyThrows;
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

	@SneakyThrows
	MixinHooks() {
		Class<?> klass = Class.forName(TRANSFORMER);
		Constructor<?> c = klass.getDeclaredConstructor();
		c.setAccessible(true);
		transformer = c.newInstance();
		method = klass.getDeclaredMethod("transformClassBytes", String.class, String.class, byte[].class);
		method.setAccessible(true);
	}

	public static boolean isMixinClass(ClassNode node) {
		if (MIXIN.equals(node.name)) {
			return true;
		}
		List<AnnotationNode> annotations = node.invisibleAnnotations;
		return annotations != null && annotations.stream().anyMatch(it -> MIXIN.equals(it.desc));
	}

	@SneakyThrows
	static void setup(List<String> args) {
		Class<?> bootstrapCls = Class.forName(BOOTSTRAP);
		Method m = bootstrapCls.getDeclaredMethod("start");
		m.setAccessible(true);
		m.invoke(null);
		(m = bootstrapCls.getDeclaredMethod("doInit", CommandLineOptions.class))
				.setAccessible(true);
		m.invoke(null, CommandLineOptions.of(args));
		(m = bootstrapCls.getDeclaredMethod("inject"))
				.setAccessible(true);
		m.invoke(null);
		(m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class))
				.setAccessible(true);
		m.invoke(null, MixinEnvironment.Phase.DEFAULT);
	}

	public byte[] transformClass(String name, byte[] bytes) {
		try {
			return (byte[]) method.invoke(transformer, name, name, bytes);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}
