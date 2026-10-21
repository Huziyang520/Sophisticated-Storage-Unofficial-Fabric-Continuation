package net.p3pp3rf1y.sophisticatedstorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.util.*;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class LimitedBarrelBlockEntity extends BarrelBlockEntity implements ICountDisplay, IFillLevelDisplay {
	private static final String SLOT_COUNTS_TAG = "slotCounts";
	private static final String SLOT_FILL_LEVELS_TAG = "slotFillLevels";
	public static final Consumer<VoidUpgradeWrapper> VOID_UPGRADE_VOIDING_OVERFLOW_OF_EVERYTHING_BY_DEFAULT = voidUpgrade -> {
		voidUpgrade.getFilterLogic().setAllowByDefault(false);
		voidUpgrade.setShouldVoidOverflowDefaultOrLoadFromNbt(true);
	};
	public static final String STORAGE_TYPE = "limited_barrel";
	private long lastDepositTime = -100;

	private final List<Integer> slotCounts = new ArrayList<>();
	private final List<Float> slotFillLevels = new ArrayList<>();
	private Map<Integer, DyeColor> slotColors = new HashMap<>();
	private boolean showCounts = true;
	private boolean showFillLevels = false;

	public LimitedBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(pos, state, ModBlocks.LIMITED_BARREL_BLOCK_ENTITY_TYPE.get());
		registerUpgradeDefaults();
		// 26.3：构造期 getBlockState() 还拿不到方块状态（要等 setLevel 才注入），
		// 此时 getInventoryHandler() 会按 getDefaultNumberOfInventorySlots() = 0 建出 0 槽的 handler，
		// 打开界面时 slotCount 恒为 0 → LimitedBarrelScreen 直接抛 IllegalStateException。
		// 因此把监听器注册推迟到 setLevel。
	}

	@Override
	public void setLevel(net.minecraft.world.level.Level level) {
		super.setLevel(level);
		registerClientNotificationOnCountChange();
	}

	@Override
	public void sophisticatedCore_onLoad() {
		super.sophisticatedCore_onLoad();
		// 旧存档里的限类桶可能是在没有 setupDefaultSettings 的版本放下的，展示槽一个都没选，
		// 那样渲染数据里永远不会有物品（世界里就看不到内容）。放置时的设置只对新方块有效，
		// 所以这里在每次载入时补一次（上游也是在 onLoad 里补这个固定设置）。只在服务端做：
		// 客户端的展示物品是从服务端同步下来的，客户端重算会把它们清空。
		if (level != null && !level.isClientSide() && getBlockState().getBlock() instanceof IStorageBlock storageBlock) {
			LimitedBarrelBlock.setupDefaultSettings(getStorageWrapper(), storageBlock.getNumberOfInventorySlots());
		}
	}

	private void registerUpgradeDefaults() {
		getStorageWrapper().registerUpgradeDefaultsHandler(VoidUpgradeWrapper.class, VOID_UPGRADE_VOIDING_OVERFLOW_OF_EVERYTHING_BY_DEFAULT);
	}

	private boolean clientNotificationRegistered = false;

	private void registerClientNotificationOnCountChange() {
		if (clientNotificationRegistered) {
			return;
		}
		clientNotificationRegistered = true;
		getStorageWrapper().getInventoryHandler().addListener(slot -> WorldHelper.notifyBlockUpdate(this));
	}

	@Override
	protected void onUpgradeCachesInvalidated() {
		super.onUpgradeCachesInvalidated();
		registerClientNotificationOnCountChange();
	}

	@Override
	public boolean shouldShowCounts() {
		return showCounts;
	}

	@Override
	public boolean memorizesItemsWhenLocked() {
		return true;
	}

	@Override
	public boolean allowsEmptySlotsMatchingItemInsertsWhenLocked() {
		return false;
	}

	@Override
	public void toggleCountVisibility() {
		showCounts = !showCounts;
		setChanged();
		WorldHelper.notifyBlockUpdate(this);
	}

	@Override
	public List<Integer> getSlotCounts() {
		return slotCounts;
	}

	@Override
	public List<Float> getSlotFillLevels() {
		return slotFillLevels;
	}

	@Override
	public float getSlotFillPercentage(int slot) {
		return slot > -1 && slot < slotFillLevels.size() ? slotFillLevels.get(slot) : 0;
	}

	@Override
	public boolean shouldShowFillLevels() {
		return showFillLevels;
	}

	@Override
	public void toggleFillLevelVisibility() {
		showFillLevels = !showFillLevels;
		setChanged();
		WorldHelper.notifyBlockUpdate(this);
	}

	public boolean applyDye(int slot, ItemStack dyeStack, DyeColor dyeColor, boolean applyToAll) {
		if (slot < 0 || slot >= getStorageWrapper().getInventoryHandler().getSlotCount()) {
			return false;
		}

		StorageWrapper storageWrapper = getStorageWrapper();
		InventoryHandler invHandler = storageWrapper.getInventoryHandler();
		if (applyToAll) {
			boolean success = false;
			for (int i = 0; i < invHandler.getSlotCount(); i++) {
				success |= applyDye(i, dyeColor, invHandler);
			}
			if (!success) {
				return false;
			}
		} else {
			if (!applyDye(slot, dyeColor, invHandler)) {
				return false;
			}
		}
		setChanged();

		dyeStack.shrink(1);

		WorldHelper.notifyBlockUpdate(this);

		return true;
	}

	private boolean applyDye(int slot, DyeColor dyeColor, InventoryHandler invHandler) {
		ItemStack stackInSlot = invHandler.getStackInSlot(slot);
		if (stackInSlot.isEmpty() || dyeColor.equals(slotColors.get(slot))) {
			return false;
		}

		slotColors.put(slot, dyeColor);
		return true;
	}

	public int getSlotColor(int slot) {
		return slotColors.getOrDefault(slot, DyeColor.WHITE).getTextColor();
	}

	/**
	 * Whether the display slot holds something, <b>on both sides</b> - the left click take has to be recognised the
	 * same way on the server (which takes the item) and on the client (which decides whether to draw the destroy
	 * overlay), see {@code LimitedBarrelBlock#getDestroyProgress}.
	 * <p>
	 * The server reads the inventory handler; that copy is never sent to clients. The client therefore uses the two
	 * copies of the contents it does get: the synchronised slot counts ({@link #getSlotCounts()}, sent with every block
	 * entity update) and the render info display items (sent with the storage data). Either one is enough - if neither
	 * has arrived yet the barrel shows no contents either, so treating the slot as empty is consistent with what is
	 * drawn.
	 */
	public boolean hasItemsInSlot(int slot) {
		if (slot < 0) {
			return false;
		}
		if (level != null && !level.isClientSide()) {
			InventoryHandler handler = getStorageWrapper().getInventoryHandler();
			return slot < handler.getSlotCount() && !handler.getStackInSlot(slot).isEmpty();
		}
		if (slot < slotCounts.size() && slotCounts.get(slot) > 0) {
			return true;
		}
		return getStorageWrapper().getRenderInfo().getItemDisplayRenderInfo().getDisplayItems().stream()
				.anyMatch(displayItem -> displayItem.getSlotIndex() == slot && !displayItem.getItem().isEmpty());
	}

	public boolean depositItem(Player player, InteractionHand hand, ItemStack stackInHand, int slot) {
		//noinspection ConstantConditions
		long gameTime = getLevel().getGameTime();
		boolean doubleClick = gameTime - lastDepositTime < 10;
		lastDepositTime = gameTime;

		StorageWrapper storageWrapper = getStorageWrapper();
		InventoryHandler invHandler = storageWrapper.getInventoryHandler();
		ItemStack stackInSlot = invHandler.getStackInSlot(slot);

		MemorySettingsCategory memorySettings = getStorageWrapper().getSettingsHandler().getTypeCategory(MemorySettingsCategory.class);

		if (doubleClick) {
			return depositFromAllOfPlayersInventory(player, slot, invHandler, stackInSlot, memorySettings);
		}

		if (stackInSlot.isEmpty()) {
			if (invHandler.isItemValid(slot, stackInHand, player)) {
				int stackLimit = invHandler.getStackLimit(slot, stackInHand);
				invHandler.setStackInSlot(slot, stackInHand.split(stackLimit));
				if (isLocked()) {
					memorySettings.selectSlot(slot);
				}
				if (stackInHand.isEmpty()) {
					player.setItemInHand(hand, ItemStack.EMPTY);
				}
				return true;
			}
		} else {
			ItemStack result = invHandler.insertItemOnlyToSlot(slot, stackInHand, true);
			if (result.getCount() != stackInHand.getCount()) {
				result = invHandler.insertItemOnlyToSlot(slot, stackInHand, false);
				if (isLocked()) {
					memorySettings.selectSlot(slot);
				}
				player.setItemInHand(hand, result);
				return true;
			}
		}

		return false;
	}

	private boolean depositFromAllOfPlayersInventory(Player player, int slot, InventoryHandler invHandler, ItemStack stackInSlot, MemorySettingsCategory memorySettings) {
		AtomicBoolean success = new AtomicBoolean(false);
		Predicate<ItemStack> memoryItemMatches = itemStack -> memorySettings.isSlotSelected(slot) && memorySettings.matchesFilter(slot, itemStack);
		CapabilityHelper.runOnItemHandler(player, inventoryHandler -> InventoryHelper.iterate(inventoryHandler, (playerSlot, playerStack) -> {
			if ((stackInSlot.isEmpty() && (memoryItemMatches.test(playerStack) || invHandler.isFilterItem(playerStack.getItem())) || (!playerStack.isEmpty() && ItemStack.isSameItemSameComponents(stackInSlot, playerStack)))) {

				ItemStack result = invHandler.insertItemOnlyToSlot(slot, playerStack, true);
				if (result.getCount() < playerStack.getCount()) {
					ItemStack extracted = inventoryHandler.extractItem(playerSlot, playerStack.getCount() - result.getCount(), true);
					if (!extracted.isEmpty()) {
						invHandler.insertItemOnlyToSlot(slot, inventoryHandler.extractItem(playerSlot, extracted.getCount(), false), false);
						success.set(true);
					}
				}
			}
		}));
		return success.get();
	}

	boolean tryToTakeItem(Player player, int slot) {
		InventoryHandler inventoryHandler = getStorageWrapper().getInventoryHandler();
		ItemStack stackInSlot = inventoryHandler.getStackInSlot(slot);
		if (stackInSlot.isEmpty()) {
			return false;
		}

		int countToTake = player.isShiftKeyDown() ? Math.min(stackInSlot.getMaxStackSize(), stackInSlot.getCount()) : 1;
		ItemStack stackTaken = inventoryHandler.extractItem(slot, countToTake, false);

		if (player.getInventory().add(stackTaken)) {
			//noinspection ConstantConditions
			getLevel().playSound(null, getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, (RandHelper.getRandomMinusOneToOne(getLevel().getRandom()) * .7f + 1) * 2);
		} else {
			player.drop(stackTaken, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return true;
	}

	@Override
	void updateOpenBlockState(BlockState state, boolean open) {
		//noop
	}

	@Override
	protected CompoundTag createClientData() {
		// Same nested tag the client's loadAdditional reads (see StorageBlockEntity#createClientData); the slot counts
		// and fill levels have to live inside it or the client never sees them.
		CompoundTag updateTag = super.createClientData();
		ArrayList<Integer> sc = new ArrayList<>();
		ListTag sfl = new ListTag();
		InventoryHelper.iterate(getStorageWrapper().getInventoryHandler(), (slot, stack) -> {
			sc.add(slot, stack.getCount());
			sfl.add(slot, FloatTag.valueOf(stack.getCount() / (float) getStorageWrapper().getInventoryHandler().getStackLimit(slot, stack)));
		});
		int[] slotCountsArray = new int[sc.size()];
		for (int i = 0; i < sc.size(); i++) {
			slotCountsArray[i] = sc.get(i);
		}
		updateTag.putIntArray(SLOT_COUNTS_TAG, slotCountsArray);
		updateTag.put(SLOT_FILL_LEVELS_TAG, sfl);
		return updateTag;
	}

	@Override
	public void loadSynchronizedData(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadSynchronizedData(tag, registries);
		if (tag.contains(SLOT_COUNTS_TAG)) {
			int[] countsArray = tag.getIntArray(SLOT_COUNTS_TAG).orElse(new int[0]);
			if (slotCounts.size() != countsArray.length) {
				slotCounts.clear();
				for (int i = 0; i < countsArray.length; i++) {
					slotCounts.add(i, countsArray[i]);
				}
			} else {
				for (int i = 0; i < countsArray.length; i++) {
					slotCounts.set(i, countsArray[i]);
				}
			}
		}
		if (tag.contains(SLOT_FILL_LEVELS_TAG)) {
			ListTag fillLevelsList = tag.getListOrEmpty(SLOT_FILL_LEVELS_TAG);
			if (slotFillLevels.size() != fillLevelsList.size()) {
				slotFillLevels.clear();
				for (int i = 0; i < fillLevelsList.size(); i++) {
					slotFillLevels.add(i, fillLevelsList.getFloatOr(i, 0.0F));
				}
			} else {
				for (int i = 0; i < fillLevelsList.size(); i++) {
					slotFillLevels.set(i, fillLevelsList.getFloatOr(i, 0.0F));
				}
			}
		}
		showCounts = NBTHelper.getBoolean(tag, "showCounts").orElse(true);
		showFillLevels = NBTHelper.getBoolean(tag, "showFillLevels").orElse(false);
		slotColors = NBTHelper.getMap(tag, "slotColors", Integer::valueOf, (tagName, t) -> Optional.of(DyeColor.byId(((IntTag) t).intValue()))).orElseGet(HashMap::new);
	}

	@Override
	protected void saveSynchronizedData(CompoundTag tag) {
		super.saveSynchronizedData(tag);
		if (!showCounts) {
			tag.putBoolean("showCounts", showCounts);
		}
		if (showFillLevels) {
			tag.putBoolean("showFillLevels", showFillLevels);
		}
		if (!slotColors.isEmpty()) {
			NBTHelper.putMap(tag, "slotColors", slotColors, String::valueOf, color -> IntTag.valueOf(color.getId()));
		}
	}

	@Override
	protected String getStorageType() {
		return STORAGE_TYPE;
	}
}
