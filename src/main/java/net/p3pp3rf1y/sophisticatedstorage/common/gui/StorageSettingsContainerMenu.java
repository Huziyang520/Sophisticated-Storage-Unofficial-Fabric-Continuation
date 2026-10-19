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

import org.jetbrains.annotations.Nullable;

public class StorageSettingsContainerMenu extends SettingsContainerMenu<IStorageWrapper> {
	private final BlockPos pos;
	@Nullable
	private final StorageBlockEntity storageBlockEntity;
	/**
	 * Whether closing this menu should decrement the block's opener count.
	 * <p>
	 * The settings menu always replaces the storage menu, and the block only cares whether anybody is still looking at
	 * it. If both menus release the opener, then the count drops to zero the moment the settings tab is opened - the
	 * chest lid closes - and opening the storage menu again counts it back up, which is the open/close loop the player
	 * sees. So the menu that was replaced hands its opener over, and only the menu that ends the session releases it -
	 * exactly once, see {@link #removed(Player)}.
	 */
	private boolean holdsOpeners;

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
		this.storageBlockEntity = WorldHelper.getBlockEntity(player.level(), pos, StorageBlockEntity.class).orElse(null);
		// A settings menu is only ever created as the replacement of the storage menu for the same block, and that
		// menu always hands its opener over instead of releasing it - so this menu owns it and has to release it on
		// close. Relying on the constructor flag alone was wrong: on the server the replaced menu is already removed
		// (and the player's container menu reset to the inventory) when this menu is built, so {@code isMenuOf} is
		// false there, while on the client it is still true. The block only needs "somebody still looks at me", and
		// {@link StorageBlockEntity#stopOpen} never decrements below zero, so owning it unconditionally is safe.
		this.holdsOpeners = true;
	}

	private static IStorageWrapper getWrapper(Level level, BlockPos pos) {
		return WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(be -> (IStorageWrapper) be.getStorageWrapper()).orElse(NoopStorageWrapper.INSTANCE);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!holdsOpeners) {
			return;
		}
		holdsOpeners = false;
		if (storageBlockEntity != null) {
			storageBlockEntity.stopOpen(player);
		}
	}

	/** Hands the opener over to the storage menu that replaces this one when the player goes back to the storage tab. */
	public void transferOpenersToStorageMenu() {
		holdsOpeners = false;
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
