package net.p3pp3rf1y.sophisticatedstorage.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.crafting.IWrapperRecipe;
import net.p3pp3rf1y.sophisticatedcore.crafting.RecipeWrapperSerializer;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.item.ChestBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;

import java.util.List;
import java.util.Optional;

public class StorageTierUpgradeRecipe implements CraftingRecipe, IWrapperRecipe<ShapedRecipe> {
	private final ShapedRecipe compose;

	public StorageTierUpgradeRecipe(ShapedRecipe compose) {
		this.compose = compose;
	}

	@Override
	public ShapedRecipe getCompose() {
		return compose;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return compose.matches(input, level) && getOriginalStorage(input).isPresent();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack upgradedStorage = compose.assemble(input);
		getOriginalStorage(input).ifPresent(originalStorage -> {
			upgradedStorage.applyComponents(originalStorage.getComponents());
		});
		if (upgradedStorage.sophisticatedCore_has(ModCoreDataComponents.STORAGE_UUID)) {
			StackStorageWrapper storageWrapper = StackStorageWrapper.fromStack(upgradedStorage);
			StorageBlockItem.setNumberOfInventorySlots(upgradedStorage, storageWrapper.getDefaultNumberOfInventorySlots());
			StorageBlockItem.setNumberOfUpgradeSlots(upgradedStorage, storageWrapper.getDefaultNumberOfUpgradeSlots());
		}
		return upgradedStorage;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	private Optional<ItemStack> getOriginalStorage(CraftingInput input) {
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack slotStack = input.getItem(slot);
			if (slotStack.getItem() instanceof StorageBlockItem
					&& (!(slotStack.getItem() instanceof ChestBlockItem) || !ChestBlockItem.isDoubleChest(slotStack))) {
				return Optional.of(slotStack);
			}
		}

		return Optional.empty();
	}

	@Override
	public String group() {
		return compose.group();
	}

	@Override
	public boolean showNotification() {
		return compose.showNotification();
	}

	@Override
	public CraftingBookCategory category() {
		return compose.category();
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return compose.recipeBookCategory();
	}

	@Override
	public PlacementInfo placementInfo() {
		return compose.placementInfo();
	}

	@Override
	public List<RecipeDisplay> display() {
		return compose.display();
	}

	@Override
	public RecipeType<CraftingRecipe> getType() {
		return compose.getType();
	}

	@Override
	public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
		return (RecipeSerializer<? extends CraftingRecipe>) ModBlocks.STORAGE_TIER_UPGRADE_RECIPE_SERIALIZER.get();
	}

	public static class Serializer {
		private Serializer() {
		}

		public static RecipeSerializer<StorageTierUpgradeRecipe> create() {
			return RecipeWrapperSerializer.create(StorageTierUpgradeRecipe::new, ShapedRecipe.SERIALIZER);
		}
	}
}
