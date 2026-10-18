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
		woodType = NBTHelper.getString(tag, "woodType").flatMap(woodTypeName -> WoodType.values().filter(wt -> wt.name().equals(woodTypeName)).findFirst())
				.orElse(getStorageWrapper().hasMainColor() && getStorageWrapper().hasAccentColor() ? null : WoodType.ACACIA);
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
		return makeWoodStorageDescriptionId(getWoodType().orElse(null));
	}

	private Component makeWoodStorageDescriptionId(@Nullable WoodType wt) {
		String id = getBlockState().getBlock().getDescriptionId();
		return WoodStorageBlockItem.getDisplayName(id, wt);
	}

	public boolean isPacked() {
		return packed;
	}

	public void setPacked(boolean packed) {
		this.packed = packed;
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
