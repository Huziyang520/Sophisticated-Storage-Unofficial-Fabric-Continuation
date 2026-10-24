package net.p3pp3rf1y.sophisticatedstorage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TranslationHelper;
import net.p3pp3rf1y.sophisticatedcore.controller.ILinkable;
import net.p3pp3rf1y.sophisticatedcore.util.ItemBase;
import net.p3pp3rf1y.sophisticatedcore.util.RegistryHelper;
import net.p3pp3rf1y.sophisticatedcore.util.StreamCodecHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.block.*;
import net.p3pp3rf1y.sophisticatedstorage.client.gui.StorageTranslationHelper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.init.ModDataComponents;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class StorageToolItem extends ItemBase {
	/** 与语言文件里 {@code item.sophisticatedstorage.storage_tool.overlay.*} 的后缀一一对应 */
	/** 选中控制器：接下来点的容器都会被连到它上面 */
	private static final String LINKING_OVERLAY = "linking";
	private static final String LINKED_OVERLAY = "linked_to_controller";
	private static final String LINK_FAILED_OVERLAY = "link_failed";
	private static final String UNLINKING_OVERLAY = "unlinking";
	private static final String LOCKED_OVERLAY = "locked";
	private static final String UNLOCKED_OVERLAY = "unlocked";

	public StorageToolItem() {
		super(new Properties().stacksTo(1));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltipComponents, TooltipFlag flag) {
		StorageTranslationHelper.INSTANCE.getTranslatedLines(stack.getItem().getDescriptionId() + TranslationHelper.TOOLTIP_SUFFIX, null, ChatFormatting.DARK_GRAY).forEach(tooltipComponents);
		String itemName = RegistryHelper.getItemKey(stack.getItem()).getPath();
		tooltipComponents.accept(Component.translatable(StorageTranslationHelper.INSTANCE.translItemTooltip(itemName) + ".controls",
				Component.translatable(StorageTranslationHelper.INSTANCE.translItemTooltip(itemName) + ".controls.combination").withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
	}

	public static void useOffHandOnPlaced(ItemStack tool, StorageBlockEntity be) {
		if (be.getLevel().isClientSide()) {
			return;
		}
		Mode mode = getMode(tool);

		if (mode == Mode.LINK) {
			getControllerLink(tool).ifPresentOrElse(be::linkToController, be::unlinkFromController);
		} else if (mode == Mode.LOCK) {
			be.toggleLock();
		}
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack tool, UseOnContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		Block blockClicked = level.getBlockState(pos).getBlock();
		Player player = context.getPlayer();
		Mode mode = getMode(tool);
		switch (mode) {
			case LINK -> {
				if (tryLinking(pos, level, blockClicked, tool, player)) {
					return InteractionResult.SUCCESS;
				}
			}
			case LOCK -> {
				if (tryTogglingLock(pos, level, player)) {
					return InteractionResult.SUCCESS;
				}
			}
			case COUNT_DISPLAY -> {
				if (tryToggling(pos, level, ICountDisplay.class, ICountDisplay::toggleCountVisibility)) {
					return InteractionResult.SUCCESS;
				}
			}
			case LOCK_DISPLAY -> {
				if (tryToggling(pos, level, ILockable.class, ILockable::toggleLockVisibility)) {
					return InteractionResult.SUCCESS;
				}
			}
			case TIER_DISPLAY -> {
				if (tryToggling(pos, level, ITierDisplay.class, ITierDisplay::toggleTierVisiblity)) {
					return InteractionResult.SUCCESS;
				}
			}
			case UPGRADES_DISPLAY -> {
				if (tryToggling(pos, level, IUpgradeDisplay.class, IUpgradeDisplay::toggleUpgradesVisiblity)) {
					return InteractionResult.SUCCESS;
				}
			}
			case FILL_LEVEL_DISPLAY -> {
				if (tryToggling(pos, level, IFillLevelDisplay.class, IFillLevelDisplay::toggleFillLevelVisibility)) {
					return InteractionResult.SUCCESS;
				}
			}
		}
		return super.onItemUseFirst(tool, context);
	}

	private static <T> boolean tryToggling(BlockPos pos, Level level, Class<T> clazz, Consumer<T> toggle) {
		return WorldHelper.getLoadedBlockEntity(level, pos, clazz).map(be -> {
			if (level.isClientSide()) {
				return true;
			}

			toggle.accept(be);
			return true;
		}).orElse(false);
	}

	/**
	 * LOCK 模式：切换上锁状态，并在服务端把结果以可翻译组件提示给玩家。
	 * <p>
	 * 以前这里走通用的 {@link #tryToggling}，只有方块外观变化，玩家很容易判成「右键没反应」
	 * （第 5 轮第 15 条：存储工具不可用）。锁是「静默操作」类的功能，必须给一条明确的回执。
	 */
	private static boolean tryTogglingLock(BlockPos pos, Level level, @Nullable Player player) {
		return WorldHelper.getLoadedBlockEntity(level, pos, ILockable.class).map(lockable -> {
			if (level.isClientSide()) {
				return true;
			}
			lockable.toggleLock();
			showOverlayMessage(player, lockable.isLocked() ? LOCKED_OVERLAY : UNLOCKED_OVERLAY);
			return true;
		}).orElse(false);
	}

	private static void showOverlayMessage(@Nullable Player player, String overlayMessage, Object... params) {
		if (player == null) {
			return;
		}
		player.sendOverlayMessage(StorageTranslationHelper.INSTANCE.translItemOverlayMessage(ModItems.STORAGE_TOOL.get(), overlayMessage, params));
	}

	private boolean tryLinking(BlockPos pos, Level level, Block blockClicked, ItemStack tool, @Nullable Player player) {
		if (blockClicked == ModBlocks.CONTROLLER.get()) {
			setControllerLink(tool, pos);
			if (!level.isClientSide()) {
				// 选中控制器之后，玩家必须知道「现在再点一个容器就是把它连上来」，否则后面那次点击看起来毫无反应
				showOverlayMessage(player, LINKING_OVERLAY, pos.getX(), pos.getY(), pos.getZ());
			}
			return true;
		}
		return WorldHelper.getBlockEntity(level, pos, ILinkable.class).map(linkable -> {
			Optional<BlockPos> controllerPos = getControllerLink(tool);
			if (controllerPos.isEmpty()) {
				linkable.unlinkFromController();
				if (!level.isClientSide()) {
					showOverlayMessage(player, UNLINKING_OVERLAY);
				}
				return true;
			}
			linkable.linkToController(controllerPos.get());
			if (level.isClientSide()) {
				return true;
			}
			// 失败不再静默：要么这个方块本来就不接受链接（如已打包），要么它已经挂在别的控制器上
			if (linkable instanceof StorageBlockEntity storageBlockEntity && !storageBlockEntity.isLinked()) {
				showOverlayMessage(player, LINK_FAILED_OVERLAY);
			} else {
				BlockPos linkedController = controllerPos.get();
				showOverlayMessage(player, LINKED_OVERLAY, linkedController.getX(), linkedController.getY(), linkedController.getZ());
			}
			return true;
		}).orElseGet(() -> {
			if (!level.isClientSide()) {
				showOverlayMessage(player, LINK_FAILED_OVERLAY);
			}
			return false;
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
		if (player.isShiftKeyDown()) {
			ItemStack tool = player.getItemInHand(usedHand);
			if (getControllerLink(tool).isPresent()) {
				removeControllerLink(tool);
				return InteractionResult.SUCCESS;
			}
		}

		return super.use(level, player, usedHand);
	}

	private void setControllerLink(ItemStack tool, BlockPos pos) {
		tool.sophisticatedCore_set(ModDataComponents.CONTROLLER_POS, pos);
	}

	public static Optional<BlockPos> getControllerLink(ItemStack tool) {
		return Optional.ofNullable(tool.sophisticatedCore_get(ModDataComponents.CONTROLLER_POS));
	}

	private void removeControllerLink(ItemStack tool) {
		tool.sophisticatedCore_remove(ModDataComponents.CONTROLLER_POS);
	}

	public static Component getOverlayMessage(ItemStack tool) {
		Mode mode = getMode(tool);
		Item item = tool.getItem();
		return switch (mode) {
			case LINK ->
					getControllerLink(tool).map(controllerPos -> StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "linking", controllerPos.getX(), controllerPos.getY(), controllerPos.getZ()))
							.orElseGet(() -> StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "unlinking"));
			case LOCK -> StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "toggling_lock");
			case LOCK_DISPLAY ->
					StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "toggling_lock_display");
			case COUNT_DISPLAY ->
					StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "toggling_count_display");
			case TIER_DISPLAY ->
					StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "toggling_tier_display");
			case UPGRADES_DISPLAY ->
					StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "toggling_upgrades_display");
			case FILL_LEVEL_DISPLAY ->
					StorageTranslationHelper.INSTANCE.translItemOverlayMessage(item, "toggling_fill_level_display");
		};
	}

	public static Mode getMode(ItemStack tool) {
		return tool.sophisticatedCore_getOrDefault(ModDataComponents.TOOL_MODE, Mode.LINK);
	}

	public static void cycleMode(ItemStack tool, boolean next) {
		tool.sophisticatedCore_set(ModDataComponents.TOOL_MODE, next ? getMode(tool).next() : getMode(tool).previous());
	}

	/**
	 * 服务端回执（{@link net.p3pp3rf1y.sophisticatedstorage.network.ToolModePayload}）落到客户端手持物上。
	 * <p>
	 * 客户端这份 stack 是本地副本，写组件只影响本地的 HUD 读取（{@code ToolInfoOverlay}），不会回传服务端；
	 * 之后物品栏同步到达时两边数值一致。
	 */
	@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
	public static void setModeFromServer(ItemStack tool, int modeOrdinal) {
		Mode[] modes = Mode.values();
		if (tool.getItem() != ModItems.STORAGE_TOOL.get() || modeOrdinal < 0 || modeOrdinal >= modes.length) {
			return;
		}
		tool.sophisticatedCore_set(ModDataComponents.TOOL_MODE, modes[modeOrdinal]);
	}

	public enum Mode implements StringRepresentable {
		LINK,
		LOCK,
		COUNT_DISPLAY,
		LOCK_DISPLAY,
		TIER_DISPLAY,
		UPGRADES_DISPLAY,
		FILL_LEVEL_DISPLAY;

		public static final StringRepresentable.EnumCodec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
		public static final StreamCodec<FriendlyByteBuf, Mode> STREAM_CODEC = StreamCodecHelper.enumCodec(Mode.class);

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public Mode previous() {
			return values()[Math.floorMod(ordinal() - 1, values().length)];
		}

		@Override
		public String getSerializedName() {
			return name();
		}
	}
}
