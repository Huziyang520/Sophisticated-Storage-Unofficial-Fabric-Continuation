package net.p3pp3rf1y.sophisticatedstorage.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.crafting.IWrapperRecipe;
import net.p3pp3rf1y.sophisticatedcore.crafting.RecipeWrapperSerializer;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.world.item.BlockItem;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ShulkerBoxFromVanillaShapelessRecipe implements CraftingRecipe, IWrapperRecipe<ShapelessRecipe> {
	private final ShapelessRecipe compose;

	public ShulkerBoxFromVanillaShapelessRecipe(ShapelessRecipe compose) {
		this.compose = compose;
	}

	@Override
	public ShapelessRecipe getCompose() {
		return compose;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return compose.matches(input, level) && getVanillaShulkerBox(input).map(storage -> !(storage.getItem() instanceof WoodStorageBlockItem) || !WoodStorageBlockItem.isPacked(storage)).orElse(false);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack upgradedStorage = compose.assemble(input);
		getVanillaShulkerBox(input).ifPresent(vanillaShulkerBox -> {
			@Nullable Storage<ItemVariant> itemCap = ContainerItemContext.withConstant(vanillaShulkerBox).find(ItemStorage.ITEM);
			if (itemCap != null) {
				StackStorageWrapper wrapper = StackStorageWrapper.fromStack(upgradedStorage);
				InventoryHelper.iterate(itemCap, stack -> {
					if (!stack.isEmpty()) {
						wrapper.getInventoryHandler().insertItem(stack, false);
					}
				});
			}
		});
		return upgradedStorage;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	private Optional<ItemStack> getVanillaShulkerBox(CraftingInput input) {
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack slotStack = input.getItem(slot);
			if (slotStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
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
		return (RecipeSerializer<? extends CraftingRecipe>) ModBlocks.SHULKER_BOX_FROM_VANILLA_SHAPELESS_RECIPE_SERIALIZER.get();
	}

	public static class Serializer {
		private Serializer() {
		}

		public static RecipeSerializer<ShulkerBoxFromVanillaShapelessRecipe> create() {
			return RecipeWrapperSerializer.create(ShulkerBoxFromVanillaShapelessRecipe::new, ShapelessRecipe.SERIALIZER);
		}
	}
}
