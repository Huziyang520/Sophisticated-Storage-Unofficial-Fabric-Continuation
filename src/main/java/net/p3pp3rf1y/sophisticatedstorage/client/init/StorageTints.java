package net.p3pp3rf1y.sophisticatedstorage.client.init;

import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockBase;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.client.render.StorageItemTints;
import net.p3pp3rf1y.sophisticatedstorage.client.render.StorageWoodColors;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;

import org.jetbrains.annotations.Nullable;

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
			return stateWoodColor(state);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			StorageBlockEntity be = WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).orElse(null);
			// First frame(s) after placement: the client block entity is not there yet. The wood type is mirrored
			// into the block state, which arrives together with the block, so the barrel is already tinted.
			int tint = StorageWoodColors.resolve(be == null ? -1 : be.getStorageWrapper().getMainColor(), woodTypeOf(be));
			return tint != -1 ? tint : stateWoodColor(state);
		}
	};

	private static final BlockTintSource ACCENT_COLOR = new BlockTintSource() {
		@Override
		public int color(BlockState state) {
			return stateWoodColor(state);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			StorageBlockEntity be = WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).orElse(null);
			int tint = StorageWoodColors.resolve(be == null ? -1 : be.getStorageWrapper().getAccentColor(), woodTypeOf(be));
			return tint != -1 ? tint : stateWoodColor(state);
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
		// 26.3 has no registry for ItemTintSource types, so the barrel item tints are registered by id instead
		StorageItemTints.register();
	}

	/**
	 * Wood type of a barrel that is not dyed, as stored on the block entity.
	 * <p>
	 * The block entity copy is the authority for an existing block: it is written from the placed item
	 * ({@code WoodStorageBlockBase#setPlacedBy}) and only ever holds a wood type that really was there. The
	 * {@code WOOD} block state is <b>not</b> equivalent - it defaults to index 0 (acacia) and keeps that value for
	 * every barrel whose item had no wood component (a dyed barrel) and for blocks that were placed before the
	 * property existed. Preferring the state therefore painted such barrels in acacia, which is what made the whole
	 * mod look like it used acacia as its base wood. The state is still the fallback for the first frame(s), when the
	 * client block entity has not received its data packet yet (see {@link #stateWoodColor}).
	 */
	@Nullable
	private static WoodType woodTypeOf(@Nullable StorageBlockEntity be) {
		return be instanceof WoodStorageBlockEntity woodBe ? woodBe.getWoodType().orElse(null) : null;
	}

	/**
	 * Wood colour taken from the block state - available on the very first frame, unlike the block entity data.
	 * Only barrels carry {@link BarrelBlock#WOOD}; other storage blocks (chests, shulker boxes) stay untinted here.
	 */
	private static int stateWoodColor(BlockState state) {
		if (!state.hasProperty(BarrelBlock.WOOD)) {
			return -1;
		}
		return StorageWoodColors.tintFor(WoodStorageBlockBase.woodTypeAt(state.getValue(BarrelBlock.WOOD)));
	}
}
