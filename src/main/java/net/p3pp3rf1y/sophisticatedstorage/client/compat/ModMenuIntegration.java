package net.p3pp3rf1y.sophisticatedstorage.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedstorage.Config;
import net.p3pp3rf1y.sophisticatedcore.platform.config.ConfigScreens;

import java.util.List;

/**
 * Adds this mod's config entry to Mod Menu.
 * <p>
 * The Cloth Config screen is only touched from openConfig, so a player who has not installed
 * Cloth Config gets a failure when opening the entry rather than a crash while the mod list is being built.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::openConfig;
	}

	private static Screen openConfig(Screen parent) {
		return ConfigScreens.create(Component.translatable("modmenu.sophisticatedstorage.config.title"), parent, List.of(
				Config.COMMON_SPEC,
				Config.SERVER_SPEC,
				Config.CLIENT_SPEC
		));
	}
}
