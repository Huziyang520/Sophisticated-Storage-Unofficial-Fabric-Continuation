package net.p3pp3rf1y.sophisticatedstorage.common.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SettingsContainerMenu;
import net.p3pp3rf1y.sophisticatedcore.util.NoopStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

public class StorageSettingsContainerMenu extends SettingsContainerMenu<IStorageWrapper> {
	private final BlockPos pos;
	/**
	 * Whether closing this menu should decrement the block's opener count.
	 * <p>
	 * The settings menu always replaces the storage menu, and the block only cares whether anybody is still looking at
	 * it. If both menus release the opener, then the count drops to zero the moment the settings tab is opened - the
	 * chest lid closes - and opening the storage menu again counts it back up, which is the open/close loop the player
	 * sees. So the menu that was replaced hands its opener over, and only the menu that ends the session releases it.
	 */
	private boolean stopOpenersOnRemove;

	protected StorageSettingsContainerMenu(int windowId, Player player, BlockPos pos) {
		this(windowId, player, pos, false);
	}

	protected StorageSettingsContainerMenu(int windowId, Player player, BlockPos pos, boolean openersAlreadyActive) {
		this(ModBlocks.SETTINGS_CONTAINER_TYPE.get(), windowId, player, pos, openersAlreadyActive);
	}

	protected StorageSettingsContainerMenu(MenuType<?> menuType, int windowId, Player player, BlockPos pos) {
		this(menuType, windowId, player, pos, false);
	}

	protected StorageSettingsContainerMenu(MenuType<?> menuType, int windowId, Player player, BlockPos pos, boolean openersAlreadyActive) {
		super(menuType, windowId, player, getWrapper(player.level(), pos));
		this.pos = pos;
		this.stopOpenersOnRemove = openersAlreadyActive;
	}

	private static IStorageWrapper getWrapper(Level level, BlockPos pos) {
		return WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(be -> (IStorageWrapper) be.getStorageWrapper()).orElse(NoopStorageWrapper.INSTANCE);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// on the client every menu that replaced the storage menu owns the opener it inherited; on the server only the menu
		// that was told to (the opener transfer) may release it, so the block never sees a close/open cycle
		if (player.level().isClientSide() || stopOpenersOnRemove) {
			WorldHelper.getBlockEntity(player.level(), pos, StorageBlockEntity.class).ifPresent(be -> be.stopOpen(player));
		}
	}

	/** Hands the opener over to the storage menu that replaces this one when the player goes back to the storage tab. */
	public void transferOpenersToStorageMenu() {
		stopOpenersOnRemove = false;
	}

	@Override
	public void detectSettingsChangeAndReload() {
		//noop
	}

	public static StorageSettingsContainerMenu fromBuffer(int windowId, Inventory playerInventory, FriendlyByteBuf buffer) {
		return new StorageSettingsContainerMenu(windowId, playerInventory.player, buffer.readBlockPos());
	}

	@Override
	public BlockPos getBlockPosition() {
		return pos;
	}
}
