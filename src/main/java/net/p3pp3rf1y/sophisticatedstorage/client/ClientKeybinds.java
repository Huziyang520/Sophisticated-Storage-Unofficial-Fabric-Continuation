package net.p3pp3rf1y.sophisticatedstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.StorageTranslationHelper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.network.ScrolledToolPayload;

/**
 * 26.3 的 {@link KeyMapping} 分类不再是字符串，而是带 {@link Identifier} 的 {@link KeyMapping.Category}，
 * 分类标签的翻译键由 {@code Category#label()} 生成，形如 {@code key.category.<namespace>.<path>}。
 */
public final class ClientKeybinds {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "main"));

	/** 切换存储工具模式（默认 H），与滚轮下发的是同一个包 */
	public static final KeyMapping CYCLE_TOOL_MODE = new KeyMapping(
			StorageTranslationHelper.INSTANCE.translKeybind("cycle_tool_mode"),
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_H,
			CATEGORY);

	private ClientKeybinds() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(CYCLE_TOOL_MODE);
		ClientTickEvents.END_CLIENT_TICK.register(ClientKeybinds::onEndClientTick);
	}

	private static void onEndClientTick(Minecraft minecraft) {
		// 无论是否能触发都要消费点击，否则在 GUI 里按下的 H 会在关闭界面后集中生效
		while (CYCLE_TOOL_MODE.consumeClick()) {
			LocalPlayer player = minecraft.player;
			// 26.3 把当前屏幕移到了 Gui：Minecraft 上已没有 screen 字段
			if (player == null || minecraft.gui.screen() != null) {
				continue;
			}
			if (player.getMainHandItem().getItem() != ModItems.STORAGE_TOOL.get()) {
				continue;
			}
			// 服务端 handler 只接受主手为存储工具的包，语义与滚轮切换一致（next = true）
			ClientPlayNetworking.send(new ScrolledToolPayload(true));
		}
	}
}
