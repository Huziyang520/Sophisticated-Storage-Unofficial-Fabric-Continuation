package net.p3pp3rf1y.sophisticatedstorage.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;

public class StorageScreen extends StorageScreenBase<StorageContainerMenu> {
	public static StorageScreen constructScreen(StorageContainerMenu screenContainer, Inventory inv, Component title) {
		// kept as a breadcrumb while the port is under test: if this never shows up in the log after right clicking a
		// storage block, the menu reached the client but no screen was registered for it
		net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage.LOGGER.info("storage screen constructed (menu {})", screenContainer.containerId);
		return new StorageScreen(screenContainer, inv, title);
	}

	protected StorageScreen(StorageContainerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected String getStorageSettingsTabTooltip() {
		return StorageTranslationHelper.INSTANCE.translGui("settings.tooltip");
	}
}
