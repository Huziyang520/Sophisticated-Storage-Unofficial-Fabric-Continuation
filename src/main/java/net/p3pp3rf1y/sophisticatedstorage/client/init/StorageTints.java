package net.p3pp3rf1y.sophisticatedstorage.client.init;

import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Block colour sources for the storage blocks.
 * <p>
 * 26.3 replaced the old {@code ColorProviderRegistry} (block state + tint index callback) with per block lists of
 * {@link BlockTintSource}; the position in the list <b>is</b> the tint index written on the model face, and the
 * index is only looked up while {@code index < list.size()} ({@code BlockColors#getTintSource} returns null
 * otherwise, which the renderer resolves to "untinted"). The upstream models therefore keep their tint indexes
 * 1000 (main colour) and 1001 (accent colour) and this class registers a list that is addressable up to 1001.
 * <p>
 * On the barrel models only the recolourable overlay parts carry a tint index (upstream
 * {@code ModBlockColors#getBarrelTintColor}: 1000 = {@code getMainColor()}, 1001 = {@code getAccentColor()}).
 * An undyed barrel has no colour set, so the wood type colour is used instead - that is what lets one static
 * model show every wood type after the dynamic per wood part models were dropped.
 */
public final class StorageTints {
	/** tint index of the main colour faces of a barrel model */
	private static final int MAIN_TINT_INDEX = 1000;
	/** tint index of the accent colour faces of a barrel model */
	private static final int ACCENT_TINT_INDEX = 1001;
	private static final int BARREL_TINT_COUNT = ACCENT_TINT_INDEX + 1;

	/**
	 * Average brightness of the tintable overlay textures (barrel_side_tintable_main / _accent are near white
	 * because they are meant to be multiplied by a dye colour). Wood colours are divided by this so a tinted
	 * barrel ends up at the brightness of the wood's own barrel texture instead of 16% darker.
	 */
	private static final float OVERLAY_BRIGHTNESS = 215F / 255F;

	/**
	 * Mean RGB of sophisticatedstorage:block/&lt;wood&gt;_barrel_side, measured from the mod's own textures.
	 * Used as the "not dyed" tint so the shared static model still renders each wood type in its own colour.
	 */
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

	private static final BlockTintSource NO_TINT = new BlockTintSource() {
		@Override
		public int color(BlockState state) {
			return -1;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			return -1;
		}
	};

	private static final BlockTintSource MAIN_COLOR = new BlockTintSource() {
		@Override
		public int color(BlockState state) {
			return -1;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			StorageBlockEntity be = WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).orElse(null);
			if (be == null) {
				return -1;
			}
			int mainColor = be.getStorageWrapper().getMainColor();
			return mainColor != -1 ? mainColor : woodColor(be);
		}
	};

	private static final BlockTintSource ACCENT_COLOR = new BlockTintSource() {
		@Override
		public int color(BlockState state) {
			return -1;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			StorageBlockEntity be = WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).orElse(null);
			if (be == null) {
				return -1;
			}
			int accentColor = be.getStorageWrapper().getAccentColor();
			return accentColor != -1 ? accentColor : woodColor(be);
		}
	};

	/** index == tint index of the barrel models; only 1000 and 1001 are ever hit by them */
	private static final List<BlockTintSource> BARREL_TINTS = new AbstractList<>() {
		@Override
		public BlockTintSource get(int index) {
			if (index == MAIN_TINT_INDEX) {
				return MAIN_COLOR;
			}
			if (index == ACCENT_TINT_INDEX) {
				return ACCENT_COLOR;
			}
			return NO_TINT;
		}

		@Override
		public int size() {
			return BARREL_TINT_COUNT;
		}
	};

	private StorageTints() {
	}

	public static void register() {
		BlockColorRegistry.register(BARREL_TINTS,
				ModBlocks.ALL_BARRELS.stream().map(Supplier::get).toArray(Block[]::new));
		// chests and shulker boxes only ever tint their particle face (upstream getChestShulkerBoxColor, index 0)
		BlockColorRegistry.register(List.of(MAIN_COLOR),
				ModBlocks.CHESTS.stream().map(Supplier::get).toArray(Block[]::new));
		BlockColorRegistry.register(List.of(MAIN_COLOR),
				ModBlocks.SHULKER_BOXES.stream().map(Supplier::get).toArray(Block[]::new));
	}

	/**
	 * Colour of the barrel's wood type, or -1 (untinted) when the block has no wood type or the block entity is
	 * not loaded yet.
	 */
	private static int woodColor(StorageBlockEntity be) {
		if (!(be instanceof WoodStorageBlockEntity woodBe)) {
			return -1;
		}
		WoodType woodType = woodBe.getWoodType().orElse(null);
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
