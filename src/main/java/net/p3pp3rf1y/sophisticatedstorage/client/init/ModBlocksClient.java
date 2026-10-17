package net.p3pp3rf1y.sophisticatedstorage.client.init;

import net.minecraft.client.gui.screens.MenuScreens;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.*;

import static net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks.*;

public class ModBlocksClient {
	public static void registerScreens() {
		MenuScreens.register(STORAGE_CONTAINER_TYPE.get(), StorageScreen::constructScreen);
		MenuScreens.register(SETTINGS_CONTAINER_TYPE.get(), StorageSettingsScreen::constructScreen);
		MenuScreens.register(LIMITED_BARREL_CONTAINER_TYPE.get(), LimitedBarrelScreen::new);
		MenuScreens.register(LIMITED_BARREL_SETTINGS_CONTAINER_TYPE.get(), LimitedBarrelSettingsScreen::new);
		// 26.3：DecorationTableScreen 依赖已删除的 GUI 渲染 API，暂放于 src/parked/java
	}
}
