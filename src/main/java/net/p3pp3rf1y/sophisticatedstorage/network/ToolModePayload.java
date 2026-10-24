package net.p3pp3rf1y.sophisticatedstorage.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageToolItem;

/**
 * 服务端 → 客户端：存储工具当前模式。
 * <p>
 * {@link ScrolledToolPayload}（H 键 / 滚轮）只在<b>服务端</b>的手持物组件上切模式，客户端那份 stack 要等下一次
 * 物品栏同步才会变，所以 HUD（{@code ToolInfoOverlay}，读的是客户端本地组件）会停在旧模式 —— 玩家看到的就是
 * 「按了没反应」。这条回执把结果送回客户端，本地立刻写回组件，HUD 当帧就显示新模式与对应提示。
 * <p>
 * 对照上游 26.3 的做法：模式切换后由服务端把结果同步回客户端，而不是让客户端自己猜。
 */
public record ToolModePayload(int modeOrdinal) implements CustomPacketPayload {
	public static final Type<ToolModePayload> TYPE = new Type<>(SophisticatedStorage.getRL("tool_mode"));
	public static final StreamCodec<ByteBuf, ToolModePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			ToolModePayload::modeOrdinal,
			ToolModePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Environment(EnvType.CLIENT)
	public static void handlePayload(ToolModePayload payload, ClientPlayNetworking.Context context) {
		StorageToolItem.setModeFromServer(context.player().getMainHandItem(), payload.modeOrdinal());
	}
}
