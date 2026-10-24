package net.p3pp3rf1y.sophisticatedstorage.block;

import com.mojang.math.Axis;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IUpgradeRenderer;
import net.p3pp3rf1y.sophisticatedcore.client.render.UpgradeRenderRegistry;
import net.p3pp3rf1y.sophisticatedcore.controller.IControllableStorage;
import net.p3pp3rf1y.sophisticatedcore.renderdata.IUpgradeRenderData;
import net.p3pp3rf1y.sophisticatedcore.renderdata.RenderInfo;
import net.p3pp3rf1y.sophisticatedcore.renderdata.UpgradeRenderDataType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.util.BlockBase;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.util.StorageDataHelper;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class StorageBlockBase extends BlockBase implements IStorageBlock, ISneakItemInteractionBlock, EntityBlock {
	public static final BooleanProperty TICKING = BooleanProperty.create("ticking");
	protected final Supplier<Integer> numberOfInventorySlotsSupplier;
	protected final Supplier<Integer> numberOfUpgradeSlotsSupplier;

	protected StorageBlockBase(Properties properties, Supplier<Integer> numberOfInventorySlotsSupplier, Supplier<Integer> numberOfUpgradeSlotsSupplier) {
		super(properties);
		this.numberOfInventorySlotsSupplier = numberOfInventorySlotsSupplier;
		this.numberOfUpgradeSlotsSupplier = numberOfUpgradeSlotsSupplier;
	}

	@Override
	public abstract StorageBlockEntity newBlockEntity(BlockPos pos, BlockState state);

	@Override
	public void addCreativeTabItems(Consumer<ItemStack> itemConsumer) {
		itemConsumer.accept(new ItemStack(this));
	}

	@Nullable
	protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> typePassedIn, BlockEntityType<E> typeExpected, BlockEntityTicker<? super E> blockEntityTicker) {
		//noinspection unchecked
		return typeExpected == typePassedIn ? (BlockEntityTicker<A>) blockEntityTicker : null;
	}

	protected void renderUpgrades(Level level, RandomSource rand, BlockPos pos, Direction facing, RenderInfo renderInfo, BlockState storageBlockState) {
		if (Minecraft.getInstance().isPaused()) {
			return;
		}
		renderInfo.getUpgradeRenderData().forEach((type, data) -> UpgradeRenderRegistry.getUpgradeRenderer(type).ifPresent(renderer -> {
			if (storageBlockState.getBlock() instanceof StorageBlockBase storageBlock) {
				storageBlock.renderUpgrade(renderer, level, rand, pos, facing, type, data, storageBlockState, storageBlock);
			}

		}));
	}

	protected Vector3f getMiddleFacePoint(BlockState state, BlockPos pos, Direction facing, Vector3f vector) {
		Vector3f point = new Vector3f(vector);
		point.add(0, 0, 0.6f);
		point.rotate(Axis.XP.rotationDegrees(-90.0F));
		point.rotate(facing.getRotation());
		point.add(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
		return point;
	}

	private <T extends IUpgradeRenderData> void renderUpgrade(IUpgradeRenderer<T> renderer, Level level, RandomSource rand, BlockPos pos, Direction facing, UpgradeRenderDataType<?> type, IUpgradeRenderData data, BlockState state, StorageBlockBase storageBlock) {
		//noinspection unchecked
		type.cast(data).ifPresent(renderData -> renderer.render(level, rand, vector -> storageBlock.getMiddleFacePoint(state, pos, facing, vector), (T) renderData));
	}

	@Override
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean insideEffect) {
		super.entityInside(state, level, pos, entity, effectApplier, insideEffect);
		if (!level.isClientSide() && entity instanceof ItemEntity itemEntity) {
			WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).ifPresent(be -> tryToPickup(level, itemEntity, be.getStorageWrapper()));
		}
	}

	protected void tryToPickup(Level level, ItemEntity itemEntity, IStorageWrapper w) {
		ItemStack remainingStack = itemEntity.getItem().copy();
		remainingStack = InventoryHelper.runPickupOnPickupResponseUpgrades(level, w.getUpgradeHandler(), remainingStack, false);
		if (remainingStack.getCount() < itemEntity.getItem().getCount()) {
			itemEntity.setItem(remainingStack);
		}
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return !level.isClientSide() && Boolean.TRUE.equals(state.getValue(StorageBlockBase.TICKING)) ? StorageBlockBase.createTickerHelper(blockEntityType, getBlockEntityType(), (l, blockPos, blockState, storageBlockEntity) -> StorageBlockEntity.serverTick(l, blockPos, storageBlockEntity)) : null;
	}

	protected abstract BlockEntityType<? extends StorageBlockEntity> getBlockEntityType();

	@Override
	public int getNumberOfInventorySlots() {
		return numberOfInventorySlotsSupplier.get();
	}

	@Override
	public int getNumberOfUpgradeSlots() {
		return numberOfUpgradeSlotsSupplier.get();
	}

	@Override
	public void setTicking(Level level, BlockPos pos, BlockState currentState, boolean ticking) {
		level.setBlockAndUpdate(pos, currentState.setValue(StorageBlockBase.TICKING, ticking));
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos, Direction direction) {
		return WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(be -> InventoryHelper.getAnalogOutputSignal(be.getStorageWrapper().getInventoryForInputOutput())).orElse(0);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean isMoving) {
		// 注意：这个钩子在 26.3 里是「方块实体已经从区块摘掉之后」才调用的，
		// 所以这里不能再通过 level.getBlockEntity(pos) 去拿实体做投放（拿不到）。
		// 内容物投放/解除控制器绑定已移到 StorageBlockEntity#preRemoveSideEffects。
		super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		super.playerWillDestroy(level, pos, state, player);
		WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).ifPresent(IControllableStorage::removeFromController);
		return state;
	}

	/**
	 * 掉落物的「容器数据必须随物品走」保证。
	 * <p>
	 * 生存模式挖方块的掉落完全由战利品表产生（{@code Block.playerDestroy → Level.destroyBlock →
	 * dropResources}），条目里的 {@code sophisticatedstorage:copy_storage_data} 再回调
	 * {@link IAdditionalDropDataBlock#addDropData}。这条链上任何一环没跑到（战利品表没加载、函数顺序变化、
	 * 新的掉落来源），玩家看到的就是「掉落物没有内容 / 什么都没掉」。这里是那一层的兜底：
	 * 只要方块实体本来就要把内容交给物品（打包态、潜影盒），掉落物就必须带
	 * {@link net.minecraft.core.component.DataComponents#BLOCK_ENTITY_DATA}，缺失时用
	 * {@link StorageDataHelper#writeStorageDataToStack} 这个唯一的写入口补上，并留下一条 error 日志，
	 * 让「数据丢失」变成可诊断的事件而不是静默的空盒子。
	 */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof StorageBlockEntity storageBlockEntity)) {
			return drops;
		}
		if (drops.isEmpty()) {
			// 战利品表没跑：多数情况是数据包被判不兼容（pack.mcmeta 的 data 版本号），留痕便于定位
			SophisticatedStorage.LOGGER.warn("[storage-data] no drops generated for {} at {}", state.getBlock(), storageBlockEntity.getBlockPos());
			return drops;
		}
		if (this instanceof IAdditionalDropDataBlock additionalDropDataBlock) {
			ensureStorageDataInDrops(additionalDropDataBlock, drops, storageBlockEntity);
		}
		return drops;
	}

	private static void ensureStorageDataInDrops(IAdditionalDropDataBlock dropDataSource, List<ItemStack> drops, StorageBlockEntity blockEntity) {
		boolean contentsHaveToGoIntoTheItem = !blockEntity.shouldDropContents();
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			// 正常路径（战利品表的 copy_storage_data）已经写过数据，这里只在缺失时补，
			// 避免同一条路径被跑两遍（第二次对双箱、打包状态都不是幂等的）
			boolean hasStorageData = StorageDataHelper.getEntityDataTag(drop, blockEntity.getType()).isPresent();
			if (!hasStorageData) {
				dropDataSource.addDropData(drop, blockEntity);
				hasStorageData = StorageDataHelper.getEntityDataTag(drop, blockEntity.getType()).isPresent();
			}
			if (!contentsHaveToGoIntoTheItem || hasStorageData) {
				continue;
			}
			SophisticatedStorage.LOGGER.error("[storage-data] drop of {} at {} has no container data, writing it explicitly",
					drop.getItem(), blockEntity.getBlockPos());
			StorageDataHelper.writeStorageDataToStack(drop, blockEntity);
		}
	}

	/**
	 * 在方块位置生成一个可立即拾取的掉落物。
	 * <p>
	 * 手工投放（创造模式打包掉落、潜影盒）不能用 {@code Block.popResource}：那条路每次都会重新算一次
	 * 「同 tick 合并」，两个内容不同的容器掉在同一个方块里时会被合并成一份，被合并掉的那份
	 * {@code BLOCK_ENTITY_DATA} 就没了 —— 玩家看到的就是「掉落物不见了」。这里统一走
	 * {@link ItemEntity#setDefaultPickUpDelay()}（10 tick，与原版一致），并且只投这一份。
	 */
	protected static void spawnStorageDrop(Level level, BlockPos pos, ItemStack stack) {
		if (level.isClientSide() || stack.isEmpty()) {
			return;
		}
		ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
		itemEntity.setDefaultPickUpDelay();
		level.addFreshEntity(itemEntity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
		WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).ifPresent(be -> {
			RenderInfo renderInfo = be.getStorageWrapper().getRenderInfo();
			renderUpgrades(level, rand, pos, getFacing(state), renderInfo, state);
		});

	}

	@SuppressWarnings("java:S1172") // Used in overrides
	public VerticalFacing getVerticalFacing(BlockState state) {
		Direction facing = getFacing(state);
		if (facing == Direction.UP) {
			return VerticalFacing.UP;
		} else if (facing == Direction.DOWN) {
			return VerticalFacing.DOWN;
		}

		return VerticalFacing.NO;
	}

	public Direction getHorizontalDirection(BlockState state) {
		Direction facing = getFacing(state);

		if (facing == Direction.UP) {
			return Direction.NORTH;
		} else if (facing == Direction.DOWN) {
			return Direction.SOUTH;
		}

		return facing;
	}

	public abstract Direction getFacing(BlockState state);

	public int getDisplayItemsCount(List<RenderInfo.DisplayItem> displayItems) {
		return displayItems.size();
	}

	public boolean hasFixedIndexDisplayItems() {
		return false;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
		WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).ifPresent(be -> be.onNeighborChange(pos));
	}

	protected boolean tryAddUpgrade(Player player, InteractionHand hand, StorageBlockEntity b, ItemStack itemInHand, Direction facing, BlockHitResult hitResult) {
		if (player.level().isClientSide()) {
			return true;
		}

		if (hitResult.getDirection() != facing) {
			return false;
		}

		return tryAddSingleUpgrade(player, hand, b, itemInHand);
	}

	public boolean tryAddSingleUpgrade(Player player, InteractionHand hand, StorageBlockEntity b, ItemStack itemInHand) {
		return tryAddSingleUpgrade(player, hand, itemInHand, b.getStorageWrapper());
	}

	public static boolean tryAddSingleUpgrade(Player player, InteractionHand hand, ItemStack itemInHand, IStorageWrapper storageWrapper) {
		if (itemInHand.getItem() instanceof UpgradeItemBase<?> upgradeItem && itemInHand.is(ModItems.STORAGE_UPGRADE_TAG)) {
			UpgradeHandler upgradeHandler = storageWrapper.getUpgradeHandler();
			if (upgradeItem.canAddUpgradeTo(storageWrapper, itemInHand, true, player.level().isClientSide()).successful()
					&& InventoryHelper.insertIntoInventory(itemInHand, upgradeHandler, true).getCount() != itemInHand.getCount()) {
				InventoryHelper.insertIntoInventory(itemInHand.copyWithCount(1), upgradeHandler, false);
				itemInHand.shrink(1);
				if (itemInHand.isEmpty()) {
					player.setItemInHand(hand, ItemStack.EMPTY);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean trySneakItemInteraction(Player player, InteractionHand hand, BlockState state, Level level, BlockPos pos, BlockHitResult hitVec, ItemStack itemInHand) {
		if (level.isClientSide() || hitVec.getDirection() != getFacing(state)) {
			return false;
		}

		return tryFillUpgrades(player, hand, level, pos, itemInHand);
	}

	public boolean tryFillUpgrades(Player player, InteractionHand hand, Level level, BlockPos pos, ItemStack itemInHand) {
		return WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(b -> {
			boolean result = false;
			while (!itemInHand.isEmpty()) {
				if (tryAddSingleUpgrade(player, hand, b, itemInHand)) {
					result = true;
				} else {
					break;
				}
			}
			return result;
		}).orElse(false);
	}

	@SuppressWarnings("java:S1172") // Parameter used in overrides
	public List<BlockPos> getNeighborPos(BlockState state, BlockPos origin, Direction facing) {
		return List.of(origin.relative(facing));
	}

	/**
	 * 交互成功时该返回什么：打包带要用 {@code CONSUME}（已处理 + 挥臂），其它物品仍是 {@code SUCCESS}。
	 * 以前统一返回 SUCCESS（不挥臂），加上客户端先短路，玩家看到的就是「点了没反应 / 手持物异常」。
	 */
	protected static net.minecraft.world.InteractionResult itemInteractionResult(ItemStack stackInHand) {
		return stackInHand.getItem() == net.p3pp3rf1y.sophisticatedstorage.init.ModItems.PACKING_TAPE.get()
				? net.minecraft.world.InteractionResult.CONSUME
				: net.minecraft.world.InteractionResult.SUCCESS;
	}

	/**
	 * 手持打包带且配置允许打包时为 true。客户端也要用它做同一个判定，否则客户端会先短路成 SUCCESS，
	 * 服务端却按胶带处理（改耐久、打包方块），两边结果不一致。
	 */
	protected static boolean isPackingTape(ItemStack stackInHand) {
		return stackInHand.getItem() == net.p3pp3rf1y.sophisticatedstorage.init.ModItems.PACKING_TAPE.get()
				&& !Boolean.TRUE.equals(net.p3pp3rf1y.sophisticatedstorage.Config.COMMON.dropPacked.get());
	}
}
