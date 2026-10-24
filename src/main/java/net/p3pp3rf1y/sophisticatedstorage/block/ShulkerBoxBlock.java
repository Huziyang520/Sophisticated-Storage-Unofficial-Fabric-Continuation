package net.p3pp3rf1y.sophisticatedstorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.p3pp3rf1y.sophisticatedcore.controller.IControllerBoundable;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.Config;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.item.ShulkerBoxItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageToolItem;
import net.p3pp3rf1y.sophisticatedstorage.util.StorageDataHelper;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ShulkerBoxBlock extends StorageBlockBase implements IAdditionalDropDataBlock {
	public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
	private static final VoxelShape ITEM_ENTITY_COLLISION_SHAPE = box(0.05, 0.05, 0.05, 15.95, 15.95, 15.95);

	public ShulkerBoxBlock(Supplier<Integer> numberOfInventorySlotsSupplier, Supplier<Integer> numberOfUpgradeSlotsSupplier) {
		this(numberOfInventorySlotsSupplier, numberOfUpgradeSlotsSupplier, 2.0F);
	}

	public ShulkerBoxBlock(Supplier<Integer> numberOfInventorySlotsSupplier, Supplier<Integer> numberOfUpgradeSlotsSupplier, float explosionResistance) {
		super(getProperties(explosionResistance), numberOfInventorySlotsSupplier, numberOfUpgradeSlotsSupplier);
	}

	private static Properties getProperties(float explosionResistance) {
		StatePredicate statePredicate = (state, blockGetter, pos) -> {
			BlockEntity blockentity = blockGetter.getBlockEntity(pos);
			if (!(blockentity instanceof ShulkerBoxBlockEntity shulkerboxblockentity)) {
				return true;
			} else {
				return shulkerboxblockentity.isClosed();
			}
		};
		return Properties.of().strength(2.0F, explosionResistance).dynamicShape().noOcclusion().isSuffocating(statePredicate).isViewBlocking((state, level, pos, box) -> statePredicate.test(state, level, pos)).pushReaction(PushReaction.POPPED).mapColor(DyeColor.PURPLE);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, ModBlocks.SHULKER_BOX_BLOCK_ENTITY_TYPE.get(), (l, pos, s, blockEntity) -> {
			ShulkerBoxBlockEntity.tick(l, pos, s, blockEntity);
			if (!l.isClientSide()) {
				StorageBlockEntity.serverTick(l, pos, blockEntity);
			}
		});
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		} else if (player.isSpectator()) {
			return InteractionResult.CONSUME;
		} else if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity) || !canOpen(state, level, pos, shulkerBoxBlockEntity)) {
			return InteractionResult.FAIL;
		}

		return WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(b -> {
			if (tryItemInteraction(player, hand, b, player.getItemInHand(hand), getFacing(state), hitResult)) {
				return itemInteractionResult(player.getItemInHand(hand));
			}
			// 26.3：未处理必须返回 TRY_WITH_EMPTY_HAND，才会回落到 useWithoutItem 打开界面
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}).orElse(InteractionResult.TRY_WITH_EMPTY_HAND);
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		} else if (player.isSpectator()) {
			return InteractionResult.CONSUME;
		} else if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity) || !canOpen(state, level, pos, shulkerBoxBlockEntity)) {
			return InteractionResult.PASS;
		}

		player.awardStat(Stats.CUSTOM.get(Stats.OPEN_SHULKER_BOX));
		player.sophisticatedCore_openMenu(new SimpleMenuProvider((w, p, pl) -> new StorageContainerMenu(w, pl, pos),
				WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(StorageBlockEntity::getDisplayName).orElse(Component.empty())), pos);
		if (level instanceof ServerLevel serverLevel) {
			PiglinAi.angerNearbyPiglins(serverLevel, player, true);
		}
		return InteractionResult.CONSUME;
	}

	private boolean tryItemInteraction(Player player, InteractionHand hand, StorageBlockEntity b, ItemStack itemInHand, Direction facing, BlockHitResult hitResult) {
		return tryAddUpgrade(player, hand, b, itemInHand, facing, hitResult);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).ifPresent(be -> {
			// 唯一的读入口：内容、颜色、木种、槽数、升级、打包状态都从物品组件/BE 数据里读回来。
			StorageDataHelper.readStorageDataFromStack(stack, be);

			if (stack.has(DataComponents.CUSTOM_NAME)) {
				be.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
			}

			be.getStorageWrapper().onInit();
			be.tryToAddToController();

			if (placer != null && placer.getOffhandItem().getItem() == ModItems.STORAGE_TOOL.get()) {
				StorageToolItem.useOffHandOnPlaced(placer.getOffhandItem(), be);
			}

			be.setChanged();
			// the colours live in the block entity and the client only learns about them from a block entity update, without
			// which a placed dyed shulker box keeps its undyed colours until something else updates the block
			be.setUpdateBlockRender();
			WorldHelper.notifyBlockUpdate(be);
			// notifyBlockUpdate only marks the block for the next chunk broadcast - send the data packet right away so the
			// very first frame and the first opened menu already see the colours (same as WoodStorageBlockBase#setPlacedBy)
			StorageSync.syncToClients(be);
		});
	}

	@Override
	public void addCreativeTabItems(Consumer<ItemStack> itemConsumer) {
		itemConsumer.accept(new ItemStack(this));

		if (this == ModBlocks.SHULKER_BOX.get() || Boolean.TRUE.equals(!Config.CLIENT_SPEC.isLoaded() || Config.CLIENT.showHigherTierTintedVariants.get())) {
			for (DyeColor color : DyeColor.values()) {
				ItemStack storageStack = getTintedStack(color);
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

	public ItemStack getTintedStack(DyeColor color) {
		ItemStack storageStack = new ItemStack(this);
		if (storageStack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
			tintableBlockItem.setMainColor(storageStack, color.getTextureDiffuseColor());
			tintableBlockItem.setAccentColor(storageStack, color.getTextureDiffuseColor());
		}
		return storageStack;
	}

	/**
	 * Same check as vanilla 26.3 ({@code ShulkerBoxBlock#canOpen}) - in particular it has to be fed
	 * {@link Vec3#atBottomCenterOf} and not {@code atLowerCornerOf}.
	 * <p>
	 * {@code Shulker#getProgressDeltaAabb} builds a cube that is <b>centred</b> on the passed position in x/z
	 * (-0.5..0.5) and starts at its y. With the block corner as origin that cube is shifted half a block towards
	 * -x/-z, so it no longer covers the cell the shulker box opens into but straddles the four cells around the
	 * corner. Opening upwards survived that (the cells above a freshly placed box are usually all air), every other
	 * facing hit a neighbour that is not part of the swept volume at all - the box could be placed in any direction
	 * but only the upwards one could still be opened.
	 */
	private static boolean canOpen(BlockState state, Level level, BlockPos pos, ShulkerBoxBlockEntity blockEntity) {
		if (blockEntity.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
			return true;
		} else {
			AABB aabb = Shulker.getProgressDeltaAabb(1.0F, state.getValue(FACING), 0.0F, 0.5F, Vec3.atBottomCenterOf(pos)).deflate(1.0E-6D);
			return level.noCollision(aabb);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockEntity blockentity = level.getBlockEntity(pos);
		// 26.3 vanilla：只在实际会阻止掉落（创造/旁观等）且盒子非空时才额外丢一份带内容的盒子，
		// 否则打空盒子也会凭空复制一个出来
		if (blockentity instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity && !level.isClientSide()
				&& player.preventsBlockDrops() && hasContents(shulkerBoxBlockEntity)) {
			ItemStack shulkerBoxDrop = new ItemStack(this);
			addShulkerContentsToStack(shulkerBoxDrop, shulkerBoxBlockEntity);

			// 统一的手工投放：默认拾取延迟 + 不参与同 tick 合并，掉落物不会「还没落地就没了」
			spawnStorageDrop(level, pos, shulkerBoxDrop);
		}

		return super.playerWillDestroy(level, pos, state, player);
	}

	private static boolean hasContents(ShulkerBoxBlockEntity be) {
		var handler = be.getStorageWrapper().getInventoryHandler();
		for (int slot = 0; slot < handler.getSlotCount(); slot++) {
			if (!handler.getStackInSlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void addDropData(ItemStack stack, StorageBlockEntity be) {
		addShulkerContentsToStack(stack, be);
	}

	/**
	 * 潜影盒的内容永远跟着物品走（{@code shouldDropContents()} 为 false，不存在「撒出来」的路径），
	 * 所以这里无条件写全量数据：内容、升级、颜色、槽数、名字、层级显示。
	 */
	private void addShulkerContentsToStack(ItemStack stack, StorageBlockEntity be) {
		StorageDataHelper.writeStorageDataToStack(stack, be);
		StorageBlockItem.setShowsTier(stack, be.shouldShowTier());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// 不能拿 BE 的开盖包围盒当轮廓：Shulker.getProgressAabb 返回的是以方块中心为原点的盒子，
		// 直接 Shapes.create 会得到整体偏移半格的碰撞箱，与世界里的模型对不上。模型是整格立方体，轮廓就是整格。
		return Shapes.block();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack stack = super.getCloneItemStack(level, pos, state, false);
		// 创造取物必须拿到和掉落物一样的数据（含内容），否则「中键拿一个装满的盒子，放下去是空的」
		WorldHelper.getBlockEntity(level, pos, ShulkerBoxBlockEntity.class)
				.ifPresent(be -> StorageDataHelper.writeStorageDataToStack(stack, be));
		return stack;
	}

	@SuppressWarnings("deprecation")
	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@SuppressWarnings("deprecation")
	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void setTicking(Level level, BlockPos pos, BlockState currentState, boolean ticking) {
		//noop as shulker box is always ticking due to calculation of animation and related bounding box size on server
	}

	@SuppressWarnings("deprecation")
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public StorageBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ShulkerBoxBlockEntity(pos, state);
	}

	@Override
	protected BlockEntityType<? extends StorageBlockEntity> getBlockEntityType() {
		return ModBlocks.SHULKER_BOX_BLOCK_ENTITY_TYPE.get();
	}

	@Override
	public Direction getFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return context instanceof EntityCollisionContext entityCollisionContext && entityCollisionContext.getEntity() instanceof ItemEntity ? ITEM_ENTITY_COLLISION_SHAPE : super.getCollisionShape(state, level, pos, context);
	}

	@Override
	public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return false;
	}

	@Override
	public boolean sophisticatedCore_addLandingEffects(BlockState state1, ServerLevel level, BlockPos pos, BlockState state2, LivingEntity entity, int numberOfParticles) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state1), entity.getX(), entity.getY(), entity.getZ(), numberOfParticles, 0.0D, 0.0D, 0.0D, 0.15D);
		return true;
	}

	@Override
	public boolean sophisticatedCore_addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
		Vec3 vec3 = entity.getDeltaMovement();
		level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
				entity.getX() + (level.getRandom().nextDouble() - 0.5D) * entity.getBbWidth(), entity.getY() + 0.1D, entity.getZ() + (level.getRandom().nextDouble() - 0.5D) * entity.getBbWidth(),
				vec3.x * -4.0D, 1.5D, vec3.z * -4.0D);
		return true;
	}
}
