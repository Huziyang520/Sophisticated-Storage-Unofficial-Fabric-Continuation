package net.p3pp3rf1y.sophisticatedstorage.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedcore.controller.IControllerBoundable;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelMaterial;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.ITintableBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.block.ItemContentsStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockBase;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.item.BarrelBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.ChestBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

import org.jetbrains.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 容器数据与物品之间的唯一一对读写入口。
 * <p>
 * 以前「放置 / 破坏 / 打包 / 拆包 / 创造取物」五条路径各自写一半数据（有的只写颜色、有的只写 uuid、有的什么
 * 都不写），于是出现「染色容器挖掉掉色」「装好东西挖下来再放下就空」「shift 提示恒为空」这类数据丢失。
 * 现在所有路径都必须经过这里，任何新路径都不得再自行读写容器 NBT。
 * <p>
 * 写入口把整份方块实体数据同时写到两处：
 * <ul>
 *   <li>{@link DataComponents#BLOCK_ENTITY_DATA} —— 物品自带的方块实体数据。它随物品一起走（背包里、箱子里、
 *       掉落在地上、被物品栏移动），客户端不需要任何网络包就能读到内容；原版
 *       {@code BlockItem#updateCustomBlockEntityTag} 在放置时还会自动把它灌回方块实体。这也是
 *       {@link StorageBlockItem#getEntityWrapperTagFromStack} / {@code StackStorageWrapper} 一直期望存在的
 *       读取源，只是此前没有任何代码写过它。</li>
 *   <li>{@code ItemContentsStorage[uuid]} + {@code storage_uuid} 组件 —— 保留既有的按 uuid 同步/请求链路
 *       （快捷提示的按需同步，以及旧存档里只有 uuid 的物品）。</li>
 * </ul>
 * 读入口优先用物品自带的方块实体数据，缺失时回落到 uuid 副本；读完后删掉 uuid 副本，避免同一份内容在两个键
 * 下分叉（分叉时「挖下再放下」会读到旧的那一份，也就是玩家看到的物品消失）。
 */
public final class StorageDataHelper {
	/** {@link WoodStorageBlockEntity} 写在 {@code sophisticatedstorage_data} 里的打包标记。 */
	private static final String PACKED_TAG = "packed";

	private StorageDataHelper() {
	}

	/**
	 * 把方块实体的完整数据（颜色、木种、槽数、升级、内容、打包状态）写进物品。
	 * 破坏掉落、创造取物、打包、以及任何「把方块变成物品」的路径都必须调用它。
	 */
	public static void writeStorageDataToStack(ItemStack stack, StorageBlockEntity blockEntity) {
		HolderLookup.Provider registries = registriesOf(blockEntity);
		StorageWrapper storageWrapper = blockEntity.getStorageWrapper();

		// uuid 必须先固化再存档：方块实体的 uuid 覆盖实现会在缺失时生成并落盘，
		// 这样物品和方块两侧指向同一个内容键，放回去以后不会再生成第二个键。
		UUID contentsUuid = storageWrapper.getContentsUuid().orElseGet(UUID::randomUUID);

		CompoundTag dataTag = toStorageTag(blockEntity, registries);
		dataTag.remove(IControllerBoundable.CONTROLLER_POS_TAG);

		// 1) 物品自带的方块实体数据（客户端可读、放置时原版自动回灌）
		stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(blockEntity.getType(), dataTag));
		// 2) 按 uuid 的副本（既有同步/请求链路 + 旧存档兼容）
		ItemContentsStorage.get().setStorageContents(contentsUuid, dataTag);
		stack.sophisticatedCore_set(ModCoreDataComponents.STORAGE_UUID, contentsUuid);

		writeTintAndTypeComponents(stack, blockEntity, storageWrapper);
	}

	/**
	 * 把物品上的容器数据灌回方块实体。放置（含打包后放下、拆包）路径必须调用它。
	 * <p>
	 * 客户端不执行：客户端的方块实体只是渲染副本，数据由服务端同步。
	 */
	public static void readStorageDataFromStack(ItemStack stack, StorageBlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) {
			return;
		}

		HolderLookup.Provider registries = level.registryAccess();
		Optional<UUID> contentsUuid = Optional.ofNullable(stack.sophisticatedCore_get(ModCoreDataComponents.STORAGE_UUID));

		CompoundTag dataTag = getEntityDataTag(stack, blockEntity.getType()).orElse(null);
		if (dataTag == null) {
			dataTag = contentsUuid.map(uuid -> ItemContentsStorage.get().getOrCreateStorageContents(uuid))
					.filter(tag -> !tag.isEmpty())
					.orElse(null);
		}
		if (dataTag != null) {
			NbtTransferHelper.loadAdditional(blockEntity, dataTag, registries);
		}

		if (contentsUuid.isPresent()) {
			// 内容已经搬进方块实体，同一个键不能再指向旧副本，否则再挖下来会读到两份不同的数据
			blockEntity.getStorageWrapper().setContentsUuid(contentsUuid.get());
			ItemContentsStorage.get().removeStorageContents(contentsUuid.get());
		}

		readTintAndTypeComponents(stack, blockEntity);
	}

	/** 物品自带的方块实体数据（已去掉 id），类型不匹配时视为没有。 */
	public static Optional<CompoundTag> getEntityDataTag(ItemStack stack, BlockEntityType<?> expectedType) {
		TypedEntityData<BlockEntityType<?>> entityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (entityData == null || entityData.type() != expectedType) {
			return Optional.empty();
		}
		return Optional.of(entityData.copyTagWithoutId());
	}

	/** 物品是否自带容器内容数据（掉落/打包/创造取物写进去的那份，客户端可直接读，不必等网络包）。 */
	public static boolean hasStorageDataInStack(ItemStack stack) {
		return StorageBlockItem.getEntityWrapperTagFromStack(stack).isPresent();
	}

	@Nullable
	public static UUID contentsUuidOf(ItemStack stack) {
		return stack.sophisticatedCore_get(ModCoreDataComponents.STORAGE_UUID);
	}

	/**
	 * 内容快照：打包/拆包前后做断言，确认没有任何一条路径重建了 handler 或写入了空 Size。
	 * 只比较内容本身，剔除打包动作自己会改的字段（packed）以及控制器位置。
	 */
	public static CompoundTag snapshotContents(StorageBlockEntity blockEntity) {
		CompoundTag tag = blockEntity.saveWithoutMetadata(registriesOf(blockEntity));
		tag.remove(IControllerBoundable.CONTROLLER_POS_TAG);
		if (tag.getCompound(StorageBlockEntity.DATA_TAG).isPresent()) {
			CompoundTag dataTag = tag.getCompoundOrEmpty(StorageBlockEntity.DATA_TAG).copy();
			dataTag.remove(PACKED_TAG);
			// 打包动作本身会置这个标志（让客户端重画方块），它不是容器内容
			dataTag.remove("updateBlockRender");
			tag.put(StorageBlockEntity.DATA_TAG, dataTag);
		}
		return tag;
	}

	/**
	 * 打包/拆包断言：内容必须逐字节一致。不一致说明打包路径又动了 handler 或写了空 Size —— 那正是「打包后物品
	 * 被清空」的根因，这里直接报错，让它出现在日志里而不是等玩家发现物品没了。
	 *
	 * @return 内容是否一致
	 */
	public static boolean assertContentsUnchanged(String action, CompoundTag before, StorageBlockEntity blockEntity) {
		CompoundTag after = snapshotContents(blockEntity);
		if (before.equals(after)) {
			return true;
		}
		SophisticatedStorage.LOGGER.error("[storage-data] {} changed the container contents at {}: before={} after={}",
				action, blockEntity.getBlockPos(), before, after);
		return false;
	}

	private static void writeTintAndTypeComponents(ItemStack stack, StorageBlockEntity blockEntity, StorageWrapper storageWrapper) {
		if (stack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
			if (storageWrapper.hasMainColor()) {
				tintableBlockItem.setMainColor(stack, storageWrapper.getMainColor());
			} else {
				tintableBlockItem.removeMainColor(stack);
			}
			if (storageWrapper.hasAccentColor()) {
				tintableBlockItem.setAccentColor(stack, storageWrapper.getAccentColor());
			} else {
				tintableBlockItem.removeAccentColor(stack);
			}
		}

		if (stack.getItem() instanceof WoodStorageBlockItem) {
			// 染满的容器没有木种（与 WoodStorageBlockItem#isFullyTinted、方块实体的显示名一致）
			if (isFullyTinted(storageWrapper)) {
				WoodStorageBlockItem.removeWoodTypeComponent(stack);
			} else {
				resolveWoodType(blockEntity).ifPresent(woodType -> WoodStorageBlockItem.setWoodType(stack, woodType));
			}
		}

		StorageBlockItem.setNumberOfInventorySlots(stack, storageWrapper.getInventoryHandler().getSlotCount());
		StorageBlockItem.setNumberOfUpgradeSlots(stack, storageWrapper.getUpgradeHandler().getSlotCount());
		StorageBlockItem.setShowsTier(stack, blockEntity.shouldShowTier());

		if (blockEntity.hasCustomName()) {
			stack.set(DataComponents.CUSTOM_NAME, blockEntity.getCustomName());
		}

		if (blockEntity instanceof WoodStorageBlockEntity woodStorageBlockEntity) {
			WoodStorageBlockItem.setPacked(stack, woodStorageBlockEntity.isPacked());
		}

		if (blockEntity.getBlockState().hasProperty(ChestBlock.TYPE)
				&& blockEntity.getBlockState().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			ChestBlockItem.setDoubleChest(stack, true);
		}

		if (blockEntity instanceof BarrelBlockEntity barrelBlockEntity) {
			BarrelBlockItem.setFlatTop(stack, blockEntity.getBlockState().getValue(BarrelBlock.FLAT_TOP));
			Map<BarrelMaterial, Identifier> materials = barrelBlockEntity.getMaterials();
			if (!materials.isEmpty()) {
				BarrelBlockItem.setMaterials(stack, materials);
			}
		}
	}

	private static void readTintAndTypeComponents(ItemStack stack, StorageBlockEntity blockEntity) {
		StorageWrapper storageWrapper = blockEntity.getStorageWrapper();
		if (stack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
			// 颜色永远以物品组件为准：方块实体里没颜色而物品上有，就是「挖掉后掉色」的那一半
			storageWrapper.setColors(tintableBlockItem.getMainColor(stack).orElse(-1), tintableBlockItem.getAccentColor(stack).orElse(-1));
		}

		if (blockEntity instanceof WoodStorageBlockEntity woodStorageBlockEntity) {
			WoodStorageBlockItem.getWoodType(stack).ifPresent(woodStorageBlockEntity::setWoodType);
			woodStorageBlockEntity.setPacked(WoodStorageBlockItem.isPacked(stack));
		}

		int inventorySlots = StorageBlockItem.getNumberOfInventorySlots(stack);
		if (inventorySlots > 0 && storageWrapper.getInventoryHandler().getSlotCount() != inventorySlots) {
			blockEntity.changeStorageSize(inventorySlots - storageWrapper.getInventoryHandler().getSlotCount(), 0);
		}
		int upgradeSlots = StorageBlockItem.getNumberOfUpgradeSlots(stack);
		if (upgradeSlots > 0 && storageWrapper.getUpgradeHandler().getSlotCount() != upgradeSlots) {
			blockEntity.changeStorageSize(0, upgradeSlots - storageWrapper.getUpgradeHandler().getSlotCount());
		}

		if (blockEntity instanceof BarrelBlockEntity barrelBlockEntity) {
			Map<BarrelMaterial, Identifier> materials = BarrelBlockItem.getMaterials(stack);
			if (!materials.isEmpty()) {
				barrelBlockEntity.setMaterials(new EnumMap<>(materials));
			}
		}
	}

	private static boolean isFullyTinted(StorageWrapper storageWrapper) {
		return storageWrapper.hasMainColor() && storageWrapper.hasAccentColor();
	}

	private static Optional<WoodType> resolveWoodType(StorageBlockEntity blockEntity) {
		if (blockEntity instanceof WoodStorageBlockEntity woodStorageBlockEntity) {
			Optional<WoodType> woodType = woodStorageBlockEntity.getWoodType();
			if (woodType.isPresent()) {
				return woodType;
			}
			// 方块实体里没有木种（旧存档 / 同步数据缺失）时回落到方块状态的 WOOD：
			// 掉落物不该因为这份副本缺失就变回默认木种。染色容器优先颜色，不写木种。
			if (blockEntity.getBlockState().hasProperty(BarrelBlock.WOOD)) {
				return Optional.of(WoodStorageBlockBase.woodTypeAt(blockEntity.getBlockState().getValue(BarrelBlock.WOOD)));
			}
		}
		return Optional.empty();
	}

	private static HolderLookup.Provider registriesOf(StorageBlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		return level == null ? RegistryAccess.EMPTY : level.registryAccess();
	}

	/**
	 * 用于写进物品的方块实体数据。木制容器走 {@link WoodStorageBlockEntity#getStorageContentsTag()}，
	 * 它会剔除「双箱另一半的位置」这类只对世界里那个方块成立的字段（否则物品放下后会指向一个不存在的另一半）。
	 */
	private static CompoundTag toStorageTag(StorageBlockEntity blockEntity, HolderLookup.Provider registries) {
		if (blockEntity instanceof WoodStorageBlockEntity woodStorageBlockEntity && blockEntity.getLevel() != null) {
			return woodStorageBlockEntity.getStorageContentsTag();
		}
		return blockEntity.saveWithoutMetadata(registries);
	}
}
