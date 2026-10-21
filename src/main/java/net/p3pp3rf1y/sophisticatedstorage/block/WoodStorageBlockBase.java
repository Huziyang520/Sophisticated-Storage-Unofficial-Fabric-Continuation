package net.p3pp3rf1y.sophisticatedstorage.block;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.Config;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageToolItem;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.util.StorageDataHelper;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class WoodStorageBlockBase extends StorageBlockBase implements IAdditionalDropDataBlock {
	public static final Map<WoodType, BlockFamily> CUSTOM_TEXTURE_WOOD_TYPES = ImmutableMap.<WoodType, BlockFamily>builder()
			.put(WoodType.ACACIA, BlockFamilies.ACACIA_PLANKS)
			.put(WoodType.BIRCH, BlockFamilies.BIRCH_PLANKS)
			.put(WoodType.CRIMSON, BlockFamilies.CRIMSON_PLANKS)
			.put(WoodType.DARK_OAK, BlockFamilies.DARK_OAK_PLANKS)
			.put(WoodType.JUNGLE, BlockFamilies.JUNGLE_PLANKS)
			.put(WoodType.OAK, BlockFamilies.OAK_PLANKS)
			.put(WoodType.SPRUCE, BlockFamilies.SPRUCE_PLANKS)
			.put(WoodType.WARPED, BlockFamilies.WARPED_PLANKS)
			.put(WoodType.MANGROVE, BlockFamilies.MANGROVE_PLANKS)
			.put(WoodType.CHERRY, BlockFamilies.CHERRY_PLANKS)
			.put(WoodType.BAMBOO, BlockFamilies.BAMBOO_PLANKS)
			.build();

	/**
	 * Every wood type a wooden storage block can be made of, in the (stable) order of {@link #CUSTOM_TEXTURE_WOOD_TYPES}.
	 * <p>
	 * The barrel model is shared by all wood types and gets its colour from a tint source, so the tint has to know the
	 * wood type. Reading it from the block entity gives a white barrel for the frames between the placement and the
	 * block entity data packet (the client has no wood type yet). That is why the index of the wood type in this list is
	 * mirrored into a block state property ({@code BarrelBlock#WOOD}): the state arrives together with the block itself,
	 * so the very first rendered frame is already tinted correctly.
	 */
	public static final List<WoodType> WOOD_TYPES = List.copyOf(CUSTOM_TEXTURE_WOOD_TYPES.keySet());

	/** index of the wood type in {@link #WOOD_TYPES}; unknown / absent wood types map to the first entry (acacia). */
	public static int woodTypeIndex(@Nullable WoodType woodType) {
		int index = woodType == null ? -1 : WOOD_TYPES.indexOf(woodType);
		return index < 0 ? 0 : index;
	}

	public static WoodType woodTypeAt(int index) {
		return WOOD_TYPES.get(Math.max(0, Math.min(index, WOOD_TYPES.size() - 1)));
	}

	protected WoodStorageBlockBase(Properties properties, Supplier<Integer> numberOfInventorySlotsSupplier, Supplier<Integer> numberOfUpgradeSlotsSupplier) {
		super(properties, numberOfInventorySlotsSupplier, numberOfUpgradeSlotsSupplier);
	}

	public void addNameWoodAndTintData(ItemStack stack, BlockGetter level, BlockPos pos) {
		WorldHelper.getBlockEntity(level, pos, WoodStorageBlockEntity.class).ifPresent(be -> addNameWoodAndTintData(stack, be));
	}

	/**
	 * 掉落物 / 创造取物统一走 {@link StorageDataHelper#writeStorageDataToStack}：颜色、木种、槽数、升级、
	 * 内容、打包状态一次写全（以前这里只写名字/木种/颜色，装好东西挖下来再放下就空）。
	 * <p>
	 * 内容是否要跟着物品走由「已打包」或 {@code dropPacked} 决定；没打包时内容会在方块被移除时撒出来
	 * （{@link StorageBlockEntity#preRemoveSideEffects}），不能同时写进物品，否则就是复制。
	 */
	@Override
	public void addDropData(ItemStack stack, StorageBlockEntity be) {
		if (!(be instanceof WoodStorageBlockEntity wbe)) {
			return;
		}
		boolean contentsGoIntoItem = wbe.isPacked() || shouldNonEmptyDropPacked(wbe);
		addNameWoodAndTintData(stack, wbe);
		if (contentsGoIntoItem) {
			StorageDataHelper.writeStorageDataToStack(stack, wbe);
			StorageBlockItem.setShowsTier(stack, be.shouldShowTier());
			StorageBlockItem.setNumberOfInventorySlots(stack, be.getStorageWrapper().getInventoryHandler().getSlotCount());
			StorageBlockItem.setNumberOfUpgradeSlots(stack, be.getStorageWrapper().getUpgradeHandler().getSlotCount());
			WoodStorageBlockItem.setPacked(stack, true);
		}
	}

	private static boolean shouldNonEmptyDropPacked(WoodStorageBlockEntity wbe) {
		if (Boolean.FALSE.equals(Config.COMMON.dropPacked.get())) {
			return false;
		}

		return !InventoryHelper.isEmpty(wbe.getStorageWrapper().getInventoryHandler()) || !InventoryHelper.isEmpty(wbe.getStorageWrapper().getUpgradeHandler());
	}

	private void addNameWoodAndTintData(ItemStack stack, WoodStorageBlockEntity wbe) {
		if (stack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
			int mainColor = wbe.getStorageWrapper().getMainColor();
			if (mainColor != -1) {
				tintableBlockItem.setMainColor(stack, mainColor);
			}
			int accentColor = wbe.getStorageWrapper().getAccentColor();
			if (accentColor != -1) {
				tintableBlockItem.setAccentColor(stack, accentColor);
			}
		}
		if (wbe.hasCustomName()) {
			stack.sophisticatedCore_set(DataComponents.CUSTOM_NAME, wbe.getCustomName());
		}
		// 方块实体里没有木种（旧存档 / 同步数据缺失）时，退回方块状态的 WOOD：掉落物不该因为这份副本缺失就变回默认木种。
		// 染色的桶不写木种（颜色比木种优先），和放置时 setRenderBlockRenderProperties 的判断保持一致。
		getWoodTypeOrState(wbe).ifPresent(n -> WoodStorageBlockItem.setWoodType(stack, n));
	}

	private static Optional<WoodType> getWoodTypeOrState(WoodStorageBlockEntity wbe) {
		Optional<WoodType> woodType = wbe.getWoodType();
		if (woodType.isPresent()) {
			return woodType;
		}
		if (wbe.getStorageWrapper().hasMainColor() && wbe.getStorageWrapper().hasAccentColor()) {
			return Optional.empty();
		}
		BlockState state = wbe.getBlockState();
		if (!state.hasProperty(BarrelBlock.WOOD)) {
			return Optional.empty();
		}
		return Optional.of(WoodStorageBlockBase.woodTypeAt(state.getValue(BarrelBlock.WOOD)));
	}

	@Override
	public void addCreativeTabItems(Consumer<ItemStack> itemConsumer) {
		// 配置在创造栏枚举之前可能还没 attach（isLoaded() = false）。此时必须按默认值 false 处理，
		// 否则只会加进一个金合欢木变体，玩家看到的就是“存储只有金合欢木”。
		if (Config.CLIENT_SPEC.isLoaded() && Boolean.TRUE.equals(Config.CLIENT.showSingleWoodVariantOnly.get())) {
			itemConsumer.accept(WoodStorageBlockItem.setWoodType(new ItemStack(this), WoodType.ACACIA));
		} else {
			CUSTOM_TEXTURE_WOOD_TYPES.keySet().forEach(woodType -> itemConsumer.accept(WoodStorageBlockItem.setWoodType(new ItemStack(this), woodType)));
		}

		if (isBasicTier() || Boolean.TRUE.equals(!Config.CLIENT_SPEC.isLoaded() || Config.CLIENT.showHigherTierTintedVariants.get())) {
			for (DyeColor color : DyeColor.values()) {
				ItemStack storageStack = new ItemStack(this);
				if (storageStack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
					tintableBlockItem.setMainColor(storageStack, color.getTextureDiffuseColor());
					tintableBlockItem.setAccentColor(storageStack, color.getTextureDiffuseColor());
				}
				itemConsumer.accept(storageStack);
			}
			ItemStack storageStack = new ItemStack(this);
			if (storageStack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
				tintableBlockItem.setMainColor(storageStack, DyeColor.YELLOW.getTextureDiffuseColor());
				tintableBlockItem.setAccentColor(storageStack, DyeColor.LIME.getTextureDiffuseColor());
			}
			itemConsumer.accept(storageStack);
		}
	}

	private boolean isBasicTier() {
		return this == ModBlocks.BARREL.get() || this == ModBlocks.CHEST.get()
				|| this == ModBlocks.LIMITED_BARREL_1.get() || this == ModBlocks.LIMITED_BARREL_2.get()
				|| this == ModBlocks.LIMITED_BARREL_3.get() || this == ModBlocks.LIMITED_BARREL_4.get();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack stack = new ItemStack(this);
		addNameWoodAndTintData(stack, level, pos);
		// 创造取物（中键）与掉落必须拿到同一份数据：内容、升级、打包状态一个不能少，
		// 否则「创造模式复制一个装满的桶，放下去是空的」（12 号通病）。
		WorldHelper.getBlockEntity(level, pos, WoodStorageBlockEntity.class)
				.ifPresent(be -> StorageDataHelper.writeStorageDataToStack(stack, be));
		return stack;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (level.isClientSide()) {
			// The client runs the placement prediction itself - BlockItem#place calls setPlacedBy right after it
			// creates the block and its block entity - so the client copy exists before any packet arrives. Copying
			// the wood type and the colours from the placed item here is what makes the very first rendered frame the
			// dyed barrel: the tint source otherwise only has the block state (a dyed barrel carries no wood type, so
			// it painted the default wood colour) until the block entity data packet shows up one or more ticks later,
			// which the player sees as "the barrel flashes the wood colour and then turns dyed".
			WorldHelper.getBlockEntity(level, pos, WoodStorageBlockEntity.class).ifPresent(be -> applyRenderPropertiesFromItem(stack, be));
			return;
		}

		WorldHelper.getBlockEntity(level, pos, WoodStorageBlockEntity.class).ifPresent(be -> {
			// 唯一的读入口：内容 + 颜色 + 木种 + 槽数 + 打包状态都从物品组件/BE 数据里读回来。
			be.setBeingUpgraded(true);
			StorageDataHelper.readStorageDataFromStack(stack, be);

			if (stack.has(DataComponents.CUSTOM_NAME)) {
				be.setCustomName(stack.getHoverName());
			}
			setRenderBlockRenderProperties(stack, be);

			be.getStorageWrapper().onInit();
			be.tryToAddToController();

			if (placer != null && placer.getOffhandItem().getItem() == ModItems.STORAGE_TOOL.get()) {
				StorageToolItem.useOffHandOnPlaced(placer.getOffhandItem(), be);
			}
			be.setBeingUpgraded(false);
		});
	}

	/**
	 * Copies the wood type and the colours of the placed item into the block entity. This is plain data, so it runs on
	 * both sides: the server writes the authoritative copy, the client writes it straight from the item it just placed
	 * (see {@link #setPlacedBy}) so that the first rendered frame is already the dyed barrel.
	 */
	protected void applyRenderPropertiesFromItem(ItemStack stack, WoodStorageBlockEntity be) {
		WoodStorageBlockItem.getWoodType(stack).ifPresent(be::setWoodType);
		be.getStorageWrapper().setColors(StorageBlockItem.getMainColorFromComponentHolder(stack).orElse(-1), StorageBlockItem.getAccentColorFromComponentHolder(stack).orElse(-1));
	}

	protected void setRenderBlockRenderProperties(ItemStack stack, WoodStorageBlockEntity be) {
		applyRenderPropertiesFromItem(stack, be);
		WoodStorageBlockItem.getWoodType(stack).ifPresent(woodType -> {
			// 方块状态里的 WOOD 是“第一帧”就要用的颜色来源（方块实体数据要等一个包才会到客户端）。
			// getStateForPlacement 已经写过一次，这里再兜一次底：如果物品的木种组件在某个端口上没被
			// 读到（或者别的代码路径改了状态），至少保证状态和方块实体不会各说各话。
			Level level = be.getLevel();
			BlockState state = be.getBlockState();
			if (level != null && !level.isClientSide() && state.hasProperty(BarrelBlock.WOOD)) {
				int index = WoodStorageBlockBase.woodTypeIndex(woodType);
				if (state.getValue(BarrelBlock.WOOD) != index) {
					level.setBlock(be.getBlockPos(), state.setValue(BarrelBlock.WOOD, index), Block.UPDATE_ALL);
				}
			}
		});

		// 26.3: the colour is read from the block entity while rendering, but the client only gets it with a block entity
		// update. Without these calls a freshly placed dyed barrel keeps painting the wood colour that the block state
		// carries (a dyed barrel has no wood type, so that is acacia) until some unrelated block update happens - which is
		// exactly "the item is dyed, the placed block is wood coloured".
		be.setUpdateBlockRender();
		WorldHelper.notifyBlockUpdate(be);
		// notifyBlockUpdate alone only marks the block for the next chunk broadcast; send the data packet explicitly so
		// the first open/menu (and the first rendered frame) already sees the colours, wood type and slot count.
		StorageSync.syncToClients(be);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockState ret = super.playerWillDestroy(level, pos, state, player);
		WorldHelper.getBlockEntity(level, pos, WoodStorageBlockEntity.class)
				.ifPresent(wbe -> {
					if (Boolean.TRUE.equals(Config.COMMON.dropPacked.get())) {
						wbe.setPacked(true);

						if (player.isCreative() && (
								!InventoryHelper.isEmpty(wbe.getStorageWrapper().getInventoryHandler()) || !InventoryHelper.isEmpty(wbe.getStorageWrapper().getUpgradeHandler())
						)) {
							ItemStack drop = new ItemStack(this);
							addDropData(drop, wbe);
							ItemEntity itementity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, drop);
							itementity.setDefaultPickUpDelay();
							level.addFreshEntity(itementity);
						}
					}
				});
		return ret;
	}

	@SuppressWarnings("java:S1172") //parameter is used in override
	protected boolean tryItemInteraction(Player player, InteractionHand hand, WoodStorageBlockEntity b, ItemStack stackInHand, Direction facing, BlockHitResult hitResult) {
		if (stackInHand.getItem() == ModItems.PACKING_TAPE.get()) {
			if (Boolean.TRUE.equals(Config.COMMON.dropPacked.get())) {
				player.sendOverlayMessage(Component.translatable("gui.sophisticatedstorage.status.packing_tape_disabled"));
			} else {
				packStorage(player, hand, b, stackInHand);
			}
			return true;
		}
		return tryAddUpgrade(player, hand, b, stackInHand, facing, hitResult);
	}

	protected void packStorage(Player player, InteractionHand hand, WoodStorageBlockEntity b, ItemStack stackInHand) {
		if (!player.isCreative()) {
			// 用 hurtAndBreak 而不是 setDamageValue + setItemInHand(EMPTY)：前者走原版耐久链路，
			// 用尽时由服务端播正确的物品损坏音效/挥臂反馈，后者会让手持物直接凭空消失（客户端表现为
			// 音效错乱 + 手持物品瞬间消失/指针残留）。
			stackInHand.hurtAndBreak(1, player, hand);
		}
		packStorageEntity(player, b);
	}

	/**
	 * 只做「把这个方块实体标记成已打包」这一件事：不改内容、不重建 handler、不碰 Size。
	 * 打包前后各取一次内容快照做断言，任何内容变化都会被记录为错误（打包绝不允许动容器内容）。
	 */
	protected void packStorageEntity(Player player, WoodStorageBlockEntity b) {
		CompoundTag contentsBeforePacking = StorageDataHelper.snapshotContents(b);

		BlockState blockState = b.getBlockState();
		if (blockState.getBlock() instanceof StorageBlockBase storageBlock && blockState.getValue(StorageBlockBase.TICKING)) {
			storageBlock.setTicking(player.level(), b.getBlockPos(), blockState, false);
		}

		b.setPacked(true);
		// setPacked 只改内存标记：没有 setChanged 这份打包状态可能连存档都到不了，
		// 没有 setUpdateBlockRender + 立即发包则客户端永远停在未打包模型（现象 A：没有 packed 纹理）。
		b.setChanged();
		b.setUpdateBlockRender();
		b.removeFromController();

		WorldHelper.notifyBlockUpdate(b);
		StorageSync.syncToClients(b);

		StorageDataHelper.assertContentsUnchanged("packing", contentsBeforePacking, b);
	}
}
