package net.p3pp3rf1y.sophisticatedstorage.block;

import net.p3pp3rf1y.sophisticatedcore.platform.item.SlottedStackStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;

public abstract class WoodStorageBlockEntity extends StorageBlockEntity {
	private static final String PACKED_TAG = "packed";
	@Nullable
	private WoodType woodType = null;

	private boolean packed = false;

	protected WoodStorageBlockEntity(BlockPos pos, BlockState state, BlockEntityType<? extends StorageBlockEntity> blockEntityType) {
		super(pos, state, blockEntityType);
	}

	@Override
	protected void saveSynchronizedData(CompoundTag tag) {
		super.saveSynchronizedData(tag);
		if (woodType != null) {
			tag.putString("woodType", woodType.name());
		}
		tag.putBoolean(PACKED_TAG, packed);
	}

	public CompoundTag getStorageContentsTag() {
		CompoundTag contents = saveWithoutMetadata(level.registryAccess());
		contents.putBoolean(PACKED_TAG, false);
		return contents;
	}

	@Override
	public void loadSynchronizedData(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadSynchronizedData(tag, registries);
		// 没有 woodType 就是没有：以前这里在“没染色”时回落成 ACACIA，于是任何一次不带同步数据的加载
		// （物品数据、方块实体首次同步、区块数据）都会把桶的木种改成金合欢。木种的正源是方块状态的 WOOD
		// 属性（放置时从物品写入），方块实体这份只是给渲染/掉落物用的副本，缺失时保持 null 即可。
		woodType = NBTHelper.getString(tag, "woodType").flatMap(woodTypeName -> WoodType.values().filter(wt -> wt.name().equals(woodTypeName)).findFirst())
				.orElse(null);
		packed = tag.getBooleanOr(PACKED_TAG, false);
		// 客户端是走 loadSynchronizedData 拿到 woodType 的（不经过 setWoodType），
		// 必须让区块重画，否则方块颜色按“无染色”烘焙后一直是白的
		WorldHelper.notifyBlockUpdate(this);
	}

	public Optional<WoodType> getWoodType() {
		return Optional.ofNullable(woodType);
	}

	public void setWoodType(WoodType woodType) {
		this.woodType = woodType;
		setChanged();
		// 方块颜色是从方块实体读的，客户端拿到 woodType 后必须让区块重新渲染，
		// 否则首次烘焙时取不到颜色（-1 = 不染色）→ 放下后是白桶，直到下一次方块更新
		WorldHelper.notifyBlockUpdate(this);
	}

	@Override
	public Component getDisplayName() {
		if (displayName != null) {
			return displayName;
		}
		return makeWoodStorageDescriptionId(resolveDisplayWoodType());
	}

	/**
	 * Wood type used for the block's name (Jade / the menu title), resolved the same way the item resolves its own
	 * name: a fully dyed block has no wood name at all, otherwise the copied wood type wins and the block state is the
	 * fallback.
	 * <p>
	 * The block state fallback matters because it always arrives with the block, while the block entity copy travels in
	 * the update packet - without it a placed acacia limited barrel showed up as just "Limited Copper Barrel" in Jade.
	 */
	@Nullable
	private WoodType resolveDisplayWoodType() {
		StorageWrapper wrapper = getStorageWrapper();
		if (wrapper.hasMainColor() && wrapper.hasAccentColor()) {
			return null;
		}
		Optional<WoodType> storedWoodType = getWoodType();
		if (storedWoodType.isPresent()) {
			return storedWoodType.get();
		}
		BlockState state = getBlockState();
		if (state.hasProperty(BarrelBlock.WOOD)) {
			return WoodStorageBlockBase.woodTypeAt(state.getValue(BarrelBlock.WOOD));
		}
		return null;
	}

	private Component makeWoodStorageDescriptionId(@Nullable WoodType wt) {
		String id = getBlockState().getBlock().getDescriptionId();
		return WoodStorageBlockItem.getDisplayName(id, wt);
	}

	public boolean isPacked() {
		return packed;
	}

	public void setPacked(boolean packed) {
		if (this.packed == packed) {
			return;
		}
		this.packed = packed;
		// 打包状态存在 synchronized data 里，必须标脏才会进存档；否则「打包后挖掉再放下」会读回未打包状态。
		setChanged();
		// 打包态只由 block entity 渲染器表现（{@code PackedBarrelRenderer}）：往方块状态里加属性会改变
		// 后续所有方块的 state id，老存档里的方块会被重映射，代价远大于收益。
	}

	@Override
	public boolean shouldDropContents() {
		return !isPacked();
	}

	@Nullable
	@Override
	public SlottedStackStorage getExternalItemHandler(@Nullable Direction side) {
		if (isPacked()) {
			return null;
		}
		return super.getExternalItemHandler(side);
	}

	@Override
	public boolean canConnectStorages() {
		return !packed && super.canConnectStorages();
	}

	@Override
	public boolean canBeConnected() {
		return !packed && super.canBeConnected();
	}

	@Override
	public boolean canBeLinked() {
		return !packed;
	}

	@Override
	protected boolean canRefreshUpgrades() {
		return super.canRefreshUpgrades() && !packed;
	}
}
