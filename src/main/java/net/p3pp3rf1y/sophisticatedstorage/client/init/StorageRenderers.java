package net.p3pp3rf1y.sophisticatedstorage.client.init;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.ShulkerBoxBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.client.render.BarrelItemModel;
import net.p3pp3rf1y.sophisticatedstorage.client.render.ChestItemRenderer;
import net.p3pp3rf1y.sophisticatedstorage.client.render.ChestRenderer;
import net.p3pp3rf1y.sophisticatedstorage.client.render.LimitedBarrelRenderer;
import net.p3pp3rf1y.sophisticatedstorage.client.render.ShulkerBoxItemRenderer;
import net.p3pp3rf1y.sophisticatedstorage.client.render.ShulkerBoxRenderer;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

/**
 * Client side block entity renderer registration. The chest and the shulker box are drawn entirely by their block
 * entity renderers (their block models are particle-only placeholders), so these registrations are required for the
 * blocks to be visible at all.
 * <p>
 * {@code BlockEntityRenderers.register} is private in vanilla and widened for mods by
 * {@code fabric-transitive-access-wideners-v1} (the same mechanism the existing {@code MenuScreens.register} calls
 * rely on).
 */
public final class StorageRenderers {
	private StorageRenderers() {
	}

	public static void register() {
		BlockEntityRenderers.register(ModBlocks.CHEST_BLOCK_ENTITY_TYPE.get(),
				(BlockEntityRendererProvider<ChestBlockEntity, ChestRenderer.ChestRenderState>) ChestRenderer::new);
		BlockEntityRenderers.register(ModBlocks.SHULKER_BOX_BLOCK_ENTITY_TYPE.get(),
				(BlockEntityRendererProvider<ShulkerBoxBlockEntity, ShulkerBoxRenderer.ShulkerRenderState>) ShulkerBoxRenderer::new);
		BlockEntityRenderers.register(ModBlocks.LIMITED_BARREL_BLOCK_ENTITY_TYPE.get(),
				(BlockEntityRendererProvider<LimitedBarrelBlockEntity, LimitedBarrelRenderer.LimitedBarrelRenderState>) LimitedBarrelRenderer::new);

		// Item model types have no registration API in 26.3 either; both these registers are late bound id mappers that
		// are only queried while an item definition is decoded, so filling them during client init is enough.
		BarrelItemModel.register();
		ChestItemRenderer.register();
		ShulkerBoxItemRenderer.register();
	}
}
