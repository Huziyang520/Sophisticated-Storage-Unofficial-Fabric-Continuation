package net.p3pp3rf1y.sophisticatedstorage.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.p3pp3rf1y.sophisticatedcore.event.client.ClientRawInputEvent;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.network.ScrolledToolPayload;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageContentsTooltip;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.PaintbrushOverlay;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.ToolInfoOverlay;
import net.p3pp3rf1y.sophisticatedstorage.client.init.ModBlocksClient;
import net.p3pp3rf1y.sophisticatedstorage.client.init.ModItemsClient;
import net.p3pp3rf1y.sophisticatedstorage.client.init.StorageRenderers;
import net.p3pp3rf1y.sophisticatedstorage.client.init.StorageTints;

public class SophisticatedStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 26.3：原 ClientEventHandler 依赖已删除的渲染 / Fabric 事件 API，暂放于 src/parked/java，
		// 这里只保留屏幕与 HUD 注册（HUD 改用 26.3 的 HudElementRegistry）。
		ModBlocksClient.registerScreens();
		ModItemsClient.registerScreens();
		ClientKeybinds.register();
		registerScrollInput();
		// 26.3：木桶的木种/染料着色走 BlockTintSource 列表；箱子/潜影盒的外观完全由 BER 画，必须注册渲染器
		StorageTints.register();
		StorageRenderers.register();

		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "paintbrush_info"), PaintbrushOverlay.HUD_PAINTBRUSH_INFO);
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "tool_info"), ToolInfoOverlay.HUD_TOOL_INFO);

		// 26.3 resolves a tooltip component through ClientTooltipComponent.create, which only knows the vanilla ones;
		// without this hook any tooltip carrying the storage contents component (and every JEI ingredient tooltip that
		// inherits it) throws "Unknown TooltipComponent" and the client dies while drawing the tooltip.
		ClientTooltipComponentCallback.EVENT.register(SophisticatedStorageClient::createTooltipComponent);
	}

	/** Shift + 滚轮切换存储工具模式；返回非 PASS 时核心的原始输入钩子会吞掉这次滚动。 */
	private static void registerScrollInput() {
		ClientRawInputEvent.MOUSE_SCROLLED.register((minecraft, deltaX, deltaY) -> {
			if (minecraft.gui.screen() != null || deltaY == 0) {
				return InteractionResult.PASS;
			}
			LocalPlayer player = minecraft.player;
			if (player == null || !player.isShiftKeyDown() || player.getMainHandItem().getItem() != ModItems.STORAGE_TOOL.get()) {
				return InteractionResult.PASS;
			}
			ClientPlayNetworking.send(new ScrolledToolPayload(deltaY > 0));
			return InteractionResult.SUCCESS;
		});
	}

	@Nullable
	private static ClientTooltipComponent createTooltipComponent(TooltipComponent data) {
		if (data instanceof StorageContentsTooltip storageContentsTooltip) {
			return new net.p3pp3rf1y.sophisticatedstorage.client.render.ClientStorageContentsTooltip(storageContentsTooltip);
		}

		return null;
	}
}
