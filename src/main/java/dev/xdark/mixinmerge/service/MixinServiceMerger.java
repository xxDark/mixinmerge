package dev.xdark.mixinmerge.service;

import dev.xdark.mixinmerge.MixinMerger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.Constants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MixinServiceMerger extends MixinServiceAbstract
    implements IClassProvider,
        IClassBytecodeProvider,
        ITransformerProvider,
        IGlobalPropertyService,
        IMixinAuditTrail {

  static final IPropertyKey SIDE = new Key("side");
  private static final Logger LOGGER = LogManager.getLogger("MixinServiceMerger");

  private final Map<IPropertyKey, Object> properties = new HashMap<>();

  @Override
  public void prepare() {
    String side = MixinMerger.side;
    MixinMerger.side = null;
    if (side != null) {
      if ((side = side.toLowerCase(Locale.ENGLISH)).contains("client")) {
        setProperty(SIDE, Constants.SIDE_CLIENT);
      } else if (side.contains("server")) {
        setProperty(SIDE, Constants.SIDE_SERVER);
      } else {
        setProperty(SIDE, Constants.SIDE_UNKNOWN);
      }
    }
  }

  @Override
  public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
    return getClassNode(name, false);
  }

  @Override
  public ClassNode getClassNode(String name, boolean runTransformers)
      throws ClassNotFoundException, IOException {
    ClassNode node;
    try (InputStream in = getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
      if (in == null) {
        throw new ClassNotFoundException(name);
      }
      node = new ClassNode();
      new ClassReader(in).accept(node, 0);
    }
    return node;
  }

  @Override
  public URL[] getClassPath() {
    return ((URLClassLoader) getClass().getClassLoader()).getURLs();
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    return findClass(name, false);
  }

  @Override
  public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
    if (name.startsWith("org.spongepowered.")) {
      throw new ClassNotFoundException(name);
    }
    Class<?> c = Class.forName(name, initialize, getClassLoader());
    if (c == Mixin.class) {
      throw new ClassNotFoundException(name);
    }
    if (c.getDeclaredAnnotation(Mixin.class) != null) {
      throw new ClassNotFoundException(name);
    }
    return c;
  }

  @Override
  public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
    return Class.forName(name, initialize, MixinServiceMerger.class.getClassLoader());
  }

  @Override
  public String getName() {
    return "MixinServiceMerger";
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public IClassProvider getClassProvider() {
    return this;
  }

  @Override
  public IClassBytecodeProvider getBytecodeProvider() {
    return this;
  }

  @Override
  public ITransformerProvider getTransformerProvider() {
    return this;
  }

  @Override
  public IClassTracker getClassTracker() {
    return null;
  }

  @Override
  public IMixinAuditTrail getAuditTrail() {
    return this;
  }

  @Override
  public Collection<String> getPlatformAgents() {
    return Collections.singletonList(MixinPlatformAgentMerger.class.getName());
  }

  @Override
  public IContainerHandle getPrimaryContainer() {
    try {
      URI uri = this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
      return new ContainerHandleURI(uri);
    } catch (URISyntaxException ex) {
      ex.printStackTrace();
    }
    return new ContainerHandleVirtual(this.getName());
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    return getClassLoader().getResourceAsStream(name);
  }

  @Override
  public Collection<ITransformer> getTransformers() {
    return Collections.emptyList();
  }

  @Override
  public Collection<ITransformer> getDelegatedTransformers() {
    return Collections.emptyList();
  }

  @Override
  public Collection<IContainerHandle> getMixinContainers() {
    List<IContainerHandle> handles = new ArrayList<>();
    for (URL url : getClassPath()) {
      URI uri;
      try {
        uri = url.toURI();
      } catch (URISyntaxException ignored) {
        continue;
      }
      Manifest manifest = null;
      try (ZipFile zf = new ZipFile(new File(uri))) {
        ZipEntry entry = zf.getEntry("META-INF/MANIFEST.MF");
        if (entry != null) {
          try (InputStream in = zf.getInputStream(entry)) {
            manifest = new Manifest(in);
          }
        }
      } catch (IOException ignored) {
      }
      if (manifest != null && manifest.getMainAttributes().getValue("TweakClass") != null) {
        handles.add(new ContainerHandleURI(uri));
      }
    }
    if (!handles.isEmpty()) {
      LOGGER.info("Found {} mixin containers on classpath", handles.size());
    }
    return handles;
  }

  @Override
  public void addTransformerExclusion(String name) {}

  private URLClassLoader getClassLoader() {
    return (URLClassLoader) getClass().getClassLoader();
  }

  @Override
  public IPropertyKey resolveKey(String name) {
    return new Key(name);
  }

  @Override
  public <T> T getProperty(IPropertyKey key) {
    return (T) properties.get(key);
  }

  @Override
  public void setProperty(IPropertyKey key, Object value) {
    properties.put(key, value);
  }

  @Override
  public <T> T getProperty(IPropertyKey key, T defaultValue) {
    return (T) properties.getOrDefault(key, defaultValue);
  }

  @Override
  public String getPropertyString(IPropertyKey key, String defaultValue) {
    Object v = properties.get(key);
    if (v != null) return v.toString();
    return defaultValue;
  }

  @Override
  public void onApply(String className, String mixinName) {
    LOGGER.trace("Applied " + mixinName + " for " + className);
  }

  @Override
  public void onPostProcess(String className) {}

  @Override
  public void onGenerate(String className, String generatorName) {}

  private static final class Key implements IPropertyKey {

    private final String name;

    Key(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return name.equals(((Key) o).name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }
}
