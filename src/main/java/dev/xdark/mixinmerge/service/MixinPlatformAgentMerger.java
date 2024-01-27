package dev.xdark.mixinmerge.service;

import org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.service.MixinService;

import java.util.Collection;

public class MixinPlatformAgentMerger extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {

	@Override
	public void init() {
	}

	@Override
	public String getSideName() {
		return MixinService.getGlobalPropertyService().getProperty(MixinServiceMerger.SIDE);
	}

	@Override
	public Collection<IContainerHandle> getMixinContainers() {
		return null;
	}
}
