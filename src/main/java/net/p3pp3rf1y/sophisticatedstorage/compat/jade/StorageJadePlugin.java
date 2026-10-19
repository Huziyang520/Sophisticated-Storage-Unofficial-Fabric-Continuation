package net.p3pp3rf1y.sophisticatedstorage.compat.jade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration: makes Jade name wooden storage blocks after the <b>item</b> that picking them would give.
 * <p>
 * The block names are translatable strings with two arguments ("%s%sBarrel", wood name + space), so
 * {@code Block#getName()} - which is what Jade's object-name provider falls back to - renders them <i>without</i> the
 * wood ("Limited Copper Barrel II"). The item, on the other hand, carries the wood type as a data component and
 * resolves its name through {@code WoodStorageBlockItem#getName}, so the hand and the tooltip disagree.
 * <p>
 * Jade has no "name provider" hook; the way to get the item name is to mark the block as a "pick" target
 * ({@code TargetOperationRepository#pick}): {@code ObjectNameProvider#appendTooltip} then uses
 * {@code BlockAccessor#getPickedResult()}, which is {@code BlockState#getCloneItemStack} →
 * {@code WoodStorageBlockBase#getCloneItemStack} and therefore carries the wood type and the colours of the block
 * entity. This is the 26.3 replacement of the old {@code IWailaClientRegistration#usePickedResult}, which no longer
 * exists in the Jade API.
 * <p>
 * Registered through the {@code jade} entrypoint in {@code fabric.mod.json}; when Jade is not installed the entrypoint
 * is never looked up and this class is never loaded.
 */
@WailaPlugin
public class StorageJadePlugin implements IWailaPlugin {
	@Override
	public void register(IWailaCommonRegistration registration) {
		// both lists are ordinary java lists, so this does not touch the block registry during init
		ModBlocks.ALL_WOODSTORAGES.forEach(blockSupplier -> pickByName(registration, blockSupplier.get()));
	}

	private static void pickByName(IWailaCommonRegistration registration, Block block) {
		ResourceKey<Block> key = BuiltInRegistries.BLOCK.getResourceKey(block).orElse(null);
		if (key != null) {
			// no-op on a dedicated server (Jade only shows the picked name on the client)
			registration.blockOperations().pick(key);
		}
	}
}
