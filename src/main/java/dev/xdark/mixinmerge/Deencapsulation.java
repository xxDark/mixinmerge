package dev.xdark.mixinmerge;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;

@UtilityClass
public class Deencapsulation {

	@SneakyThrows
	public void deencapsulate(Class<?> classBase) {
		MethodHandle METHOD_MODIFIERS;
		{
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			Unsafe unsafe = (Unsafe) field.get(null);
			field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			MethodHandles.publicLookup();
			MethodHandles.Lookup lookup = (MethodHandles.Lookup)
					unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
			METHOD_MODIFIERS = lookup.findSetter(Method.class, "modifiers", Integer.TYPE);
		}

		Method export = Module.class.getDeclaredMethod("implAddOpens", String.class);
		METHOD_MODIFIERS.invokeExact(export, Modifier.PUBLIC);
		HashSet<Module> modules = new HashSet<>();
		Module base = classBase.getModule();
		if (base.getLayer() != null)
			modules.addAll(base.getLayer().modules());
		modules.addAll(ModuleLayer.boot().modules());
		for (ClassLoader cl = classBase.getClassLoader(); cl != null; cl = cl.getParent()) {
			modules.add(cl.getUnnamedModule());
		}
		for (Module module : modules) {
			for (String name : module.getPackages()) {
				export.invoke(module, name);
			}
		}
	}
}
