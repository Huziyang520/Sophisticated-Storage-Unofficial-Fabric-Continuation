package net.p3pp3rf1y.sophisticatedstorage.client.init;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.p3pp3rf1y.sophisticatedstorage.client.particle.CustomTintTerrainParticle;

public class ModParticles {
	private ModParticles() {}

	@SuppressWarnings("unused") // need this to register the event correctly
	public static void registerProviders() {
		ParticleProviderRegistry.getInstance().register(net.p3pp3rf1y.sophisticatedstorage.init.ModParticles.TERRAIN_PARTICLE.get(), spriteSet -> new CustomTintTerrainParticle.Factory());
	}
}
