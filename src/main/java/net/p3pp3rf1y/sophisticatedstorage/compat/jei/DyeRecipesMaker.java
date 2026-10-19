package net.p3pp3rf1y.sophisticatedstorage.compat.jei;

import mezz.jei.api.recipe.vanilla.IJeiShapedRecipeBuilder;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.ItemStackTemplate;
import net.p3pp3rf1y.sophisticatedcore.Config;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.ITintableBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the "storage + dye" recipes that JEI shows.
 * <p>
 * The real recipe is a {@code sophisticatedstorage:storage_dye} custom recipe: it has no ingredients in its json (it
 * inspects the grid itself), and custom recipes are {@code isSpecial()}, so JEI skips them entirely. That is why dyed
 * barrels had no recipe page at all even once the recipe itself matched. The entries here are display-only:
 * one {@code any storage item + dye -> dyed storage item} page per colour, which is exactly what the custom recipe does
 * with a single dye stack next to the storage block.
 */
public final class DyeRecipesMaker {
	private DyeRecipesMaker() {
	}

	public static List<RecipeHolder<CraftingRecipe>> getRecipes(IVanillaRecipeFactory recipeFactory) {
		List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();

		for (DyeColor color : DyeColor.values()) {
			Item dye = Items.DYE.pick(color);
			for (var itemSupplier : ModBlocks.ALL_STORAGECONTAINER_ITEMS) {
				Item storageItem = itemSupplier.get();
				if (!Config.COMMON.enabledItems.isItemEnabled(storageItem)) {
					continue;
				}

				ItemStack result = new ItemStack(storageItem);
				if (result.getItem() instanceof ITintableBlockItem tintableBlockItem) {
					tintableBlockItem.setMainColor(result, color.getTextureDiffuseColor());
					tintableBlockItem.setAccentColor(result, color.getTextureDiffuseColor());
				}

				IJeiShapedRecipeBuilder builder = recipeFactory.createShapedRecipeBuilder(CraftingBookCategory.MISC,
						new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(result)));
				builder.define('S', Ingredient.of(storageItem));
				builder.define('D', Ingredient.of(dye));
				builder.pattern("SD");

				Identifier id = Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID,
						"dye/" + color.getName().toLowerCase(Locale.ROOT) + "/" + storageItem.builtInRegistryHolder().key().identifier().getPath());
				recipes.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), builder.build()));
			}
		}

		return recipes;
	}
}
