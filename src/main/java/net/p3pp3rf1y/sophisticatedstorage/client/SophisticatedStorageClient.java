package net.p3pp3rf1y.sophisticatedstorage.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.PaintbrushOverlay;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.ToolInfoOverlay;
import net.p3pp3rf1y.sophisticatedstorage.client.init.ModBlocksClient;
import net.p3pp3rf1y.sophisticatedstorage.client.init.ModItemsClient;

public class SophisticatedStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 26.3：原 ClientEventHandler 依赖已删除的渲染 / Fabric 事件 API，暂放于 src/parked/java，
		// 这里只保留屏幕与 HUD 注册（HUD 改用 26.3 的 HudElementRegistry）。
		ModBlocksClient.registerScreens();
		ModItemsClient.registerScreens();

		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "paintbrush_info"), PaintbrushOverlay.HUD_PAINTBRUSH_INFO);
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "tool_info"), ToolInfoOverlay.HUD_TOOL_INFO);
	}
}
