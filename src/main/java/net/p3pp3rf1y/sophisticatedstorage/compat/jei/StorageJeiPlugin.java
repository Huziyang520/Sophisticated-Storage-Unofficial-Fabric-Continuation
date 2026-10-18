package net.p3pp3rf1y.sophisticatedstorage.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;

import java.util.List;

/**
 * Hides the internal slot placeholder from JEI.
 * <p>
 * {@code inaccessible_slot} is a real registry entry (the GUI draws it as the icon of a locked slot), so JEI lists it
 * like any other item and the player sees a technical entry with no use. It stays registered - removing it breaks
 * existing worlds - and is only taken out of the ingredient list.
 */
@JeiPlugin
public class StorageJeiPlugin implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "default");
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
				List.of(new ItemStack(ModItems.INACCESSIBLE_SLOT.get())));
	}
}
