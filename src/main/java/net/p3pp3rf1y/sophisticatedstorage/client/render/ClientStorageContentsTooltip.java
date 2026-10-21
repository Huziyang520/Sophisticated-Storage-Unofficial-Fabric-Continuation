package net.p3pp3rf1y.sophisticatedstorage.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.client.render.ClientStorageContentsTooltipBase;
import net.p3pp3rf1y.sophisticatedcore.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.block.ItemContentsStorage;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageContentsTooltip;
import net.p3pp3rf1y.sophisticatedstorage.network.RequestStorageContentsPayload;
import net.p3pp3rf1y.sophisticatedstorage.util.StorageDataHelper;

import java.util.UUID;

public class ClientStorageContentsTooltip extends ClientStorageContentsTooltipBase {
	private final ItemStack storageItem;

	@SuppressWarnings("unused")
	//parameter needs to be there so that addListener logic would know which event this method listens to
	public static void onWorldLoad(Minecraft client, ClientLevel world) {
		refreshContents();
		lastRequestTime = 0;
	}

	@Override
	protected IStorageWrapper getStorageWrapper() {
		return StackStorageWrapper.fromStack(Minecraft.getInstance().level.registryAccess(), storageItem);
	}

	/**
	 * 内容是否已经可读。物品自带方块实体数据（掉落 / 打包 / 创造取物写进去的那份）时，客户端不需要任何网络包
	 * 就能显示真实内容 —— 这也是「按住 shift 恒显示空容器」的修法：以前只看服务端 uuid 副本，
	 * 没同步到就退化成空。
	 */
	@Override
	protected boolean hasContentsData(IStorageWrapper wrapper) {
		if (StorageDataHelper.hasStorageDataInStack(storageItem)) {
			return true;
		}
		UUID uuid = StorageDataHelper.contentsUuidOf(storageItem);
		// 没有 uuid 就是真的空容器（新盒子），有 uuid 但本侧还没有数据才是「同步中」
		return uuid == null || ItemContentsStorage.get().hasStorageContents(uuid);
	}

	public ClientStorageContentsTooltip(StorageContentsTooltip tooltip) {
		storageItem = tooltip.getStorageItem();
	}

	@Override
	protected void sendInventorySyncRequest(UUID uuid) {
		PacketDistributor.sendToServer(new RequestStorageContentsPayload(uuid));
	}
}
