package net.p3pp3rf1y.sophisticatedstorage.block;

import net.p3pp3rf1y.sophisticatedcore.platform.item.SlottedStackStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.controller.IControllableStorage;
import net.p3pp3rf1y.sophisticatedcore.controller.ILinkable;
import net.p3pp3rf1y.sophisticatedcore.inventory.CachedFailedInsertInventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler;
import net.p3pp3rf1y.sophisticatedcore.settings.itemdisplay.ItemDisplaySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageSettingsContainerMenu;
import net.p3pp3rf1y.sophisticatedstorage.upgrades.INeighborChangeListenerUpgrade;

import org.jetbrains.annotations.Nullable;
import java.util.*;

public abstract class StorageBlockEntity extends BlockEntity implements IControllableStorage, ILinkable, ILockable, Nameable, ITierDisplay, IUpgradeDisplay {
	public static final String STORAGE_WRAPPER_TAG = "storageWrapper";
	/**
	 * Name of the tag that holds all of this block entity's own data. The chunk data and the block entity update packet
	 * both nest it under this name and {@link #loadAdditional} always reads it from there - keep those two in sync.
	 */
	public static final String DATA_TAG = "sophisticatedstorage_data";
	private final StorageWrapper storageWrapper;
	@Nullable
	protected Component displayName = null;

	private boolean updateBlockRender = false;
	@Nullable
	private BlockPos controllerPos = null;
	private boolean isLinkedToController = false;
	private boolean isBeingUpgraded = false;

	protected abstract ContainerOpenersCounter getOpenersCounter();

	private boolean isDroppingContents = false;

	private boolean chunkBeingUnloaded = false;

	@Nullable
	private SlottedStackStorage cachedFailedInsertItemHandler;
	private boolean locked = false;
	private boolean showLock = true;
	private boolean showTier = true;
	private boolean showUpgrades = false;
	@Nullable
	private ContentsFilteredItemHandler contentsFilteredItemHandler = null;

	protected StorageBlockEntity(BlockPos pos, BlockState state, BlockEntityType<? extends StorageBlockEntity> blockEntityType) {
		super(blockEntityType, pos, state);
		storageWrapper = new StorageWrapper(() -> this::setChanged, () -> WorldHelper.notifyBlockUpdate(this), () -> {
			setChanged();
			WorldHelper.notifyBlockUpdate(this);
		}, this instanceof BarrelBlockEntity ? 4 : 1, this instanceof ICountDisplay || this instanceof IFillLevelDisplay) {

			@Override
			public Optional<UUID> getContentsUuid() {
				if (contentsUuid == null) {
					contentsUuid = UUID.randomUUID();
					save();
				}
				return Optional.of(contentsUuid);
			}

			@Override
			public ItemStack getWrappedStorageStack() {
				BlockPos pos = getBlockPos();
				BlockState state = getBlockState();

				// 26.3：BlockBehaviour#getCloneItemStack 变为 protected，改用块对应的物品
				ItemStack clonedStack = new ItemStack(state.getBlock().asItem());
				return addWrappedStorageStackData(clonedStack, state);
			}

			@Override
			protected void onUpgradeRefresh() {
				if (canRefreshUpgrades() && getBlockState().getBlock() instanceof IStorageBlock storageBlock) {
					storageBlock.setTicking(level, getBlockPos(), getBlockState(), !storageWrapper.getUpgradeHandler().getWrappersThatImplement(ITickableUpgrade.class).isEmpty());
				}
			}

			@Override
			public int getDefaultNumberOfInventorySlots() {
				if (getBlockState().getBlock() instanceof IStorageBlock storageBlock) {
					return storageBlock.getNumberOfInventorySlots();
				}
				return 0;
			}

			@Override
			protected boolean isAllowedInStorage(ItemStack stack) {
				return StorageBlockEntity.this.isAllowedInStorage(stack);
			}

			@Override
			public int getDefaultNumberOfUpgradeSlots() {
				if (getBlockState().getBlock() instanceof IStorageBlock storageBlock) {
					return storageBlock.getNumberOfUpgradeSlots();
				}
				return 0;
			}

			@Override
			public int getBaseStackSizeMultiplier() {
				return getBlockState().getBlock() instanceof IStorageBlock storageBlock ? storageBlock.getBaseStackSizeMultiplier() : super.getBaseStackSizeMultiplier();
			}

			@Override
			public String getStorageType() {
				return StorageBlockEntity.this.getStorageType();
			}

			@Override
			public Component getDisplayName() {
				return StorageBlockEntity.this.getDisplayName();
			}

			@Override
			protected boolean emptyInventorySlotsAcceptItems() {
				return !locked || allowsEmptySlotsMatchingItemInsertsWhenLocked();
			}

			@Override
			public ITrackedContentsItemHandler getInventoryForInputOutput() {
				if (locked && allowsEmptySlotsMatchingItemInsertsWhenLocked()) {
					if (contentsFilteredItemHandler == null) {
						contentsFilteredItemHandler = new ContentsFilteredItemHandler(super::getInventoryForInputOutput, () -> getStorageWrapper().getInventoryHandler().getSlotTracker(), () -> getStorageWrapper().getSettingsHandler().getTypeCategory(MemorySettingsCategory.class));
					}
					return contentsFilteredItemHandler;
				}

				return super.getInventoryForInputOutput();
			}
		};
		storageWrapper.setUpgradeCachesInvalidatedHandler(this::onUpgradeCachesInvalidated);
	}

	protected boolean canRefreshUpgrades() {
		return !isDroppingContents && level != null && !level.isClientSide();
	}

	@SuppressWarnings("java:S1172") //parameter used in override
	protected ItemStack addWrappedStorageStackData(ItemStack cloneItemStack, BlockState state) {
		return cloneItemStack;
	}

	protected abstract String getStorageType();

	protected void onUpgradeCachesInvalidated() {
		sophisticatedCore_invalidateCapabilities();
	}

	public boolean isOpen() {
		return getOpenersCounter().getOpenerCount() > 0;
	}

	@Override
	public Component getCustomName() {
		return displayName;
	}

	@Override
	public void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		CompoundTag tag = new CompoundTag();
		super.saveAdditional(output);
		saveStorageWrapper(tag);
		saveSynchronizedData(tag);
		saveControllerPos(tag);
		if (isLinkedToController) {
			tag.putBoolean("isLinkedToController", isLinkedToController);
		}
			output.store(DATA_TAG, CompoundTag.CODEC, tag);
	}

	private void saveStorageWrapper(CompoundTag tag) {
		tag.put(STORAGE_WRAPPER_TAG, storageWrapper.save(new CompoundTag()));
	}

	private void saveStorageWrapperClientData(CompoundTag tag) {
		tag.put(STORAGE_WRAPPER_TAG, storageWrapper.saveData(new CompoundTag()));
	}

	protected void saveSynchronizedData(CompoundTag tag) {
		if (displayName != null) {
			tag.putString("displayName", net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, displayName).getOrThrow().toString());
		}
		if (updateBlockRender) {
			tag.putBoolean("updateBlockRender", true);
		}
		if (locked) {
			tag.putBoolean("locked", locked);
		}
		if (!showLock) {
			tag.putBoolean("showLock", showLock);
		}
		if (!showTier) {
			tag.putBoolean("showTier", showTier);
		}
		if (showUpgrades) {
			tag.putBoolean("showUpgrades", showUpgrades);
		}
	}

	public void startOpen(Player player) {
		if (!remove && !player.isSpectator() && level != null) {
			getOpenersCounter().incrementOpeners(player, level, getBlockPos(), getBlockState(), 5.0D);
		}

	}

	public void stopOpen(Player player) {
		// 26.3's ContainerOpenersCounter#decrementOpeners has no "> 0" guard any more (unlike older versions): it
		// decrements and only fires onClose when the result is exactly 0. A single extra release therefore drives the
		// count negative, and from then on neither incrementOpeners nor decrementOpeners crosses 0 again - the open and
		// close sounds never play for that block again. Every menu releases exactly once (see the ownership flags in
		// StorageContainerMenu / StorageSettingsContainerMenu), this is the second line of defence.
		if (!remove && !player.isSpectator() && level != null && getOpenersCounter().getOpenerCount() > 0) {
			getOpenersCounter().decrementOpeners(player, level, getBlockPos(), getBlockState());
		}
	}

	/**
	 * Whether the player is currently looking at {@code blockEntity}'s storage through one of its menus.
	 * <p>
	 * Used as {@link ContainerOpenersCounter#isOwnContainer}, which 26.3 consults when the scheduled recheck recounts
	 * the openers. It has to cover the settings menu as well: while the settings page is open the storage menu is gone,
	 * so a counter that only recognises {@link StorageContainerMenu} recounts to zero, closes the chest (and plays the
	 * close sound) and then leaves the settings menu releasing an opener the counter no longer has - the negative count
	 * described in {@link #stopOpen}.
	 */
	public static boolean isMenuOf(Player player, @Nullable StorageBlockEntity blockEntity) {
		if (blockEntity == null) {
			return false;
		}
		if (player.containerMenu instanceof StorageContainerMenu storageContainerMenu) {
			return storageContainerMenu.getStorageBlockEntity() == blockEntity;
		}
		return player.containerMenu instanceof StorageSettingsContainerMenu settingsContainerMenu
				&& settingsContainerMenu.getBlockPosition().equals(blockEntity.getBlockPos());
	}

	public boolean isCurrentMenuOf(Player player) {
		return isMenuOf(player, this);
	}

	public void recheckOpen() {
		if (!remove && level != null) {
			getOpenersCounter().recheckOpeners(level, getBlockPos(), getBlockState());
		}
	}

	void playSound(BlockState state, SoundEvent sound) {
		if (level == null || !(state.getBlock() instanceof StorageBlockBase storageBlock)) {
			return;
		}
		Vec3i vec3i = storageBlock.getFacing(state).getStep();
		double d0 = worldPosition.getX() + 0.5D + vec3i.getX() / 2.0D;
		double d1 = worldPosition.getY() + 0.5D + vec3i.getY() / 2.0D;
		double d2 = worldPosition.getZ() + 0.5D + vec3i.getZ() / 2.0D;
		level.playSound(null, d0, d1, d2, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
	}

	@Override
	public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		CompoundTag tag = input.read(DATA_TAG, CompoundTag.CODEC).orElseGet(CompoundTag::new);
		super.loadAdditional(input);
		HolderLookup.Provider registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
		loadStorageWrapper(tag, registries);
		loadSynchronizedData(tag, registries);

		// Had to add a separate tag to distinguish between a normal load and an update packet
		if (!tag.contains("updateTag")) {
			loadControllerPos(tag);

			isLinkedToController = NBTHelper.getBoolean(tag, "isLinkedToController").orElse(false);
		}
	}

	private void loadStorageWrapper(CompoundTag tag, HolderLookup.Provider registries) {
		NBTHelper.getCompound(tag, STORAGE_WRAPPER_TAG).ifPresent(wrapperTag -> {
			// 两类输入共用一个入口：区块存档 / 物品自带的整块数据（没有 updateTag 标记）走完整 load；
			// 方块实体更新包（createClientData 写的 updateTag=true）只解码客户端数据，不换 handler 实例。
			// 后者是「菜单持旧 handler → 偶发点不动」和「限类木桶取一个就掉色」两条缺陷的共同根因。
			if (tag.getBooleanOr("updateTag", false)) {
				storageWrapper.loadClientData(wrapperTag);
			} else {
				storageWrapper.load(wrapperTag);
			}
		});
	}

	@Override
	public void sophisticatedCore_onLoad() {
		super.sophisticatedCore_onLoad();
		storageWrapper.onInit();
		registerWithControllerOnLoad();
	}

	public void loadSynchronizedData(CompoundTag tag, HolderLookup.Provider registries) {
		displayName = NBTHelper.getComponent(tag, "displayName", registries).orElse(null);
		locked = NBTHelper.getBoolean(tag, "locked").orElse(false);
		showLock = NBTHelper.getBoolean(tag, "showLock").orElse(true);
		showTier = NBTHelper.getBoolean(tag, "showTier").orElse(true);
		showUpgrades = NBTHelper.getBoolean(tag, "showUpgrades").orElse(false);
		if (level != null && level.isClientSide()) {
			if (tag.getBooleanOr("updateBlockRender", false)) {
				WorldHelper.notifyBlockUpdate(this);
			}
		} else {
			updateBlockRender = true;
		}
	}

	@Override
	public void sophisticatedCore_onChunkUnloaded() {
		super.sophisticatedCore_onChunkUnloaded();
		chunkBeingUnloaded = true;
	}

	@Override
	public void setRemoved() {
		if (!isBeingUpgraded && !chunkBeingUnloaded && level != null) {
			removeFromController();
		}

		super.setRemoved();
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** This is inside {@link #loadAdditional} **/
	/*@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		CompoundTag tag = pkt.getTag();
		if (tag.isEmpty()) {
			return;
		}

		loadStorageWrapper(tag, registries);
		loadSynchronizedData(tag, registries);
	}*/

	public void setUpdateBlockRender() {
		updateBlockRender = true;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		// Vanilla feeds the returned tag straight into {@link #loadAdditional(ValueInput)} on the client
		// ({@code ClientPacketListener#handleBlockEntityData} -> {@code BlockEntity#loadWithComponents}), so it has to
		// have exactly the same shape as {@link #saveAdditional}. This used to write {@code storageWrapper} and the
		// synchronised fields at the top level while {@code loadAdditional} only reads them from the nested
		// {@link #DATA_TAG} tag - the client therefore loaded an empty tag from every update packet and never got the
		// colours, the wood type, the render info (display items / slot counts) or the locked flag. That is why a dyed
		// barrel lost its colour the moment it was placed and why the limited barrel never had any contents to draw.
		CompoundTag tag = super.getUpdateTag(registries);
		tag.put(DATA_TAG, createClientData());
		return tag;
	}

	/**
	 * Builds the block entity data that is synchronised to the client, in the same shape as
	 * {@link #saveAdditional(net.minecraft.world.level.storage.ValueOutput)} writes it. Subclasses add their own
	 * synchronised fields here (see {@code LimitedBarrelBlockEntity#createClientData}).
	 */
	protected CompoundTag createClientData() {
		CompoundTag tag = new CompoundTag();
		// Had to add a separate tag to distinguish between a normal load and an update packet
		tag.putBoolean("updateTag", true);
		saveStorageWrapperClientData(tag);
		saveSynchronizedData(tag);
		return tag;
	}

	public static void serverTick(Level level, BlockPos blockPos, StorageBlockEntity storageBlockEntity) {
		storageBlockEntity.getStorageWrapper().getUpgradeHandler().getWrappersThatImplement(ITickableUpgrade.class).forEach(upgrade -> upgrade.tick(null, level, blockPos));
	}

	@Override
	public StorageWrapper getStorageWrapper() {
		return storageWrapper;
	}

	@Override
	public Component getName() {
		return getDisplayName();
	}

	@Override
	public Component getDisplayName() {
		if (displayName != null) {
			return displayName;
		}
		return getBlockState().getBlock().getName();
	}

	@SuppressWarnings("unused") //stack param used in override
	protected boolean isAllowedInStorage(ItemStack stack) {
		return true;
	}

	public void changeStorageSize(int additionalInventorySlots, int additionalUpgradeSlots) {
		int currentInventorySlots = getStorageWrapper().getInventoryHandler().getSlotCount();
		getStorageWrapper().changeSize(additionalInventorySlots, additionalUpgradeSlots);
		changeSlots(currentInventorySlots + additionalInventorySlots);
	}

	public void dropContents() {
		if (level == null || level.isClientSide()) {
			return;
		}
		isDroppingContents = true;
		InventoryHelper.dropItems(storageWrapper.getInventoryHandler(), level, worldPosition);

		InventoryHelper.dropItems(storageWrapper.getUpgradeHandler(), level, worldPosition);
		isDroppingContents = false;
	}

	/**
	 * 26.3 里方块被移除时，{@code LevelChunk.setBlockState} 的调用顺序是：
	 * {@code BlockEntity.preRemoveSideEffects} → {@code removeBlockEntity} → {@code Block.affectNeighborsAfterRemoval}。
	 * 也就是说方块侧的 {@code affectNeighborsAfterRemoval} 跑的时候方块实体已经从区块里摘掉了，
	 * 在那里用 {@code level.getBlockEntity(pos)} 取实体永远是 null（原来 {@link StorageBlockBase} 的写法），
	 * 于是未打包的箱子/木桶被破坏时里面的东西既没进掉落物、也没被撒出来，直接消失。
	 * 投放必须在实体还活着的时候做，所以搬到这里。
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		removeFromController();
		if (shouldDropContents()) {
			dropContents();
		}
	}

	public void setCustomName(Component customName) {
		displayName = customName;
		setChanged();
	}

	@Nullable
	public SlottedStackStorage getExternalItemHandler(@Nullable Direction side) {
		if (side == null) {
			return getStorageWrapper().getInventoryForInputOutput();
		}
		if (cachedFailedInsertItemHandler == null) {
			cachedFailedInsertItemHandler = new CachedFailedInsertInventoryHandler(() -> getStorageWrapper().getInventoryForInputOutput(), () -> level != null ? level.getGameTime() : 0);
		}
		return cachedFailedInsertItemHandler;
	}

	public boolean shouldDropContents() {
		return true;
	}

	@Override
	public void setControllerPos(BlockPos controllerPos) {
		this.controllerPos = controllerPos;
		setChanged();
	}

	@Override
	public Optional<BlockPos> getControllerPos() {
		return Optional.ofNullable(controllerPos);
	}

	@Override
	public void removeControllerPos() {
		if (controllerPos != null) {
			controllerPos = null;
			setChanged();
		}
	}

	@Override
	public BlockPos getStorageBlockPos() {
		return getBlockPos();
	}

	@Override
	public Level getStorageBlockLevel() {
		return Objects.requireNonNull(getLevel());
	}

	@Override
	public void linkToController(BlockPos controllerPos) {
		if (getControllerPos().isPresent()) {
			return;
		}

		isLinkedToController = true;
		ILinkable.super.linkToController(controllerPos);
		setChanged();
	}

	@Override
	public boolean isLinked() {
		return isLinkedToController && getControllerPos().isPresent();
	}

	@Override
	public void setNotLinked() {
		ILinkable.super.setNotLinked();
		isLinkedToController = false;
		setChanged();
	}

	@Override
	public boolean canConnectStorages() {
		return !isLinkedToController;
	}

	@Override
	public Set<BlockPos> getConnectablePositions() {
		return Collections.emptySet();
	}

	@Override
	public boolean connectLinkedSelf() {
		return true;
	}

	@Override
	public boolean canBeConnected() {
		return isLinked() || IControllableStorage.super.canBeConnected();
	}

	public void setBeingUpgraded(boolean isBeingUpgraded) {
		this.isBeingUpgraded = isBeingUpgraded;
	}

	public boolean isBeingUpgraded() {
		return isBeingUpgraded;
	}

	@Override
	public boolean isLocked() {
		return locked;
	}

	@Override
	public void toggleLock() {
		if (locked) {
			unlock();
		} else {
			lock();
		}
	}

	public boolean memorizesItemsWhenLocked() {
		return false;
	}

	public boolean allowsEmptySlotsMatchingItemInsertsWhenLocked() {
		return true;
	}

	private void lock() {
		locked = true;
		if (memorizesItemsWhenLocked()) {
			getStorageWrapper().getSettingsHandler().getTypeCategory(MemorySettingsCategory.class).selectSlots(0, getStorageWrapper().getInventoryHandler().getSlotCount());
		}
		updateEmptySlots();
		if (allowsEmptySlotsMatchingItemInsertsWhenLocked()) {
			contentsFilteredItemHandler = null;
			sophisticatedCore_invalidateCapabilities();
		}
		setChanged();
		WorldHelper.notifyBlockUpdate(this);
	}

	private void unlock() {
		locked = false;
		if (memorizesItemsWhenLocked()) {
			getStorageWrapper().getSettingsHandler().getTypeCategory(MemorySettingsCategory.class).unselectAllSlots();
			ItemDisplaySettingsCategory itemDisplaySettings = getStorageWrapper().getSettingsHandler().getTypeCategory(ItemDisplaySettingsCategory.class);
			InventoryHelper.iterate(getStorageWrapper().getInventoryHandler(), (slot, stack) -> {
				if (stack.isEmpty()) {
					itemDisplaySettings.itemChanged(slot);
				}
			});
		}
		updateEmptySlots();
		if (allowsEmptySlotsMatchingItemInsertsWhenLocked()) {
			contentsFilteredItemHandler = null;
			sophisticatedCore_invalidateCapabilities();
		}
		setChanged();
		setUpdateBlockRender();
		WorldHelper.notifyBlockUpdate(this);
	}

	@Override
	public boolean shouldShowLock() {
		return showLock;
	}

	@Override
	public void toggleLockVisibility() {
		showLock = !showLock;
		setChanged();
		setUpdateBlockRender();
		WorldHelper.notifyBlockUpdate(this);
	}

	@Override
	public boolean shouldShowTier() {
		return showTier;
	}

	@Override
	public void toggleTierVisiblity() {
		showTier = !showTier;
		setChanged();
		setUpdateBlockRender();
		WorldHelper.notifyBlockUpdate(this);
	}

	@Override
	public boolean shouldShowUpgrades() {
		return showUpgrades;
	}

	@Override
	public void toggleUpgradesVisiblity() {
		showUpgrades = !showUpgrades;
		setChanged();
		WorldHelper.notifyBlockUpdate(this);
	}

	public void onNeighborChange(BlockPos neighborPos) {
		Direction direction = getNeighborDirection(neighborPos);
		if (direction == null) {
			return;
		}
		storageWrapper.getUpgradeHandler().getWrappersThatImplement(INeighborChangeListenerUpgrade.class).forEach(upgrade -> upgrade.onNeighborChange(level, worldPosition, direction));
	}

	@Nullable
	protected Direction getNeighborDirection(BlockPos neighborPos) {
		Direction direction = null;
		int normalX = Integer.signum(neighborPos.getX() - worldPosition.getX());
		int normalY = Integer.signum(neighborPos.getY() - worldPosition.getY());
		int normalZ = Integer.signum(neighborPos.getZ() - worldPosition.getZ());
		for (Direction value : Direction.values()) {
			Vec3i normal = value.getStep();
			if (normal.getX() == normalX && normal.getY() == normalY && normal.getZ() == normalZ) {
				direction = value;
				break;
			}
		}
		return direction;
	}

	@SuppressWarnings("unused") //parameter used in override
	public float getSlotFillPercentage(int slot) {
		return 0; //only used in limited barrels
	}
}
