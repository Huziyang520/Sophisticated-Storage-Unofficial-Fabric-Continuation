package net.p3pp3rf1y.sophisticatedstorage.block;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.item.BarrelBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

import org.jetbrains.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class BarrelBlock extends WoodStorageBlockBase {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty FLAT_TOP = BooleanProperty.create("flat_top");
	/**
	 * Index of the barrel's wood type in {@link WoodStorageBlockBase#WOOD_TYPES}.
	 * <p>
	 * The barrel model is shared by every wood type and is tinted from a {@code BlockTintSource}, which used to read the
	 * wood type from the block entity. Right after placement the client block entity has not received its data packet
	 * yet, so the tint fell back to "untinted" and the barrel rendered white (near white overlay texture) for the first
	 * frames. The index travels in the block state instead (set from the placed item), so the state - which arrives with
	 * the block itself - already carries the colour on the first frame. Dyes keep using the block entity because a
	 * colour is not representable in a block state.
	 */
	public static final IntegerProperty WOOD = IntegerProperty.create("wood", 0, WoodStorageBlockBase.WOOD_TYPES.size() - 1);
	private static final VoxelShape ITEM_ENTITY_COLLISION_SHAPE = box(0.05, 0.05, 0.05, 15.95, 15.95, 15.95);

	public BarrelBlock(Supplier<Integer> numberOfInventorySlotsSupplier, Supplier<Integer> numberOfUpgradeSlotsSupplier, Properties properties) {
		this(numberOfInventorySlotsSupplier, numberOfUpgradeSlotsSupplier, properties, stateDef -> stateDef.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(TICKING, false).setValue(FLAT_TOP, false));
	}

	public BarrelBlock(Supplier<Integer> numberOfInventorySlotsSupplier, Supplier<Integer> numberOfUpgradeSlotsSupplier, Properties properties, Function<StateDefinition<Block, BlockState>, BlockState> getDefaultState) {
		super(properties, numberOfInventorySlotsSupplier, numberOfUpgradeSlotsSupplier);
		registerDefaultState(getDefaultState.apply(stateDefinition));
	}

	@Override
	public void addCreativeTabItems(Consumer<ItemStack> itemConsumer) {
		super.addCreativeTabItems(itemConsumer);
		if (this != ModBlocks.BARREL.get()) {
			return;
		}

		ItemStack flatBarrel = WoodStorageBlockItem.setWoodType(new ItemStack(this), WoodType.ACACIA);
		BarrelBlockItem.toggleFlatTop(flatBarrel);
		itemConsumer.accept(flatBarrel);
	}

	@SuppressWarnings("deprecation")
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

	// 26.3：自定义木桶粒子（更精细的命中/破坏粒子）依赖的 TerrainParticle 扩展接口在当前核心版本里缺失，
	// BarrelBlockClientExtensions 已暂放；这里交回原版处理。
	@Environment(EnvType.CLIENT)
	@Override
	public boolean sophisticatedCore_addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
		return false;
	}

	@Environment(EnvType.CLIENT)
	@Override
	public boolean sophisticatedCore_addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
		return false;
	}

	@Override
	public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		return WorldHelper.getBlockEntity(level, pos, WoodStorageBlockEntity.class).map(b -> {
			if (b.isPacked()) {
				// 26.3：未处理必须返回 TRY_WITH_EMPTY_HAND，否则 ServerPlayerGameMode 不会继续调 useWithoutItem（开界面的入口）
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level.isClientSide() || hand == InteractionHand.OFF_HAND) {
				return InteractionResult.SUCCESS;
			}

			if (tryItemInteraction(player, hand, b, stack, getFacing(state), hitResult)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}).orElse(InteractionResult.TRY_WITH_EMPTY_HAND);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		player.awardStat(Stats.OPEN_BARREL);
		player.sophisticatedCore_openMenu(
				new SimpleMenuProvider(
						(w, p, pl) -> instantiateContainerMenu(w, pl, pos),
						WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).map(StorageBlockEntity::getDisplayName).orElse(Component.empty())
				), pos
		);
		if (level instanceof ServerLevel serverLevel) {
			PiglinAi.angerNearbyPiglins(serverLevel, player, true);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		WorldHelper.getBlockEntity(level, pos, BarrelBlockEntity.class).ifPresent(barrel -> {
			Map<BarrelMaterial, Identifier> materials = BarrelBlockItem.getMaterials(stack);
			if (!materials.isEmpty()) {
				barrel.setMaterials(new EnumMap<>(materials));
			}
		});
	}

	protected StorageContainerMenu instantiateContainerMenu(int w, Player pl, BlockPos pos) {
		return new StorageContainerMenu(w, pl, pos);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		WorldHelper.getBlockEntity(level, pos, StorageBlockEntity.class).ifPresent(StorageBlockEntity::recheckOpen);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation direction) {
		return state.setValue(FACING, direction.rotate(state.getValue(FACING)));
	}

	@SuppressWarnings("deprecation")
	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN, TICKING, FLAT_TOP, WOOD);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack cloneItemStack = super.getCloneItemStack(world, pos, state, includeData);
		BarrelBlockItem.setFlatTop(cloneItemStack, state.getValue(FLAT_TOP));
		WorldHelper.getBlockEntity(world, pos, BarrelBlockEntity.class).ifPresent(barrelBlockEntity -> {
			Map<BarrelMaterial, Identifier> materials = barrelBlockEntity.getMaterials();
			if (!materials.isEmpty()) {
				BarrelBlockItem.setMaterials(cloneItemStack, materials);
			}
		});
		return cloneItemStack;
	}

	@Override
	public void addDropData(ItemStack stack, StorageBlockEntity be) {
		super.addDropData(stack, be);
		BlockState state = be.getBlockState();
		BarrelBlockItem.setFlatTop(stack, state.getValue(FLAT_TOP));
		if (be instanceof BarrelBlockEntity barrelBlockEntity) {
			Map<BarrelMaterial, Identifier> materials = barrelBlockEntity.getMaterials();
			if (!materials.isEmpty()) {
				BarrelBlockItem.setMaterials(stack, materials);
			}
		}
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		return defaultBlockState().setValue(FACING, blockPlaceContext.getNearestLookingDirection().getOpposite())
				.setValue(FLAT_TOP, BarrelBlockItem.isFlatTop(blockPlaceContext.getItemInHand()))
				.setValue(WOOD, WoodStorageBlockBase.woodTypeIndex(WoodStorageBlockItem.getWoodType(blockPlaceContext.getItemInHand()).orElse(null)));
	}

	@SuppressWarnings("deprecation")
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return context instanceof EntityCollisionContext entityCollisionContext && entityCollisionContext.getEntity() instanceof ItemEntity || isCalledByCollisionCacheLogic(level, pos) ? ITEM_ENTITY_COLLISION_SHAPE : super.getCollisionShape(state, level, pos, context);
	}

	@SuppressWarnings("deprecation")
	@Override
	public VoxelShape getBlockSupportShape(BlockState state, BlockGetter reader, BlockPos pos) {
		return Shapes.block();
	}

	private boolean isCalledByCollisionCacheLogic(BlockGetter level, BlockPos pos) {
		return level instanceof EmptyBlockGetter && pos == BlockPos.ZERO;
	}

	@Nullable
	@Override
	public StorageBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BarrelBlockEntity(pos, state);
	}

	@Override
	protected BlockEntityType<? extends StorageBlockEntity> getBlockEntityType() {
		return ModBlocks.BARREL_BLOCK_ENTITY_TYPE.get();
	}

	@Override
	public Direction getFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.block();
	}

	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}
}
