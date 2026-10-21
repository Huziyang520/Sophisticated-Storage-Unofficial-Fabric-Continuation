package net.p3pp3rf1y.sophisticatedstorage.item;

import net.minecraft.world.inventory.ClickAction;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.p3pp3rf1y.sophisticatedcore.api.IStashStorageItem;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TranslationHelper;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ShulkerBoxItem extends StorageBlockItem implements IStashStorageItem {
	public ShulkerBoxItem(Block block) {
		this(block, new Properties().stacksTo(1));
	}

	public ShulkerBoxItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, context, display, tooltip, tooltipFlag);
		if (tooltipFlag.isAdvanced()) {
			HolderLookup.Provider registries = context.registries();
			if (registries != null) {
				StackStorageWrapper.fromStack(registries, stack).getContentsUuid().ifPresent(uuid -> tooltip.accept(Component.literal("UUID: " + uuid).withStyle(ChatFormatting.DARK_GRAY)));
			}
		}
		if (!GuiHelper.isShiftDown()) {
			tooltip.accept(Component.translatable(
					TranslationHelper.INSTANCE.translItemTooltip("storage") + ".press_for_contents",
					Component.translatable(TranslationHelper.INSTANCE.translItemTooltip("storage") + ".shift").withStyle(ChatFormatting.AQUA)
			).withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			return Optional.ofNullable(StorageItemClient.getTooltipImage(stack));
		}
		return Optional.empty();
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public void onDestroyed(ItemEntity itemEntity) {
		Level level = itemEntity.level();
		if (level.isClientSide()) {
			return;
		}
		ItemStack stack = itemEntity.getItem();
		StackStorageWrapper storageWrapper = StackStorageWrapper.fromStack(level.registryAccess(), stack);
		InventoryHelper.dropItems(storageWrapper.getInventoryHandler(), level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
		InventoryHelper.dropItems(storageWrapper.getUpgradeHandler(), level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
	}

	@Override
	public Optional<TooltipComponent> getInventoryTooltip(ItemStack stack) {
		return Optional.of(new StorageContentsTooltip(stack));
	}

	public ItemStack stash(HolderLookup.Provider registries, ItemStack storageStack, ItemStack stack, boolean simulate) {
		StackStorageWrapper wrapper = StackStorageWrapper.fromStack(registries, storageStack);
		if (wrapper.getContentsUuid().isEmpty()) {
			wrapper.setContentsUuid(UUID.randomUUID());
		}
		return wrapper.getInventoryForUpgradeProcessing().insertItem(stack, simulate);
	}

	@Override
	public StashResult getItemStashable(HolderLookup.Provider registries, ItemStack storageStack, ItemStack stack) {
		StackStorageWrapper wrapper = StackStorageWrapper.fromStack(registries, storageStack);

		if (wrapper.getInventoryForUpgradeProcessing().insertItem(stack, true).getCount() == stack.getCount()) {
			return StashResult.NO_SPACE;
		}
		if (wrapper.getInventoryHandler().getSlotTracker().getItems().contains(stack.getItem()) || wrapper.getSettingsHandler().getTypeCategory(MemorySettingsCategory.class).matchesFilter(stack)) {
			return StashResult.MATCH_AND_SPACE;
		}

		return StashResult.SPACE;
	}

	public int getNumberOfInventorySlots(HolderLookup.Provider registries, ItemStack shulkerBoxStack) {
		int defaultNumberOfInventorySlots = StackStorageWrapper.fromStack(registries, shulkerBoxStack).getDefaultNumberOfInventorySlots();
		return Math.max(shulkerBoxStack.sophisticatedCore_getOrDefault(ModCoreDataComponents.NUMBER_OF_INVENTORY_SLOTS, defaultNumberOfInventorySlots), defaultNumberOfInventorySlots);
	}

	public int getNumberOfUpgradeSlots(HolderLookup.Provider registries, ItemStack shulkerBoxStack) {
		int defaultNumberOfUpgradeSlots = StackStorageWrapper.fromStack(registries, shulkerBoxStack).getDefaultNumberOfUpgradeSlots();
		return Math.max(shulkerBoxStack.sophisticatedCore_getOrDefault(ModCoreDataComponents.NUMBER_OF_UPGRADE_SLOTS, defaultNumberOfUpgradeSlots), defaultNumberOfUpgradeSlots);
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack storageStack, Slot slot, ClickAction action, Player player) {
		if (hasCreativeScreenContainerOpen(player) || storageStack.getCount() > 1 || !slot.mayPickup(player) || slot.getItem().isEmpty() || action != ClickAction.SECONDARY) {
			return super.overrideStackedOnOther(storageStack, slot, action, player);
		}

		if (player.level().isClientSide()) {
			return predictHandled();
		}

		ItemStack stackToStash = slot.getItem();
		ItemStack stashResult = stash(player.level().registryAccess(), storageStack, stackToStash, true);
		if (stashResult.getCount() != stackToStash.getCount()) {
			int countToTake = stackToStash.getCount() - stashResult.getCount();
			ItemStack takeResult = slot.safeTake(countToTake, countToTake, player);
			stash(player.level().registryAccess(), storageStack, takeResult, false);
			return true;
		}

		return super.overrideStackedOnOther(storageStack, slot, action, player);
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack storageStack, ItemStack otherStack, Slot slot, ClickAction action, Player player, SlotAccess carriedAccess) {
		if (hasCreativeScreenContainerOpen(player) || storageStack.getCount() > 1 || !slot.mayPlace(storageStack) || action != ClickAction.SECONDARY) {
			return super.overrideOtherStackedOnMe(storageStack, otherStack, slot, action, player, carriedAccess);
		}

		if (player.level().isClientSide()) {
			return predictHandled();
		}

		ItemStack result = stash(player.level().registryAccess(), storageStack, otherStack, false);
		if (result.getCount() != otherStack.getCount()) {
			carriedAccess.set(result);
			return true;
		}

		return super.overrideOtherStackedOnMe(storageStack, otherStack, slot, action, player, carriedAccess);
	}

	/**
	 * 客户端只预测「这次右键被处理了」，绝不写容器内容。
	 * <p>
	 * 26.3 的 {@code MultiPlayerGameMode#handleInventoryMouseClick} 会先在本机跑一遍 {@code menu.clicked}，
	 * 再把这个点击发给服务端。以前这里在客户端也真的把物品塞进 {@code StackStorageWrapper}：
	 * 物品从光标上消失、客户端本地副本多出一份、物品上还被写了一个只存在于客户端的随机 uuid。
	 * 服务端随后用**另一个** uuid 重做一次；只要服务端这次点击被丢弃（失步重同步 / 距离校验），
	 * 客户端已经空了而服务端从未收到 —— 玩家看到的就是「GUI 内右键放入容器，物品直接消失」。
	 * 改成纯预测后，内容只由服务端写，客户端等槽位广播。
	 */
	private boolean predictHandled() {
		return true;
	}

	private boolean hasCreativeScreenContainerOpen(Player player) {
		return player.level().isClientSide() && player.containerMenu instanceof CreativeModeInventoryScreen.ItemPickerMenu;
	}
}
