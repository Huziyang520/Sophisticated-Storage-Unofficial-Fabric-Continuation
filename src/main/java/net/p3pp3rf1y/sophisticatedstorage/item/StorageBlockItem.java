package net.p3pp3rf1y.sophisticatedstorage.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.p3pp3rf1y.sophisticatedcore.extensions.component.SophisticatedDataComponentHolder;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.util.BlockItemBase;
import net.p3pp3rf1y.sophisticatedstorage.block.ITintableBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModDataComponents;

import java.util.Optional;

import static net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity.STORAGE_WRAPPER_TAG;

public class StorageBlockItem extends BlockItemBase implements ITintableBlockItem {

	public StorageBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static Optional<CompoundTag> getEntityWrapperTagFromStack(SophisticatedDataComponentHolder componentHolder) {
		// 26.3：核心的 sophisticatedCore_get 泛型推断在此处过于严格，直接走原版 DataComponentHolder
		net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>> entityData =
				((net.minecraft.core.component.DataComponentHolder) componentHolder).get(DataComponents.BLOCK_ENTITY_DATA);
		if (entityData == null) {
			return Optional.empty();
		}
		return Optional.of(entityData.copyTagWithoutId().getCompoundOrEmpty(STORAGE_WRAPPER_TAG));
	}

	public static Optional<Integer> getMainColorFromComponentHolder(SophisticatedDataComponentHolder componentHolder) {
		Optional<CompoundTag> wrapperTag = getEntityWrapperTagFromStack(componentHolder);
		if (wrapperTag.isPresent() && wrapperTag.get().contains(StorageWrapper.MAIN_COLOR_TAG)) {
			return Optional.of(wrapperTag.get().getIntOr(StorageWrapper.MAIN_COLOR_TAG, 0));
		}
		return Optional.ofNullable(((net.minecraft.core.component.DataComponentHolder) componentHolder).<Integer>get(ModCoreDataComponents.MAIN_COLOR.get()));
	}

	public static Optional<Integer> getAccentColorFromComponentHolder(SophisticatedDataComponentHolder componentHolder) {
		Optional<CompoundTag> wrapperTag = getEntityWrapperTagFromStack(componentHolder);
		if (wrapperTag.isPresent() && wrapperTag.get().contains(StorageWrapper.ACCENT_COLOR_TAG)) {
			return Optional.of(wrapperTag.get().getIntOr(StorageWrapper.ACCENT_COLOR_TAG, 0));
		}
		return Optional.ofNullable(((net.minecraft.core.component.DataComponentHolder) componentHolder).<Integer>get(ModCoreDataComponents.ACCENT_COLOR.get()));
	}

	public static void setNumberOfInventorySlots(ItemStack storageStack, int numberOfInventorySlots) {
		storageStack.sophisticatedCore_set(ModCoreDataComponents.NUMBER_OF_INVENTORY_SLOTS, numberOfInventorySlots);
	}

	public static  void setNumberOfUpgradeSlots(ItemStack storageStack, int numberOfUpgradeSlots) {
		storageStack.sophisticatedCore_set(ModCoreDataComponents.NUMBER_OF_UPGRADE_SLOTS, numberOfUpgradeSlots);
	}

	public static int getNumberOfInventorySlots(ItemStack storageStack) {
		return storageStack.sophisticatedCore_getOrDefault(ModCoreDataComponents.NUMBER_OF_INVENTORY_SLOTS, 0);
	}

	public static int getNumberOfUpgradeSlots(ItemStack storageStack) {
		return storageStack.sophisticatedCore_getOrDefault(ModCoreDataComponents.NUMBER_OF_UPGRADE_SLOTS, 0);
	}

	@Override
	public void setMainColor(ItemStack storageStack, int mainColor) {
		storageStack.sophisticatedCore_set(ModCoreDataComponents.MAIN_COLOR, mainColor);
	}

	@Override
	public Optional<Integer> getMainColor(ItemStack storageStack) {
		return StorageBlockItem.getMainColorFromComponentHolder(storageStack);
	}

	@Override
	public void setAccentColor(ItemStack storageStack, int accentColor) {
		storageStack.sophisticatedCore_set(ModCoreDataComponents.ACCENT_COLOR, accentColor);
	}

	@Override
	public void removeMainColor(ItemStack stack) {
		stack.sophisticatedCore_remove(ModCoreDataComponents.MAIN_COLOR);
	}

	@Override
	public void removeAccentColor(ItemStack stack) {
		stack.sophisticatedCore_remove(ModCoreDataComponents.ACCENT_COLOR);
	}

	@Override
	public Optional<Integer> getAccentColor(ItemStack stack) {
		return StorageBlockItem.getAccentColorFromComponentHolder(stack);
	}

	public static boolean showsTier(ItemStack stack) {
		return stack.sophisticatedCore_getOrDefault(ModDataComponents.SHOWS_TIER, true);
	}

	public static void setShowsTier(ItemStack stack, boolean showsTier) {
		if (showsTier) {
			stack.sophisticatedCore_remove(ModDataComponents.SHOWS_TIER);
		} else {
			stack.sophisticatedCore_set(ModDataComponents.SHOWS_TIER, false);
		}
	}
}
