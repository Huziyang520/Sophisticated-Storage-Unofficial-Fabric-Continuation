package net.p3pp3rf1y.sophisticatedstorage.common.gui;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.p3pp3rf1y.sophisticatedcore.platform.item.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.ISyncedContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotItemHandler;
import net.p3pp3rf1y.sophisticatedcore.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.network.SyncContainerClientDataPayload;
import net.p3pp3rf1y.sophisticatedcore.util.SlotRange;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.DecorationTableBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DecorationTableMenu extends AbstractContainerMenu implements ISyncedContainer {
	private static final String SET_INHERITANCE_ACTION = "setInheritance";
	/**
	 * 方块图集的 id，值和 26.3 的 {@code TextureAtlas.LOCATION_BLOCKS} 完全相同
	 * （{@code minecraft:textures/atlas/blocks.png}，见 {@code TextureAtlas} 的静态初始化）。
	 * <p>
	 * 这里不能用那个常量：本类是通用包里的菜单，专用服务端也会构造它，而
	 * {@code net.minecraft.client.renderer.texture.TextureAtlas} 只存在于客户端 jar，
	 * 静态引用会让服务端打开装饰台时 {@code NoClassDefFoundError}（审计「附加」项）。
	 * 槽背景的 atlas / sprite 只是一对 {@link Identifier}，本身与客户端无关，所以直接写字面值。
	 */
	private static final Identifier BLOCKS_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
	private static final Identifier EMPTY_RED_DYE_SLOT_BACKGROUND = SophisticatedStorage.getRL("item/empty_red_dye_slot");
	private static final Identifier EMPTY_GREEN_DYE_SLOT_BACKGROUND = SophisticatedStorage.getRL("item/empty_green_dye_slot");
	private static final Identifier EMPTY_BLUE_DYE_SLOT_BACKGROUND = SophisticatedStorage.getRL("item/empty_blue_dye_slot");
	private static final Identifier EMPTY_MATERIAL_SLOT_BACKGROUND = SophisticatedStorage.getRL("item/empty_material_slot");
	public static final int DECORATION_SLOT_PADDING = 12;
	/** 材料槽网格：左列 = trim 升级链（4 行），右列 = core 升级链（3 行，从 top_trim 旁分出）。 */
	public static final int TRIM_COLUMN_X = 26;
	public static final int CORE_COLUMN_X = 74;
	public static final int DECORATION_ROW_TOP_Y = 17;
	public static final int DECORATION_ROW_SPACING = 30;
	public static final int DYE_ROW_X = 104;
	public static final int DYE_ROW_Y = 17;
	private final DecorationTableBlockEntity blockEntity;

	private Slot resultSlot;

	private SlotRange decorationSlotRange;
	private SlotRange dyeSlotRange;
	private SlotRange storageSlotRange;
	private SlotRange playerSlotRange;
	@Nullable
	private Runnable slotChangedListener = null;
	private SlotItemHandler storageSlot;

	public DecorationTableMenu(int containerId, Player player, BlockPos pos) {
		super(ModBlocks.DECORATION_TABLE_CONTAINER_TYPE.get(), containerId);
		blockEntity = player.level().getBlockEntity(pos, ModBlocks.DECORATION_TABLE_BLOCK_ENTITY_TYPE.get()).orElse(null);
		if (blockEntity == null) {
			throw new IllegalStateException("No block entity found at position " + pos);
		}

		int y = addDecorationSlots();
		addStorageSlots();
		y += 14;
		addPlayerSlots(player.getInventory(), y);
	}

	public void setSlotChangedListener(@Nullable Runnable listener) {
		slotChangedListener = listener;
	}

	public Slot getStorageSlot() {
		return storageSlot;
	}

	public List<ItemStack> getDecoratedPreviewStacks() {
		return blockEntity.getDecoratedPreviewStacks();
	}

	private void addStorageSlots() {
		ItemStackHandler storageBlock = blockEntity.getStorageBlock();
		storageSlot = new SlotItemHandler(storageBlock, 0, getSlot(dyeSlotRange.firstSlot()).x, getSlot(DecorationTableBlockEntity.BOTTOM_TRIM_SLOT).y) {
			@Override
			public void setChanged() {
				super.setChanged();
				if (slotChangedListener != null) {
					slotChangedListener.run();
				}
			}
		};
		addSlot(storageSlot);

		storageSlotRange = new SlotRange(dyeSlotRange.firstSlot() + dyeSlotRange.numberOfSlots(), 1);

		resultSlot = new Slot(new SimpleContainer(1) {
			@Override
			public ItemStack getItem(int index) {
				return blockEntity.getResult();
			}

			@Override
			public ItemStack removeItem(int index, int count) {
				return index == 0 ? blockEntity.extractResult(count) : ItemStack.EMPTY;
			}
		}, 0, storageSlot.x + 18 + 18, storageSlot.y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean mayPickup(Player player) {
				return super.mayPickup(player) && getMissingDyes().isEmpty();
			}

			@Override
			public ItemStack remove(int amount) {
				return super.remove(amount);
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				super.onTake(player, stack);
				if (player.level().isClientSide()) {
					return;
				}
				blockEntity.consumeIngredientsOnCraft();
				try (Transaction ctx = Transaction.openOuter()) {
					blockEntity.getStorageBlock().extractSlot(0, blockEntity.getStorageBlock().getVariantInSlot(0), 1, ctx);
					ctx.commit();
				}
			}
		};
		addSlot(resultSlot);
	}

	/**
	 * 树状槽位布局（与方块实体的部件继承链同构，槽位数量与语义不变）：
	 * <pre>
	 *   行1  [trim 0]                              [染料][染料][染料]
	 *   行2  [trim 1]  ←横向继承→  [core 4]        [主色]
	 *   行3  [trim 2]  +           [core 5]        [强调色]
	 *   行4  [trim 3]              [core 6]   [存储]▶[结果]   [预览框]
	 * </pre>
	 * 左列 4 行 = 0→1→2→3 的 trim 升级链（行间 ↓），右列 3 行 = 4→5→6 的 core 升级链（行间 ↓），
	 * 行2 的横向位置留给 {@code TOP_CORE_SLOT ← TOP_TRIM_SLOT} 的继承开关（屏幕里画成横向连接符）。
	 */
	private int addDecorationSlots() {
		ItemStackHandler decorativeBlocks = blockEntity.getDecorativeBlocks();
		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.TOP_INNER_TRIM_SLOT, TRIM_COLUMN_X, rowY(0));
		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.TOP_TRIM_SLOT, TRIM_COLUMN_X, rowY(1));
		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.SIDE_TRIM_SLOT, TRIM_COLUMN_X, rowY(2));
		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.BOTTOM_TRIM_SLOT, TRIM_COLUMN_X, rowY(3));

		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.TOP_CORE_SLOT, CORE_COLUMN_X, rowY(1));
		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.SIDE_CORE_SLOT, CORE_COLUMN_X, rowY(2));
		addDecorationSlot(decorativeBlocks, DecorationTableBlockEntity.BOTTOM_CORE_SLOT, CORE_COLUMN_X, rowY(3));
		decorationSlotRange = new SlotRange(0, decorativeBlocks.getSlotCount());

		// 染料 3×1 网格
		ItemStackHandler dyes = blockEntity.getDyes();
		addSlot(new SlotItemHandler(dyes, 0, DYE_ROW_X, DYE_ROW_Y).sophisticatedCore_setBackground(BLOCKS_ATLAS, EMPTY_RED_DYE_SLOT_BACKGROUND));
		addSlot(new SlotItemHandler(dyes, 1, DYE_ROW_X + 18, DYE_ROW_Y).sophisticatedCore_setBackground(BLOCKS_ATLAS, EMPTY_GREEN_DYE_SLOT_BACKGROUND));
		addSlot(new SlotItemHandler(dyes, 2, DYE_ROW_X + 36, DYE_ROW_Y).sophisticatedCore_setBackground(BLOCKS_ATLAS, EMPTY_BLUE_DYE_SLOT_BACKGROUND));
		dyeSlotRange = new SlotRange(decorationSlotRange.firstSlot() + decorationSlotRange.numberOfSlots(), dyes.getSlotCount());

		// 存储槽与结果槽在底行：存储 → (▶) → 结果，玩家槽位起点因此保持在 151（与本轮之前一致）
		return rowY(3) + 18 + DECORATION_SLOT_PADDING;
	}

	public static int rowY(int row) {
		return DECORATION_ROW_TOP_Y + row * DECORATION_ROW_SPACING;
	}

	private void addDecorationSlot(ItemStackHandler itemHandler, int slotIndex, int x, int y) {
		addSlot(new SlotItemHandler(itemHandler, slotIndex, x, y) {
			@Override
			public void setChanged() {
				super.setChanged();
				if (slotChangedListener != null) {
					slotChangedListener.run();
				}
			}
		}.sophisticatedCore_setBackground(BLOCKS_ATLAS, EMPTY_MATERIAL_SLOT_BACKGROUND));
	}

	private void addPlayerSlots(Inventory playerInventory, int y) {
		int playerSlotXOffset = 45;
		int hotbarPadding = 4;

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, playerSlotXOffset + col * 18, y + row * 18));
			}
		}

		for (int col = 0; col < 9; ++col) {
			Slot slot = new Slot(playerInventory, col, playerSlotXOffset + col * 18, y + 3 * 18 + hotbarPadding);
			addSlot(slot);
		}

		playerSlotRange = new SlotRange(storageSlotRange.firstSlot() + storageSlotRange.numberOfSlots() + 1, 36);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = getSlot(slotIndex);
		ItemStack slotStack = slot.getItem();
		ItemStack slotStackCopy = slotStack.copy();
		if (isPlayerSlot(slotIndex)) {
			if (blockEntity.getDecorativeBlocks().isItemValid(decorationSlotRange.firstSlot(), ItemVariant.of(slotStack), slotStack.getCount())
					&& !moveItemStackTo(slotStack, decorationSlotRange, false)) {
				return ItemStack.EMPTY;
			} else if (isValidDye(slotStack)
					&& !moveItemStackTo(slotStack, dyeSlotRange, false)) {
				return ItemStack.EMPTY;
			} else if (blockEntity.getStorageBlock().isItemValid(0, ItemVariant.of(slotStack), slotStack.getCount())
					&& !moveItemStackTo(slotStack, storageSlotRange, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (!moveItemStackTo(slotStack, playerSlotRange, true)) {
				return ItemStack.EMPTY;
			}
		}

		if (slotStackCopy.getCount() == slotStack.getCount()) {
			return ItemStack.EMPTY;
		}

		blockEntity.updateResultAndSetChanged();
		slot.onTake(player, slotStackCopy);
		return slotStackCopy;
	}

	private boolean isValidDye(ItemStack stack) {
		ItemStackHandler dyes = blockEntity.getDyes();

		for (int slot = 0; slot < dyes.getSlotCount(); slot++) {
			if (dyes.isItemValid(slot, ItemVariant.of(stack), stack.getCount())) {
				return true;
			}
		}

		return false;
	}

	private boolean moveItemStackTo(ItemStack stack, SlotRange slotRange, boolean reverse) {
		return moveItemStackTo(stack, slotRange.firstSlot(), slotRange.firstSlot() + slotRange.numberOfSlots(), reverse);
	}

	private boolean isPlayerSlot(int slotIndex) {
		return playerSlotRange.isInRange(slotIndex);
	}

	public SlotRange getDyeSlotRange() {
		return dyeSlotRange;
	}

	@Override
	public boolean stillValid(Player player) {
		return !blockEntity.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(blockEntity.getBlockPos())) <= 16.0D;
	}

	public static DecorationTableMenu fromBuffer(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
		return new DecorationTableMenu(containerId, playerInventory.player, buffer.readBlockPos());
	}

	public void setSlotMaterialInheritance(int slot, boolean inheritance) {
		blockEntity.setSlotMaterialInheritance(slot, inheritance);
		sendToServer(tag -> {
			tag.putString("action", SET_INHERITANCE_ACTION);
			tag.putInt("slot", slot);
			tag.putBoolean("inheritance", inheritance);
		});
	}

	public void setMainColor(int color) {
		blockEntity.setMainColor(color);
		sendToServer(tag -> tag.putInt("mainColor", color));
	}

	public void setAccentColor(int color) {
		blockEntity.setAccentColor(color);
		sendToServer(tag -> tag.putInt("accentColor", color));
	}

	public boolean isSlotMaterialInherited(int slot) {
		return blockEntity.isSlotMaterialInherited(slot);
	}

	public ItemStack getInheritedItem(int childSlot) {
		return blockEntity.getInheritedItem(childSlot);
	}

	public int getMainColor() {
		return blockEntity.getMainColor();
	}

	public int getAccentColor() {
		return blockEntity.getAccentColor();
	}

	public Slot getResultSlot() {
		return resultSlot;
	}

	protected void sendToServer(Consumer<CompoundTag> addData) {
		if (blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide()) {
			return;
		}

		CompoundTag data = new CompoundTag();
		addData.accept(data);
		PacketDistributor.sendToServer(new SyncContainerClientDataPayload(data));
	}

	public Map<Identifier, Integer> getPartsNeeded() {
		return blockEntity.getPartsNeeded();
	}

	public Set<Identifier> getMissingDyes() {
		return blockEntity.getMissingDyes();
	}

	@Override
	public void handlePacket(CompoundTag data) {
		String action = data.getStringOr("action", "");
		if (action.equals(SET_INHERITANCE_ACTION)) {
			setSlotMaterialInheritance(data.getIntOr("slot", 0), data.getBooleanOr("inheritance", false));
		} else if (data.contains("mainColor")) {
			setMainColor(data.getIntOr("mainColor", 0));
		} else if (data.contains("accentColor")) {
			setAccentColor(data.getIntOr("accentColor", 0));
		}
	}

	public Map<Identifier, Integer> getPartsStored() {
		return blockEntity.getPartsStored();
	}

	public boolean hasMaterials() {
		return blockEntity.hasMaterials();
	}
}
