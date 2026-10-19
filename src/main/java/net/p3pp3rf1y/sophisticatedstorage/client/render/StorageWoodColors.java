package net.p3pp3rf1y.sophisticatedstorage.client.render;

import net.minecraft.world.level.block.state.properties.WoodType;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The wood type colours shared by the block colour sources ({@code client/init/StorageTints}) and the item tint
 * sources ({@link StorageItemTints}).
 * <p>
 * The barrel models and the item model are shared by every wood type, so an undyed barrel has to be tinted with the
 * colour of its own wood texture instead of being left white. The values are the mean RGB of
 * {@code sophisticatedstorage:block/<wood>_barrel_side} measured from the mod's own textures, divided by the average
 * brightness of the tintable overlay textures so the result matches the brightness of the original wood texture.
 */
public final class StorageWoodColors {
	/**
	 * Average brightness of the tintable overlay textures (barrel_side_tintable_main / _accent are near white
	 * because they are meant to be multiplied by a dye colour). Wood colours are divided by this so a tinted
	 * barrel ends up at the brightness of the wood's own barrel texture instead of 16% darker.
	 */
	private static final float OVERLAY_BRIGHTNESS = 215F / 255F;

	private static final List<WoodColor> WOOD_COLORS = List.of(
			new WoodColor(WoodType.OAK, 134, 109, 72),
			new WoodColor(WoodType.SPRUCE, 108, 81, 50),
			new WoodColor(WoodType.BIRCH, 147, 139, 100),
			new WoodColor(WoodType.JUNGLE, 132, 100, 78),
			new WoodColor(WoodType.ACACIA, 134, 79, 49),
			new WoodColor(WoodType.DARK_OAK, 63, 45, 26),
			new WoodColor(WoodType.MANGROVE, 88, 48, 39),
			new WoodColor(WoodType.CHERRY, 146, 108, 105),
			new WoodColor(WoodType.BAMBOO, 155, 139, 78),
			new WoodColor(WoodType.CRIMSON, 96, 49, 67),
			new WoodColor(WoodType.WARPED, 48, 100, 100));

	private StorageWoodColors() {
	}

	/**
	 * Tint colour of the given wood type, or {@code -1} (untinted) when the wood type is unknown / absent.
	 */
	public static int tintFor(@Nullable WoodType woodType) {
		if (woodType == null) {
			return -1;
		}
		for (WoodColor woodColor : WOOD_COLORS) {
			if (woodColor.woodType().equals(woodType)) {
				return woodColor.tint();
			}
		}
		return -1;
	}

	private record WoodColor(WoodType woodType, int red, int green, int blue) {
		int tint() {
			return (compensate(red) << 16) | (compensate(green) << 8) | compensate(blue);
		}

		private static int compensate(int channel) {
			return Math.min(255, Math.round(channel / OVERLAY_BRIGHTNESS));
		}
	}
}
