package net.p3pp3rf1y.sophisticatedstorage.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageToolItem;

public record ScrolledToolPayload(boolean next) implements CustomPacketPayload {
	public static final Type<ScrolledToolPayload> TYPE = new Type<>(SophisticatedStorage.getRL("scrolled_tool"));
	public static final StreamCodec<ByteBuf, ScrolledToolPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			ScrolledToolPayload::next,
			ScrolledToolPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handlePayload(ScrolledToolPayload payload, ServerPlayNetworking.Context context) {
		ItemStack stack = context.player().getMainHandItem();
		if (stack.getItem() == ModItems.STORAGE_TOOL.get()) {
			StorageToolItem.cycleMode(stack, payload.next);
			// 回执：客户端 HUD 读的是本地组件，不回包的话它会一直显示旧模式（第 5 轮第 15 条）
			PacketDistributor.sendToPlayer(context.player(), new ToolModePayload(StorageToolItem.getMode(stack).ordinal()));
		}
	}
}
