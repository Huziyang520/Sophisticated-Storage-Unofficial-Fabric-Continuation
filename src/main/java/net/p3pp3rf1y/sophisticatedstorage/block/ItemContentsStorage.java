package net.p3pp3rf1y.sophisticatedstorage.block;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.p3pp3rf1y.sophisticatedcore.SophisticatedCore;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ItemContentsStorage extends SavedData {
	private static final Identifier SAVED_DATA_NAME = Identifier.fromNamespaceAndPath(SophisticatedStorage.MOD_ID, "item_contents");
	private static final Codec<ItemContentsStorage> CODEC = CompoundTag.CODEC.xmap(ItemContentsStorage::load, ItemContentsStorage::saveToTag);
	public static final SavedDataType<ItemContentsStorage> TYPE = new SavedDataType<>(SAVED_DATA_NAME, ItemContentsStorage::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, CompoundTag> storageContents = new HashMap<>();
	private static final ItemContentsStorage clientStorageCopy = new ItemContentsStorage();

	private ItemContentsStorage() {
	}

	public static ItemContentsStorage get() {
		if (SophisticatedCore.isLogicalServerThread()) {
			MinecraftServer server = SophisticatedCore.getCurrentServer();
			if (server != null) {
				ServerLevel overworld = server.getLevel(Level.OVERWORLD);
				//noinspection ConstantConditions - by this time overworld is loaded
				SavedDataStorage storage = overworld.getDataStorage();
				return storage.computeIfAbsent(TYPE);
			}
		}
		return clientStorageCopy;
	}

	public static ItemContentsStorage load(CompoundTag nbt) {
		ItemContentsStorage storage = new ItemContentsStorage();
		readStorageContents(nbt, storage);
		return storage;
	}

	private static void readStorageContents(CompoundTag nbt, ItemContentsStorage storage) {
		ListTag storageContents = nbt.getListOrEmpty(nbt.contains("shulkerBoxContents") ? "shulkerBoxContents" : "storageContents");
		for (int i = 0; i < storageContents.size(); i++) {
			CompoundTag uuidContentsPair = storageContents.getCompoundOrEmpty(i);
			Optional<String> uuid = uuidContentsPair.getString("uuid");
			if (uuid.isEmpty()) {
				continue;
			}
			try {
				storage.storageContents.put(UUID.fromString(uuid.get()), uuidContentsPair.getCompoundOrEmpty("contents"));
			} catch (IllegalArgumentException e) {
				//skip entries with malformed uuid
			}
		}
	}

	private CompoundTag saveToTag() {
		CompoundTag ret = new CompoundTag();
		writeStorageContents(ret);
		return ret;
	}

	private void writeStorageContents(CompoundTag ret) {
		ListTag storageContentsNbt = new ListTag();
		for (Map.Entry<UUID, CompoundTag> entry : storageContents.entrySet()) {
			CompoundTag uuidContentsPair = new CompoundTag();
			uuidContentsPair.putString("uuid", entry.getKey().toString());
			uuidContentsPair.put("contents", entry.getValue());
			storageContentsNbt.add(uuidContentsPair);
		}
		ret.put("storageContents", storageContentsNbt);
	}

	public CompoundTag getOrCreateStorageContents(UUID storageUuid) {
		return storageContents.computeIfAbsent(storageUuid, uuid -> {
			setDirty();
			return new CompoundTag();
		});
	}

	/** 该 uuid 的内容是否已经在本侧（服务端存档 / 客户端同步副本）里，决定快捷提示显示内容还是「同步中」。 */
	public boolean hasStorageContents(UUID storageUuid) {
		CompoundTag contents = storageContents.get(storageUuid);
		return contents != null && !contents.isEmpty();
	}

	public void removeStorageContents(UUID storageUuid) {
		storageContents.remove(storageUuid);
		setDirty();
	}

	public void setStorageContents(UUID storageUuid, CompoundTag contents) {
		storageContents.put(storageUuid, contents);
		setDirty();
	}
}
