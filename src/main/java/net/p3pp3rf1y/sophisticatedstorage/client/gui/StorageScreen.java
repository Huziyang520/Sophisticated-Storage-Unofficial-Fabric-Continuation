package net.p3pp3rf1y.sophisticatedstorage.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;

public class StorageScreen extends StorageScreenBase<StorageContainerMenu> {
	public static StorageScreen constructScreen(StorageContainerMenu screenContainer, Inventory inv, Component title) {
		// 诊断面包屑（第 5 轮实机验收后不再需要常驻 info 级）：只在 debug 下打印，
		// 仍保留「屏幕是否真的被构造」这条线索，又不污染 latest.log
		net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage.LOGGER.debug("storage screen constructed (menu {})", screenContainer.containerId);
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
