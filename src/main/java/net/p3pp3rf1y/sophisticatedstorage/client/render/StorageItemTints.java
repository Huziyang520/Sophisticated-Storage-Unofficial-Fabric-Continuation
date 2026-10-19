package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.ITintableBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

import org.jetbrains.annotations.Nullable;

/**
 * Item tint sources for the barrel item model - the item side counterpart of the barrel block colour sources in
 * {@code client/init/StorageTints}.
 * <p>
 * 26.3 has no public registration API for {@link ItemTintSource} types; the item definition
 * ({@code assets/sophisticatedstorage/items/<id>.json}) names them by id and {@link ItemTintSources#ID_MAPPER} is a
 * late bound id mapper, so putting entries into it before the item models are decoded is enough (the field is opened
 * up through the access widener, same trick the backpack module uses).
 */
public final class StorageItemTints {
	/** main colour of an undyed barrel = wood colour, or the main colour component when the barrel is dyed */
	public static final Identifier MAIN_COLOR_ID = SophisticatedStorage.getRL("barrel_main");
	/** accent colour of an undyed barrel = wood colour, or the accent colour component when the barrel is dyed */
	public static final Identifier ACCENT_COLOR_ID = SophisticatedStorage.getRL("barrel_accent");

	private static final ItemTintSource MAIN_COLOR = new BarrelColorTint(true);
	private static final ItemTintSource ACCENT_COLOR = new BarrelColorTint(false);

	/**
	 * Tint source that always reports "untinted". Only used by {@link BarrelItemModel} to fill the unused slots of
	 * the padded tint list (see there).
	 */
	static final ItemTintSource NO_TINT = new ItemTintSource() {
		@Override
		public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
			return -1;
		}

		@Override
		public MapCodec<? extends ItemTintSource> type() {
			return MapCodec.unit(this);
		}
	};

	private StorageItemTints() {
	}

	/** Registers both tint sources; called from {@code StorageTints#register} during client initialisation. */
	public static void register() {
		ItemTintSources.ID_MAPPER.put(MAIN_COLOR_ID, MapCodec.unit(MAIN_COLOR));
		ItemTintSources.ID_MAPPER.put(ACCENT_COLOR_ID, MapCodec.unit(ACCENT_COLOR));
	}

	/**
	 * Tints the main texture part or the accent part of a barrel item: the colour component wins, an undyed barrel
	 * falls back to the colour of its wood type (which is how the shared item model shows every wood type).
	 */
	private record BarrelColorTint(boolean mainColor) implements ItemTintSource {
		@Override
		public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
			if (stack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
				int color = mainColor ? tintableBlockItem.getMainColor(stack).orElse(-1)
						: tintableBlockItem.getAccentColor(stack).orElse(-1);
				if (color != -1) {
					return color;
				}
			}
			WoodType woodType = WoodStorageBlockItem.getWoodType(stack).orElse(null);
			return StorageWoodColors.tintFor(woodType);
		}

		@Override
		public MapCodec<? extends ItemTintSource> type() {
			return MapCodec.unit(this);
		}
	}
}
