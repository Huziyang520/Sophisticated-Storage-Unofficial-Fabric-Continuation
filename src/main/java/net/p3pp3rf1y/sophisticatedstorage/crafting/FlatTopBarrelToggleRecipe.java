package net.p3pp3rf1y.sophisticatedstorage.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.item.BarrelBlockItem;

public class FlatTopBarrelToggleRecipe extends CustomRecipe {
	public static final RecipeSerializer<FlatTopBarrelToggleRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(FlatTopBarrelToggleRecipe::new), StreamCodec.unit(new FlatTopBarrelToggleRecipe()));

	public FlatTopBarrelToggleRecipe() {
		super();
	}

	@Override
	public CraftingBookCategory category() {
		return CraftingBookCategory.MISC;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean barrelFound = false;
		for (int i = 0; i < input.size(); i++) {
			ItemStack item = input.getItem(i);
			if (item.isEmpty()) {
				continue;
			}

			if (!barrelFound && item.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BarrelBlock) {
				barrelFound = true;
			} else {
				return false;
			}
		}

		return barrelFound;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		for (int i = 0; i < input.size(); i++) {
			ItemStack item = input.getItem(i);
			if (item.isEmpty()) {
				continue;
			}

			if (item.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BarrelBlock) {
				ItemStack result = item.copy();
				result.setCount(1);
				BarrelBlockItem.toggleFlatTop(result);
				return result;
			}
		}

		return ItemStack.EMPTY;
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return FlatTopBarrelToggleRecipe.SERIALIZER;
	}
}
