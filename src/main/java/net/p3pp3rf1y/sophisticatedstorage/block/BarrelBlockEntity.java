package net.p3pp3rf1y.sophisticatedstorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class BarrelBlockEntity extends WoodStorageBlockEntity implements IMaterialHolder {
	private static final String MATERIALS_TAG = "materials";
	public static final String STORAGE_TYPE = "barrel";
	private Map<BarrelMaterial, Identifier> materials = new EnumMap<>(BarrelMaterial.class);
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(state, SoundEvents.BARREL_OPEN);
			updateOpenBlockState(state, true);
		}

		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(state, SoundEvents.BARREL_CLOSE);
			updateOpenBlockState(state, false);
		}

		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previousOpenerCount, int newOpenerCount) {
			//noop
		}

		public boolean isOwnContainer(Player player) {
			// the settings menu counts as "still looking at it" too, otherwise the scheduled recheck closes the barrel
			// (and plays the close sound) a few ticks after the settings page is opened - see StorageBlockEntity#isMenuOf
			return isCurrentMenuOf(player);
		}
	};

	private IDynamicRenderTracker dynamicRenderTracker = IDynamicRenderTracker.NOOP;

	@Override
	protected ContainerOpenersCounter getOpenersCounter() {
		return openersCounter;
	}

	@Override
	protected String getStorageType() {
		return STORAGE_TYPE;
	}

	protected BarrelBlockEntity(BlockPos pos, BlockState state, BlockEntityType<? extends BarrelBlockEntity> blockEntityType) {
		super(pos, state, blockEntityType);
		getStorageWrapper().getRenderInfo().setChangeListener(ri -> {
			dynamicRenderTracker.onRenderInfoUpdated(ri);
			WorldHelper.notifyBlockUpdate(this);
		});
	}

	public void setDynamicRenderTracker(IDynamicRenderTracker dynamicRenderTracker) {
		this.dynamicRenderTracker = dynamicRenderTracker;
	}

	public BarrelBlockEntity(BlockPos pos, BlockState state) {
		this(pos, state, ModBlocks.BARREL_BLOCK_ENTITY_TYPE.get());
	}

	void updateOpenBlockState(BlockState state, boolean open) {
		if (level == null) {
			return;
		}
		level.setBlock(getBlockPos(), state.setValue(BarrelBlock.OPEN, open), 3);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level.isClientSide()) {
			//dynamic render tracking lives in the (currently parked) client render package
		}
	}

	public boolean hasDynamicRenderer() {
		return dynamicRenderTracker.isDynamicRenderer();
	}

	public boolean hasFullyDynamicRenderer() {
		return dynamicRenderTracker.isFullyDynamicRenderer();
	}

	@Override
	public void toggleLock() {
		setUpdateBlockRender();
		super.toggleLock();
	}

	@Override
	protected void saveSynchronizedData(CompoundTag tag) {
		super.saveSynchronizedData(tag);
		NBTHelper.putMap(tag, MATERIALS_TAG, materials, BarrelMaterial::getSerializedName, resourceLocation -> StringTag.valueOf(resourceLocation.toString()));
	}

	@Override
	public void loadSynchronizedData(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadSynchronizedData(tag, registries);
		materials = NBTHelper.getMap(tag, MATERIALS_TAG, BarrelMaterial::fromName, (bm, t) -> t.asString().map(Identifier::parse)).orElse(Map.of());
	}

	@Override
	public void setMaterials(Map<BarrelMaterial, Identifier> materials) {
		this.materials = materials;
		setChanged();
	}

	@Override
	public Map<BarrelMaterial, Identifier> getMaterials() {
		return materials;
	}

	@Override
	public boolean canHoldMaterials() {
		return true;
	}
}
