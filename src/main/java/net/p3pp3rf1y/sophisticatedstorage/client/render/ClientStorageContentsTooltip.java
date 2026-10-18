package net.p3pp3rf1y.sophisticatedstorage.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.client.render.ClientStorageContentsTooltipBase;
import net.p3pp3rf1y.sophisticatedcore.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageContentsTooltip;
import net.p3pp3rf1y.sophisticatedstorage.network.RequestStorageContentsPayload;

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

	public ClientStorageContentsTooltip(StorageContentsTooltip tooltip) {
		storageItem = tooltip.getStorageItem();
	}

	@Override
	protected void sendInventorySyncRequest(UUID uuid) {
		PacketDistributor.sendToServer(new RequestStorageContentsPayload(uuid));
	}
}
