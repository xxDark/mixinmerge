package dev.xdark.mixinmerge;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import lombok.SneakyThrows;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class MixinMerger {

	// temporarily store side argument
	public static String side;

	@SneakyThrows
	public static void main(String[] args) {
		OptionParser parser = new OptionParser();
		parser.allowsUnrecognizedOptions();
		OptionSpec<File> inputSpec = parser.accepts("input").withRequiredArg().ofType(File.class).required();
		OptionSpec<File> outputSpec = parser.accepts("output").withRequiredArg().ofType(File.class).required();
		OptionSpec<String> sideSpec = parser.accepts("side").withRequiredArg().ofType(String.class);
		OptionSpec<String> nonOptionsSpec = parser.nonOptions();
		OptionSet options = parser.parse(args);
		side = options.valueOf(sideSpec);
		File[] files = options.valuesOf(inputSpec).stream()
				.flatMap(f -> f.isDirectory() ? Arrays.stream(f.listFiles()) : Stream.of(f))
				.toArray(File[]::new);

		// I decided to use this tool 3 years (!) later on a newer JDK, and got this
		// in my face:
		// Exception in thread "main" java.lang.reflect.InaccessibleObjectException:
		// Unable to make protected void java.net.URLClassLoader.addURL(java.net.URL) accessible:
		// module java.base does not "opens java.net" to unnamed module @306a30c7
		// Look, I just want to run the app from the terminal and not write any scripts to do so.
		boolean j9;
		try {
			Class.forName("java.lang.Module");
			j9 = true;
		} catch (ClassNotFoundException ignored) {
			j9 = false;
		}
		if (j9) {
			Deencapsulation.deencapsulate(MixinMerger.class);
		}

		ClassLoader loader = MixinMerger.class.getClassLoader();
		Object ucp;
		Method addURL;
		searchField: {
			Class<?> cls = loader.getClass();
			do {
				try {
					Field ucpField = cls.getDeclaredField("ucp");
					ucpField.setAccessible(true);
					ucp = ucpField.get(loader);
					addURL = ucp.getClass().getDeclaredMethod("addURL", URL.class);
					addURL.setAccessible(true);
					break searchField;
				} catch (NoSuchFieldException ignored) {
				}
			} while ((cls = cls.getSuperclass()) != ClassLoader.class);
			System.err.println("'ucp' field not found");
			System.exit(1);
			return;
		}
		for (File f : files) {
			addURL.invoke(ucp, f.toURI().toURL());
		}
		File output = options.valueOf(outputSpec);
		if (!output.isDirectory() && !output.mkdirs()) {
			System.err.printf("Output directory '%s' couldn't be created%n", output);
			System.exit(1);
		}
		File tmp = new File("mixinmerge.tmp");
		if (tmp.isFile() && !tmp.delete()) {
			System.err.println("'mixinmerge.tmp' couldn't be deleted");
			System.exit(1);
		}
		MixinHooks.setup(options.valuesOf(nonOptionsSpec));
		MixinHooks mixinHooks = new MixinHooks();
		byte[] bytes = new byte[16384];
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (File file : files) {
			if (!tmp.createNewFile()) {
				System.err.printf("File %s couldn't be deleted%n", tmp);
				System.exit(1);
			}
			try (ZipInputStream in = new ZipInputStream(new FileInputStream(file));
			     ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmp))) {
				ZipEntry entry;
				while ((entry = in.getNextEntry()) != null) {
					String name = entry.getName();
					baos.reset();
					int r;
					while ((r = in.read(bytes)) != -1) {
						baos.write(bytes, 0, r);
					}
					byte[] data = baos.toByteArray();
					if (name.endsWith(".class")) {
						String normal = name.replace('/', '.').substring(0, name.length() - 6);
						ClassNode node = new ClassNode();
						new ClassReader(data).accept(node, ClassReader.SKIP_CODE);
						if (!MixinHooks.isMixinClass(node)) {
							data = mixinHooks.transformClass(normal, data);
						}
					}
					out.putNextEntry(cloneEntryWithContent(entry, data));
					out.write(data);
					out.closeEntry();
					in.closeEntry();
				}
			}
			File out = new File(output, file.getName());
			if (out.isFile() && !out.delete()) {
				System.err.printf("Couldn't delete %s%n", out);
				System.exit(1);
			}
			if (!tmp.renameTo(out)) {
				System.err.printf("Couldn't move %s to %s%n", tmp, out);
				System.exit(1);
			}
		}
	}

	private static ZipEntry cloneEntryWithContent(ZipEntry ze, byte[] content) {
		ZipEntry clone = (ZipEntry) ze.clone();
		clone.setSize(content.length);
		CRC32 crc32 = new CRC32();
		crc32.update(content, 0, content.length);
		clone.setCrc(crc32.getValue());
		return clone;
	}
}
