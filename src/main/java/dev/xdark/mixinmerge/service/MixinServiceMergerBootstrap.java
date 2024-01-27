package dev.xdark.mixinmerge.service;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public final class MixinServiceMergerBootstrap implements IMixinServiceBootstrap {

	@Override
	public String getName() {
		return "MixinMerger";
	}

	@Override
	public String getServiceClassName() {
		return MixinServiceMerger.class.getName();
	}

	@Override
	public void bootstrap() {
	}
}
