package net.p3pp3rf1y.sophisticatedstorage.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedcore.Config;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockBase;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.init.ModDataComponents;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * JEI integration for the storage blocks.
 * <p>
 * Two separate jobs:
 * <ul>
 *     <li>Hides the internal slot placeholder from JEI. {@code inaccessible_slot} is a real registry entry (the GUI
 *     draws it as the icon of a locked slot), so JEI lists it like any other item and the player sees a technical entry
 *     with no use. It stays registered - removing it breaks existing worlds - and is only taken out of the ingredient
 *     list.</li>
 *     <li>Makes the wood/colour variants of the storage blocks visible. All barrels (and chests/shulker boxes) share
 *     one item id and only differ in data components, so without a subtype interpreter JEI collapses every variant of
 *     a block into a single ingredient - the first one it meets, which is why only the acacia barrel/chest showed up
 *     although the creative tab and the recipes contain all eleven wood types. The interpreter alone is not enough
 *     either: JEI collects ingredients from the item registry and from recipe outputs, so the wood variants are also
 *     added as extra ingredients.</li>
 * </ul>
 */
@JeiPlugin
public class StorageJeiPlugin implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "default");
	}

	/**
	 * Subtype identity of a storage block item: wood type plus the two tint colours (that keeps dyed variants apart as
	 * well). JEI's component based interpreter is enough here, no custom interpreter needed.
	 */
	@Override
	public void registerItemSubtypes(ISubtypeRegistration registration) {
		for (Block block : storageBlocks()) {
			if (block.asItem() instanceof BlockItem blockItem) {
				registration.registerFromDataComponentTypes(blockItem,
						ModDataComponents.WOOD_TYPE.get(),
						ModCoreDataComponents.MAIN_COLOR.get(),
						ModCoreDataComponents.ACCENT_COLOR.get());
			}
		}
	}

	/** Adds every wood variant as an ingredient so all of them can be searched and shown, not just the recipe outputs. */
	@Override
	public void registerExtraIngredients(IExtraIngredientRegistration registration) {
		List<ItemStack> variants = new ArrayList<>();
		// only wooden storages have a wood type - shulker boxes are dyed, not wood typed
		for (Supplier<? extends WoodStorageBlockBase> supplier : ModBlocks.ALL_WOODSTORAGES) {
			Block block = supplier.get();
			if (!Config.COMMON.enabledItems.isItemEnabled(block.asItem())) {
				continue;
			}
			for (WoodType woodType : WoodStorageBlockBase.CUSTOM_TEXTURE_WOOD_TYPES.keySet()) {
				variants.add(WoodStorageBlockItem.setWoodType(new ItemStack(block), woodType));
			}
		}
		registration.addExtraItemStacks(variants);
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
				List.of(new ItemStack(ModItems.INACCESSIBLE_SLOT.get())));
	}

	/** Every block whose item can differ only by data components (barrels, chests, shulker boxes). */
	private static List<Block> storageBlocks() {
		List<Block> blocks = new ArrayList<>();
		ModBlocks.ALL_STORAGECONTAINERS.forEach(supplier -> blocks.add(supplier.get()));
		return blocks;
	}
}
