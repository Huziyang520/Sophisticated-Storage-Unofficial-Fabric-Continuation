package net.p3pp3rf1y.sophisticatedstorage.common.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.ISyncedContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SophisticatedMenuProvider;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.settings.itemdisplay.ItemDisplaySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NoopStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.StorageTranslationHelper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

import java.util.Optional;

public class StorageContainerMenu extends StorageContainerMenuBase<IStorageWrapper> implements ISyncedContainer {
	private static final String SOURCE_CONTAINER_ID_TAG = "sourceContainerId";
	private final StorageBlockEntity storageBlockEntity;
	/**
	 * Whether this menu owns one opener of the block's counter and therefore has to release it when it closes.
	 * <p>
	 * Both sides count (the client needs the count for the chest lid animation), so both sides have to agree on who
	 * owns it: a menu that replaced another one for the same block inherits the opener instead of starting a second
	 * one, and the replaced menu hands it over ({@link #transferOpenersToSettingsMenu()}). Each menu releases at most
	 * once, which is what keeps the count from drifting (26.3 decrements it without a guard, and a negative count never
	 * crosses zero again, so the open/close sounds would be gone for good).
	 */
	private boolean holdsOpeners;

	public StorageContainerMenu(int containerId, Player player, BlockPos pos) {
		this(containerId, player, pos, false);
	}

	public StorageContainerMenu(int containerId, Player player, BlockPos pos, boolean openersAlreadyActive) {
		this(ModBlocks.STORAGE_CONTAINER_TYPE.get(), containerId, player, pos, openersAlreadyActive);
	}

	public StorageContainerMenu(MenuType<?> menuType, int containerId, Player player, BlockPos pos) {
		this(menuType, containerId, player, pos, false);
	}

	public StorageContainerMenu(MenuType<?> menuType, int containerId, Player player, BlockPos pos, boolean openersAlreadyActive) {
		super(menuType, containerId, player, getWrapper(player.level(), pos), NoopStorageWrapper.INSTANCE, -1, false);
		storageBlockEntity = resolveStorageBlockEntity(player.level(), pos).orElseThrow(() -> new IllegalArgumentException("Incorrect block entity at " + pos + " exptected to find StorageBlockEntity"));
		// 诊断面包屑（第 5 轮实机验收后不再需要常驻 info 级）：降为 debug，
		// 槽位数量这类信息只在排查「界面点不动 / 槽位错位」时才需要
		net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage.LOGGER.debug("storage menu created at {} (id {}) side={} slots={} upgrades={} be={} state={}",
				pos, containerId, player.level().isClientSide(), getNumberOfStorageInventorySlots(), getNumberOfUpgradeSlots(),
				storageBlockEntity.getClass().getSimpleName(), storageBlockEntity.getBlockState().getBlock());
		// Opener ownership: the server is what closes the block when the player leaves; the client counts the menu that was
		// actually created (the settings/back menus are created from a buffer on the client, so they always count
		// themselves) so its lid state follows the screens instead of drifting.
		//
		// Only one menu per block exists at a time, so exactly one of them has to hold the opener: either this menu
		// counts it itself, or it was handed over by the menu it replaces (settings tab, back-to-storage) and must
		// count it out on close. The old <code>holdsOpeners = inherited || !openersAlreadyActive</code> dropped the
		// ownership in the handed-over case: no increment and no decrement later on, so the block's counter stayed at
		// 1 for the rest of its life. 26.3 only fires {@code onOpen} when the previous count was 0 - from that moment
		// on the barrel/chest/shulker box never played an open or close sound again.
		boolean openerAlreadyCounted = openersAlreadyActive || StorageBlockEntity.isMenuOf(player, storageBlockEntity);
		if (!openerAlreadyCounted) {
			storageBlockEntity.startOpen(player);
		}
		holdsOpeners = true;
	}

	public StorageBlockEntity getStorageBlockEntity() {
		return storageBlockEntity;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		releaseOpeners(player);
	}

	/** Releases the opener at most once; a menu that handed it over owns nothing any more. */
	private void releaseOpeners(Player player) {
		if (!holdsOpeners) {
			return;
		}
		holdsOpeners = false;
		storageBlockEntity.stopOpen(player);
	}

	/** Hands the opener count over to the menu that replaces this one so the block does not see a close/open cycle. */
	public void transferOpenersToSettingsMenu() {
		holdsOpeners = false;
	}

	private static IStorageWrapper getWrapper(Level level, BlockPos pos) {
		return resolveStorageBlockEntity(level, pos).map(be -> (IStorageWrapper) be.getStorageWrapper()).orElse(NoopStorageWrapper.INSTANCE);
	}

	/**
	 * Resolves the block entity whose wrapper actually holds the items.
	 * <p>
	 * A double chest is two block entities: only the "main" half owns the storage, the other half has a
	 * {@code doubleMainPos} pointing at it. Opening the second half used to create the menu on its own, empty wrapper,
	 * so a double chest looked completely empty. The client copy of {@code doubleMainPos} can also lag behind the join,
	 * so the block state (which is always in sync) is used as a fallback and the half with the larger inventory wins -
	 * that is always the main one.
	 */
	private static Optional<StorageBlockEntity> resolveStorageBlockEntity(Level level, BlockPos pos) {
		Optional<StorageBlockEntity> blockEntity = WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class);
		if (blockEntity.isEmpty() || !(blockEntity.get() instanceof ChestBlockEntity chest)) {
			return blockEntity;
		}

		ChestBlockEntity mainChest = chest.getMainChestBlockEntity();
		if (mainChest != null && mainChest != chest) {
			return Optional.of(mainChest);
		}

		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
			if (level.getBlockEntity(otherPos) instanceof StorageBlockEntity other
					&& inventorySlots(other) > inventorySlots(chest)) {
				return Optional.of(other);
			}
		}

		return blockEntity;
	}

	private static int inventorySlots(StorageBlockEntity blockEntity) {
		return blockEntity.getStorageWrapper().getInventoryHandler().getSlotCount();
	}

	public static StorageContainerMenu fromBuffer(int windowId, Inventory playerInventory, FriendlyByteBuf buffer) {
		return new StorageContainerMenu(windowId, playerInventory.player, buffer.readBlockPos());
	}

	@Override
	public Optional<BlockPos> getBlockPosition() {
		return Optional.of(storageBlockEntity.getBlockPos());
	}

	@Override
	public Optional<Entity> getEntity() {
		return Optional.empty();
	}

	@Override
	protected StorageContainerMenuBase<IStorageWrapper>.StorageUpgradeSlot instantiateUpgradeSlot(UpgradeHandler upgradeHandler, int slotIndex) {
		return new StorageUpgradeSlot(upgradeHandler, slotIndex) {
			@Override
			protected void onUpgradeChanged() {
				if (player.level().isClientSide()) {
					return;
				}
				storageWrapper.getSettingsHandler().getTypeCategory(ItemDisplaySettingsCategory.class).itemsChanged();
			}
		};
	}

	@Override
	public void openSettings() {
		if (isClientSide()) {
			// the client lays claim to the opener as well - the settings menu takes over the count instead of this menu
			// releasing it, otherwise the client closes the lid while the settings screen is open
			transferOpenersToSettingsMenu();
			sendToServer(data -> {
				data.putString(ACTION_TAG, "openSettings");
				data.putInt(SOURCE_CONTAINER_ID_TAG, containerId);
			});
			return;
		}
		if (!stillValid(player)) {
			return;
		}
		getBlockPosition().ifPresent(pos -> {
			// the settings menu replaces this menu, so it has to inherit the opener instead of the block seeing
			// "closed" (settings opened) immediately followed by "opened" again
			transferOpenersToSettingsMenu();
			player.sophisticatedCore_openMenu(new SophisticatedMenuProvider((w, p, pl) -> instantiateSettingsContainerMenu(w, pl, pos, true),
					Component.translatable(StorageTranslationHelper.INSTANCE.translGui("settings.title")), false), storageBlockEntity.getBlockPos());
		});
	}

	@Override
	public void handlePacket(CompoundTag data) {
		if ("openSettings".equals(data.getStringOr(ACTION_TAG, "")) && data.getIntOr(SOURCE_CONTAINER_ID_TAG, -1) != containerId) {
			// a stale request from a menu that was already replaced - acting on it would open a second settings menu
			return;
		}
		super.handlePacket(data);
	}

	protected StorageSettingsContainerMenu instantiateSettingsContainerMenu(int windowId, Player player, BlockPos pos) {
		return instantiateSettingsContainerMenu(windowId, player, pos, false);
	}

	protected StorageSettingsContainerMenu instantiateSettingsContainerMenu(int windowId, Player player, BlockPos pos, boolean openersAlreadyActive) {
		return new StorageSettingsContainerMenu(windowId, player, pos, openersAlreadyActive);
	}

	@Override
	protected boolean storageItemHasChanged() {
		return false; //storage blocks never have the issue of needing to close gui when item has moved in inventory
	}

	@Override
	public boolean detectSettingsChangeAndReload() {
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		BlockPos pos = storageBlockEntity.getBlockPos();
		BlockEntity be = player.level().getBlockEntity(pos);
		return be instanceof StorageBlockEntity
				&& (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D)
				&& (!(be instanceof WoodStorageBlockEntity woodStorageBlockEntity) || !woodStorageBlockEntity.isPacked());
	}

	@Override
	protected void onStorageInventorySlotSet(int slotIndex) {
		super.onStorageInventorySlotSet(slotIndex);

		if (getStorageBlockEntity().isLocked() && getStorageBlockEntity().memorizesItemsWhenLocked() && !getSlot(slotIndex).getItem().isEmpty()) {
			MemorySettingsCategory memorySettings = getStorageWrapper().getSettingsHandler().getTypeCategory(MemorySettingsCategory.class);
			if (!memorySettings.isSlotSelected(slotIndex)) {
				memorySettings.selectSlot(slotIndex);
			}
		}
	}

	public float getSlotFillPercentage(int slot) {
		return storageBlockEntity.getSlotFillPercentage(slot);
	}
}
