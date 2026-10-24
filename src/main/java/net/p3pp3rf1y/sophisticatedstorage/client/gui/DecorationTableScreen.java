package net.p3pp3rf1y.sophisticatedstorage.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ColorButton;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ColorPicker;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.CompositeWidgetBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ToggleButton;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TextureBlitData;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.DecorationTableBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.common.gui.DecorationTableMenu;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import net.p3pp3rf1y.sophisticatedstorage.util.DecorationHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 26.3 的 GUI 采用「提取渲染状态」模型：所有绘制方法都叫 extractXxx(GuiGraphicsExtractor, ...)，
 * 没有 ItemRenderer/bufferSource/PoseStack。
 * <p>
 * 本屏按原模组的**结构**（不是抄代码）重建了装饰台：左侧 4 行 trim 升级链 + 右侧 3 行 core 升级链
 * （与 {@code DecorationTableBlockEntity#getSlotInheritedFrom} 的继承关系一一对应），行间是 ↓ 继承开关，
 * 两组之间是横向继承开关与 + 连接符，底行是 存储 → (▶) → 结果，右侧是预览框。
 * 槽位数量与语义完全沿用菜单（7 材料 + 3 染料 + 1 存储 + 1 结果），配方与继承逻辑未改。
 * <p>
 * 已知未实现：**真正的 3D 方块模型预览**。26.3 的 {@link GuiGraphicsExtractor} 只提供物品 / 实体 / 书 /
 * 旗帜图案等固定种类的 RenderState 提交入口（pip 包下只有 GuiBannerResult、GuiBookModel、GuiEntity、
 * GuiProfilerChart、GuiSkin、OversizedItem、PictureInPicture），**没有通用方块模型入口**，所以预览框
 * 目前是「深色框 + 放大绘制的装饰结果物品」（见 {@link BlockPreview}），不是原模组的旋转方块模型。
 * 恢复路线：自建 {@code DecorationTablePreviewRenderState}（实现 {@code PictureInPictureRenderState}）
 * + 自己的 {@code PictureInPictureRenderer}（注册到 26.3 的 pip 渲染器表），在渲染器里提交方块模型，
 * 再把 {@link BlockPreview#renderDecoratedStack} 换成 pip 提交。
 */
public class DecorationTableScreen extends AbstractContainerScreen<DecorationTableMenu> {
	public static final Identifier GUI_BACKGROUND = SophisticatedStorage.getRL("textures/gui/decoration_table.png");
	public static final Identifier GUI_DECORATION_TABLE_ELEMENTS = SophisticatedStorage.getRL("textures/gui/decoration_table_elements.png");
	public static final Dimension SQUARE_64 = new Dimension(64, 64);
	public static final Dimension SQUARE_8 = new Dimension(8, 8);
	private static final Dimension BACKGROUND_TEXTURE_SIZE = new Dimension(256, 256);
	private static final int GUI_WIDTH = 250;
	private static final int GUI_HEIGHT = 226;

	/** 两列材料槽之间的连接符中心 x（相对 GUI 内容区），正好落在 trim 列右沿与 core 列部件图标之间。 */
	private static final int CONNECTOR_CENTER_X = DecorationTableMenu.CORE_COLUMN_X - 22;
	private static final int CONNECTOR_COLOR = 0xFF_6E6E6E;
	/** 预览框紧贴结果槽右侧，纵向覆盖整个材料区（对应原模组右上那块黑色大框）。 */
	private static final int PREVIEW_X = 178;
	private static final int PREVIEW_WIDTH = GUI_WIDTH - PREVIEW_X - 6;
	private static final int PREVIEW_ITEM_SCALE = 3;
	private static final int PART_HINT_OFFSET_X = -18;
	/** 空槽底纹：半透明内嵌阴影 + 1px 高光/暗边，替代原模组贴图里的槽位底。 */
	private static final int INLAY_FILL = 0x40_202020;
	private static final int INLAY_HIGHLIGHT = 0x40_FFFFFF;
	private static final int INLAY_SHADOW = 0x40_000000;
	private static final int DYE_MARKER_COLOR = 0x99;

	public static final TextureBlitData TOP_INNER_TRIM_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(0, 0), Dimension.SQUARE_16);
	public static final TextureBlitData TOP_TRIM_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(0, 16), Dimension.SQUARE_16);
	public static final TextureBlitData SIDE_TRIM_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(0, 32), Dimension.SQUARE_16);
	public static final TextureBlitData BOTTOM_TRIM_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(0, 48), Dimension.SQUARE_16);
	public static final TextureBlitData TOP_CORE_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(16, 16), Dimension.SQUARE_16);
	public static final TextureBlitData SIDE_CORE_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(16, 32), Dimension.SQUARE_16);
	public static final TextureBlitData BOTTOM_CORE_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(16, 48), Dimension.SQUARE_16);
	public static final TextureBlitData ACCENT_TINT_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(32, 48), Dimension.SQUARE_16);
	public static final TextureBlitData MAIN_TINT_HIGHLIGHT = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(48, 48), Dimension.SQUARE_16);
	public static final TextureBlitData STORAGE_INFO = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(32, 16), Dimension.SQUARE_16);

	private static final TextureBlitData GUI_BACKGROUND_BLIT = new TextureBlitData(GUI_BACKGROUND, BACKGROUND_TEXTURE_SIZE, new UV(0, 0), new Dimension(GUI_WIDTH, GUI_HEIGHT));
	private static final TextureBlitData INHERITED_MATERIAL_MARKER = new TextureBlitData(GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(77, 0), Dimension.SQUARE_16);

	private static final TextureBlitData VERTICAL_ARROW_BACKGROUND = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(56, 0), SQUARE_8);
	private static final TextureBlitData VERTICAL_ARROW_HOVERED_BACKGROUND = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(48, 0), SQUARE_8);
	private static final TextureBlitData HORIZONTAL_ARROW_BACKGROUND = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(56, 8), SQUARE_8);
	private static final TextureBlitData HORIZONTAL_ARROW_HOVERED_BACKGROUND = new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(48, 8), SQUARE_8);

	private static final ButtonDefinition.Toggle<Boolean> VERTICAL_INHERITANCE_ARROW = new ButtonDefinition.Toggle<>(SQUARE_8, VERTICAL_ARROW_BACKGROUND, Map.of(
			true, new ToggleButton.StateData(new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(32, 0), SQUARE_8),
					Component.translatable(StorageTranslationHelper.INSTANCE.translButton("decoration_inheritance_on"))),
			false, new ToggleButton.StateData(new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(40, 0), SQUARE_8),
					Component.translatable(StorageTranslationHelper.INSTANCE.translButton("decoration_inheritance_off")))
	), VERTICAL_ARROW_HOVERED_BACKGROUND);

	private static final ButtonDefinition.Toggle<Boolean> HORIZONTAL_INHERITANCE_ARROW = new ButtonDefinition.Toggle<>(SQUARE_8, HORIZONTAL_ARROW_BACKGROUND, Map.of(
			true, new ToggleButton.StateData(new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(32, 8), SQUARE_8),
					Component.translatable(StorageTranslationHelper.INSTANCE.translButton("decoration_inheritance_on"))),
			false, new ToggleButton.StateData(new TextureBlitData(GUI_DECORATION_TABLE_ELEMENTS, SQUARE_64, new UV(40, 8), SQUARE_8),
					Component.translatable(StorageTranslationHelper.INSTANCE.translButton("decoration_inheritance_off")))
	), HORIZONTAL_ARROW_HOVERED_BACKGROUND);

	private BlockPreview blockPreview;

	@Nullable
	private ColorPicker colorPicker;

	private final List<Component> resultPartsNeededTooltip = new ArrayList<>();
	/**
	 * 26.3 的 {@code ContainerEventHandler.mouseClicked} 只要命中子控件就返回 true（即使控件自己没处理）。
	 * 这里的 {@link BlockPreview}、部件提示图标都盖在结果槽/存储槽上方，于是结果永远拿不出来（“只能开 UI”）。
	 * 自己按最上层优先派发一次，交给父类走槽位逻辑时把 getChildAt 关掉。
	 */
	private boolean suppressChildHit = false;

	public DecorationTableScreen(DecorationTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
		inventoryLabelX = 45;
		getMenu().setSlotChangedListener(this::updatePreviewStacks);
	}

	private void updatePreviewStacks() {
		if (blockPreview != null) {
			blockPreview.setPreviewStacks(getMenu().getDecoratedPreviewStacks());
		}
	}

	@Override
	protected void init() {
		super.init();
		inventoryLabelY = getMenu().getSlot(DecorationTableBlockEntity.BOTTOM_TRIM_SLOT).y + 18 + 2;
		Slot resultSlot = menu.getResultSlot();
		Slot topSlot = getMenu().getSlot(DecorationTableBlockEntity.TOP_INNER_TRIM_SLOT);
		// 预览框摆在结果槽右侧、纵向覆盖整个材料区：之前从染料槽右侧起算的窄框既压住结果槽又看着像空占位
		blockPreview = new BlockPreview(new Position(leftPos + PREVIEW_X, topPos + topSlot.y),
				new Dimension(PREVIEW_WIDTH, resultSlot.y + 16 - topSlot.y), () -> menu.getResultSlot().getItem());
		updatePreviewStacks();

		addRenderableWidget(blockPreview);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.TOP_TRIM_SLOT);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.SIDE_TRIM_SLOT);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.BOTTOM_TRIM_SLOT);

		// top_trim → top_core 的横向继承开关，落在两列之间（与手绘的 + 连接符同一列）
		addInheritanceArrow(DecorationTableBlockEntity.TOP_CORE_SLOT, -26, 4, HORIZONTAL_INHERITANCE_ARROW);

		addVerticalInheritanceArrow(DecorationTableBlockEntity.SIDE_CORE_SLOT);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.BOTTOM_CORE_SLOT);

		addPartHint(DecorationTableBlockEntity.TOP_INNER_TRIM_SLOT, TOP_INNER_TRIM_HIGHLIGHT, "top_inner_trim");
		addPartHint(DecorationTableBlockEntity.TOP_TRIM_SLOT, TOP_TRIM_HIGHLIGHT, "top_trim");
		addPartHint(DecorationTableBlockEntity.SIDE_TRIM_SLOT, SIDE_TRIM_HIGHLIGHT, "side_trim");
		addPartHint(DecorationTableBlockEntity.BOTTOM_TRIM_SLOT, BOTTOM_TRIM_HIGHLIGHT, "bottom_trim");
		addPartHint(DecorationTableBlockEntity.TOP_CORE_SLOT, TOP_CORE_HIGHLIGHT, "top");
		addPartHint(DecorationTableBlockEntity.SIDE_CORE_SLOT, SIDE_CORE_HIGHLIGHT, "side");
		addPartHint(DecorationTableBlockEntity.BOTTOM_CORE_SLOT, BOTTOM_CORE_HIGHLIGHT, "bottom");

		// 已存材料提示图标：放在预览框左侧、与染料行同高
		addRenderableWidget(new PartStorageInfo(new Position(leftPos + PREVIEW_X - 18, topPos + topSlot.y), menu::getPartsStored));

		addDyeElements();

		if (colorPicker != null) {
			colorPicker.setPosition(new Position(leftPos + (imageWidth - ColorPicker.DIMENSIONS.width()) / 2, topPos + (imageHeight - ColorPicker.DIMENSIONS.height()) / 2));
		}
	}

	private void addDyeElements() {
		Slot greenDyeSlot = menu.getSlot(menu.getDyeSlotRange().firstSlot() + 1);
		Slot topTrimSlot = menu.getSlot(DecorationTableBlockEntity.TOP_TRIM_SLOT);
		Slot sideTrimSlot = menu.getSlot(DecorationTableBlockEntity.SIDE_TRIM_SLOT);

		ColorButton mainColorButton = new ColorButton(new Position(leftPos + greenDyeSlot.x - 1, topPos + topTrimSlot.y - 1), new Dimension(18, 18), menu::getMainColor,
				button -> openColorPicker(menu.getMainColor(), menu::setMainColor), Component.translatable(StorageTranslationHelper.INSTANCE.translButton("pick_color")));
		addRenderableWidget(mainColorButton);
		ColorButton accentColorButton = new ColorButton(new Position(leftPos + greenDyeSlot.x - 1, topPos + sideTrimSlot.y - 1), new Dimension(18, 18), menu::getAccentColor,
				button -> openColorPicker(menu.getAccentColor(), menu::setAccentColor), Component.translatable(StorageTranslationHelper.INSTANCE.translButton("pick_color")));
		addRenderableWidget(accentColorButton);

		addRenderableWidget(new PartIcon(new Position(mainColorButton.getX() + mainColorButton.getWidth() + 1, mainColorButton.getY() + 1), MAIN_TINT_HIGHLIGHT, Component.translatable(StorageTranslationHelper.INSTANCE.translGui("tint.main"))));
		addRenderableWidget(new PartIcon(new Position(accentColorButton.getX() + accentColorButton.getWidth() + 1, accentColorButton.getY() + 1), ACCENT_TINT_HIGHLIGHT, Component.translatable(StorageTranslationHelper.INSTANCE.translGui("tint.accent"))));
	}

	private void openColorPicker(int color, IntConsumer colorSetter) {
		colorPicker = new ColorPicker(this, new Position(leftPos + (imageWidth - ColorPicker.DIMENSIONS.width()) / 2, topPos + (imageHeight - ColorPicker.DIMENSIONS.height()) / 2),
				color, c -> {
			colorSetter.accept(c);
			colorPicker = null;
			blockPreview.setVisible(true);
			updatePreviewStacks();
		});
		blockPreview.setVisible(false);
	}

	private void addVerticalInheritanceArrow(int slotIndex) {
		addInheritanceArrow(slotIndex, 4, -11, VERTICAL_INHERITANCE_ARROW);
	}

	private void addInheritanceArrow(int slotIndex, int xOffset, int yOffset, ButtonDefinition.Toggle<Boolean> arrowDefinition) {
		Slot slot = menu.getSlot(slotIndex);
		addRenderableWidget(new ToggleButton<>(new Position(leftPos + slot.x + xOffset, topPos + slot.y + yOffset), arrowDefinition,
				button -> {
					resultPartsNeededTooltip.clear();
					getMenu().setSlotMaterialInheritance(slotIndex, !getMenu().isSlotMaterialInherited(slotIndex));
					updatePreviewStacks();
				}, () -> getMenu().isSlotMaterialInherited(slotIndex)) {
			@Override
			public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
				super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
				// 26.3 的 WidgetBase.extractRenderState 只画背景，前景状态图标需要显式补画
				extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTicks);
			}
		});
	}

	@Override
	public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		GuiHelper.blit(guiGraphics, leftPos, topPos, GUI_BACKGROUND_BLIT);
		renderEmptySlotInlays(guiGraphics);
		renderTreeConnectors(guiGraphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

		if (colorPicker != null) {
			// 颜色选择器盖在槽位之上，先压一层暗色背景保证可读性
			guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0_000000);
			renderColorPicker(guiGraphics, mouseX, mouseY, partialTick);
		}
	}

	private void renderColorPicker(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (colorPicker == null) {
			return;
		}
		colorPicker.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
		// 组合控件内部子控件需要逐个提取渲染状态（CompositeWidgetBase 的 children 渲染入口不是公开 API）
		for (GuiEventListener child : colorPicker.children()) {
			if (child instanceof Renderable renderable) {
				renderable.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
			}
		}
	}

	/**
	 * 空槽底纹：半透明内嵌阴影 + 1px 高光/暗边，替代原模组贴图里自带的槽位底。
	 * 只画给装饰台自己的 12 个槽（材料 / 染料 / 存储 / 结果），玩家背包槽位由背景贴图自带。同时给空染料槽右下角
	 * 画一个 5×5 的小色点，取代原来整格 16×16 的红/绿/蓝纯色块。
	 */
	private void renderEmptySlotInlays(GuiGraphicsExtractor guiGraphics) {
		int firstDye = getMenu().getDyeSlotRange().firstSlot();
		int lastContentSlot = Math.max(getMenu().getResultSlot().index, getMenu().getStorageSlot().index);
		for (int i = 0; i <= lastContentSlot; i++) {
			Slot slot = getMenu().getSlot(i);
			if (!slot.getItem().isEmpty()) {
				continue;
			}
			int x = leftPos + slot.x;
			int y = topPos + slot.y;
			guiGraphics.fill(x, y, x + 16, y + 16, INLAY_FILL);
			guiGraphics.fill(x, y, x + 16, y + 1, INLAY_HIGHLIGHT);
			guiGraphics.fill(x, y, x + 1, y + 16, INLAY_HIGHLIGHT);
			guiGraphics.fill(x, y + 15, x + 16, y + 16, INLAY_SHADOW);
			guiGraphics.fill(x + 15, y, x + 16, y + 16, INLAY_SHADOW);
		}
		renderDyeSlotMarkers(guiGraphics, firstDye);
	}

	private void renderDyeSlotMarkers(GuiGraphicsExtractor guiGraphics, int firstDyeSlot) {
		int[] colors = {0xFF_FF4040, 0xFF_40C040, 0xFF_4060FF};
		for (int i = 0; i < colors.length; i++) {
			Slot slot = getMenu().getSlot(firstDyeSlot + i);
			if (!slot.getItem().isEmpty()) {
				continue;
			}
			int x = leftPos + slot.x + 10;
			int y = topPos + slot.y + 10;
			guiGraphics.fill(x, y, x + 5, y + 5, (DYE_MARKER_COLOR << 24) | (colors[i] & 0x00FF_FFFF));
		}
	}

	/**
	 * 手绘的连接符：两列材料槽之间的 + 与存储→结果之间的 ▶。
	 * 行间 ↓ 与 top_core 的横向继承箭头是真实控件（{@link ToggleButton}），这里只补不承载交互的视觉符号。
	 */
	private void renderTreeConnectors(GuiGraphicsExtractor guiGraphics) {
		int plusCenterX = leftPos + CONNECTOR_CENTER_X;
		int plusCenterY = topPos + getMenu().getSlot(DecorationTableBlockEntity.SIDE_TRIM_SLOT).y + 8;
		guiGraphics.fill(plusCenterX - 4, plusCenterY - 1, plusCenterX + 5, plusCenterY + 1, CONNECTOR_COLOR);
		guiGraphics.fill(plusCenterX - 1, plusCenterY - 4, plusCenterX + 1, plusCenterY + 5, CONNECTOR_COLOR);

		Slot storageSlot = getMenu().getStorageSlot();
		int arrowX = leftPos + storageSlot.x + 18 + 2;
		int arrowCenterY = topPos + storageSlot.y + 8;
		for (int i = 0; i < 7; i++) {
			int halfHeight = 6 - i;
			guiGraphics.fill(arrowX + i, arrowCenterY - halfHeight, arrowX + i + 1, arrowCenterY + halfHeight + 1, CONNECTOR_COLOR);
		}
	}

	private void addPartHint(int slotIndex, TextureBlitData texture, String barrelPart) {
		Slot slot = menu.getSlot(slotIndex);
		addRenderableWidget(new PartIcon(new Position(leftPos + slot.x + PART_HINT_OFFSET_X, topPos + slot.y), texture, Component.translatable(StorageTranslationHelper.INSTANCE.translGui("barrel_part." + barrelPart))));
	}

	@Override
	public void extractSlot(GuiGraphicsExtractor guiGraphics, Slot slot, int mouseX, int mouseY) {
		if (slot.getItem().isEmpty() && getMenu().isSlotMaterialInherited(slot.index)) {
			ItemStack inheritedItem = getMenu().getInheritedItem(slot.index);
			if (!inheritedItem.isEmpty()) {
				Matrix3x2fStack pose = guiGraphics.pose();
				pose.pushMatrix();
				pose.translate(leftPos, topPos);
				GuiHelper.renderItemInGUI(guiGraphics, minecraft, inheritedItem, slot.x, slot.y);
				GuiHelper.blit(guiGraphics, slot.x, slot.y, INHERITED_MATERIAL_MARKER);
				pose.popMatrix();
			}
		}
		super.extractSlot(guiGraphics, slot, mouseX, mouseY);
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> tooltip = super.getTooltipFromContainerItem(stack);
		if (hoveredSlot == getMenu().getResultSlot() && !hoveredSlot.getItem().isEmpty() && hoveredSlot.getItem().getItem() != ModItems.PAINTBRUSH.get()) {
			tooltip.addAll(getResultPartsNeededTooltip());
		} else if (!resultPartsNeededTooltip.isEmpty()) {
			resultPartsNeededTooltip.clear();
		}
		return tooltip;
	}

	private List<Component> getResultPartsNeededTooltip() {
		if (!resultPartsNeededTooltip.isEmpty()) {
			return resultPartsNeededTooltip;
		}

		Map<Identifier, Integer> partsNeeded = getMenu().getPartsNeeded();
		addPartCountInfo(partsNeeded, resultPartsNeededTooltip, location -> getMenu().getMissingDyes().contains(location) ? ChatFormatting.RED : ChatFormatting.DARK_GRAY);
		return resultPartsNeededTooltip;
	}

	private record PartCount(Identifier part, int count) {
	}

	private static void addPartCountInfo(Map<Identifier, Integer> partCounts, List<Component> tooltip, Function<Identifier, ChatFormatting> getPartFormatting) {
		Map<ItemStack, PartCount> itemCounts = new LinkedHashMap<>();
		partCounts.forEach((part, count) -> {
			if (BuiltInRegistries.ITEM.containsKey(part)) {
				Item item = BuiltInRegistries.ITEM.getValue(part);
				itemCounts.put(new ItemStack(item), new PartCount(part, count));
			} else {
				// 26.3 移除了 Registry#getTag，只保留 holder 视图的 getTagOrEmpty
				for (Holder<Item> dye : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, part))) {
					itemCounts.put(new ItemStack(dye.value()), new PartCount(part, count));
					break;
				}
			}
		});

		itemCounts.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().getHoverName().getString())).forEach(entry -> {
			ItemStack itemStack = entry.getKey();
			Identifier location = entry.getValue().part();
			int count = entry.getValue().count();
			MutableComponent partCountText = Component.literal(count + "/" + DecorationHelper.BLOCK_TOTAL_PARTS + " (" + String.format("%.0f%%", (float) count / DecorationHelper.BLOCK_TOTAL_PARTS * 100) + ") of ");
			tooltip.add(partCountText.append(itemStack.getHoverName()).withStyle(getPartFormatting.apply(location)));
		});
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (colorPicker != null) {
			return colorPicker.mouseDragged(event, dragX, dragY);
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		super.extractTooltip(guiGraphics, mouseX, mouseY);

		for (GuiEventListener child : children()) {
			if (child instanceof WidgetBase widget) {
				widget.extractTooltip(this, guiGraphics, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (colorPicker != null) {
			return colorPicker.mouseClicked(event, false);
		}
		GuiEventListener focused = getFocused();
		if (focused != null && !focused.isMouseOver(mouseX, mouseY) && (focused instanceof WidgetBase widgetBase)) {
			widgetBase.setFocused(false);
		}

		// 最上层优先、只有真正处理了才算命中：预览框/部件图标盖在结果槽上时，不能把点击吞掉
		List<? extends GuiEventListener> children = children();
		for (int i = children.size() - 1; i >= 0; i--) {
			GuiEventListener child = children.get(i);
			if (!child.isMouseOver(mouseX, mouseY)) {
				continue;
			}
			if (child.mouseClicked(event, isDoubleClick)) {
				setFocused(child);
				if (event.button() == 0) {
					setDragging(true);
				}
				return true;
			}
		}

		suppressChildHit = true;
		try {
			return super.mouseClicked(event, isDoubleClick);
		} finally {
			suppressChildHit = false;
		}
	}

	@Override
	public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
		if (suppressChildHit) {
			return Optional.empty();
		}
		List<? extends GuiEventListener> children = children();
		for (int i = children.size() - 1; i >= 0; i--) {
			GuiEventListener child = children.get(i);
			if (child.isMouseOver(mouseX, mouseY)) {
				return Optional.of(child);
			}
		}
		return Optional.empty();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256 && colorPicker != null) {
			colorPicker = null;
			blockPreview.setVisible(true);
			updatePreviewStacks();
			return true;
		}

		return super.keyPressed(event);
	}

	private static class PartIcon extends WidgetBase {
		private final TextureBlitData texture;
		private final Component tooltip;

		protected PartIcon(Position position, TextureBlitData texture, Component tooltip) {
			super(position, new Dimension(texture.getWidth(), texture.getHeight()));
			this.texture = texture;
			this.tooltip = tooltip;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
			GuiHelper.blit(guiGraphics, x, y, texture);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			//noop
		}

		@Override
		public void extractTooltip(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
			if (isMouseOver(mouseX, mouseY)) {
				guiGraphics.setTooltipForNextFrame(minecraft.font, tooltip, mouseX, mouseY);
			}
		}
	}

	private static class PartStorageInfo extends WidgetBase {
		private final List<Component> partStorageTooltip = new ArrayList<>();
		private final Supplier<Map<Identifier, Integer>> getPartsStored;

		protected PartStorageInfo(Position position, Supplier<Map<Identifier, Integer>> getPartsStored) {
			super(position, Dimension.SQUARE_16);
			this.getPartsStored = getPartsStored;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
			if (hasNoPartsToShow()) {
				return;
			}

			GuiHelper.blit(guiGraphics, x, y, STORAGE_INFO);
		}

		private boolean hasNoPartsToShow() {
			return getPartsStored.get().isEmpty();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			//noop
		}

		@Override
		public void extractTooltip(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
			if (hasNoPartsToShow()) {
				return;
			}

			if (isMouseOver(mouseX, mouseY)) {
				guiGraphics.setTooltipForNextFrame(minecraft.font, getPartStorageTooltip(), Optional.empty(), mouseX, mouseY);
			} else if (!partStorageTooltip.isEmpty()) {
				partStorageTooltip.clear();
			}
		}

		private List<Component> getPartStorageTooltip() {
			if (!partStorageTooltip.isEmpty() || hasNoPartsToShow()) {
				return partStorageTooltip;
			}

			partStorageTooltip.add(Component.translatable(StorageTranslationHelper.INSTANCE.translGuiTooltip("parts_stored")));
			addPartCountInfo(getPartsStored.get(), partStorageTooltip, location -> ChatFormatting.GRAY);
			return partStorageTooltip;
		}
	}

	/**
	 * 预览框：深色底框 + 放大绘制的「装饰后结果」物品，空的时候给一行可翻译提示。
	 * <p>
	 * **真正的 3D 方块模型预览尚未实现**：26.3 的 GUI 只有提取渲染状态管线，{@link GuiGraphicsExtractor} 只提供
	 * 物品 / 实体 / 书 / 旗帜图案 / 玩家皮肤等固定种类的提交入口（pip 包下共 7 种 RenderState），**没有通用的
	 * 方块模型提交入口**，所以这里只能画物品图标（原模组是旋转的容器模型）。恢复路线：自建
	 * {@code DecorationTablePreviewRenderState implements PictureInPictureRenderState} + 自己的
	 * {@code PictureInPictureRenderer}（注册进 26.3 的 pip 渲染器表），在渲染器里用方块模型渲染目标物品，
	 * 再把 {@link #renderDecoratedStack} 改成提交该 RenderState。
	 */
	private static class BlockPreview extends CompositeWidgetBase<WidgetBase> {
		private static final int ITEM_SIZE = 16;
		private static final int TITLE_HEIGHT = 12;
		private static final Component TITLE = Component.translatable(StorageTranslationHelper.INSTANCE.translGui("decoration_table.preview_title"));
		private static final Component EMPTY_HINT = Component.translatable(StorageTranslationHelper.INSTANCE.translGui("decoration_table.preview_empty"));
		private static final Component CYCLE_HINT = Component.translatable(StorageTranslationHelper.INSTANCE.translGui("decoration_table.preview_cycle"));

		private final List<ItemStack> previewStacks = new ArrayList<>();
		private final Supplier<ItemStack> fallbackStack;
		private int selectedPreview = 0;

		protected BlockPreview(Position position, Dimension dimension, Supplier<ItemStack> fallbackStack) {
			super(position, dimension);
			this.fallbackStack = fallbackStack;
		}

		public void setPreviewStacks(List<ItemStack> previewStacks) {
			this.previewStacks.clear();
			this.previewStacks.addAll(previewStacks);
			this.selectedPreview = 0;
		}

		private ItemStack getPreviewStack() {
			if (previewStacks.isEmpty()) {
				return fallbackStack.get();
			}
			return previewStacks.get(Math.min(selectedPreview, previewStacks.size() - 1));
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			// 底框在 extractContents 里画，物品/文字/指示点在这里画（26.3 的「底」「面」两段式）
			super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
			guiGraphics.text(font, TITLE, x + 4, y + 4, 0xFF_9A9A9A, false);
			renderDecoratedStack(guiGraphics);
			renderSelectionDots(guiGraphics);
		}

		/**
		 * 把装饰结果物品放大 {@link DecorationTableScreen#PREVIEW_ITEM_SCALE} 倍画在框中央。
		 * 26.3 的 {@code GuiGraphicsExtractor#item} 会把当前 pose 矩阵复制进 GuiItemRenderState，
		 * 因此 pushMatrix + translate + scale 对物品同样生效。
		 */
		private void renderDecoratedStack(GuiGraphicsExtractor guiGraphics) {
			ItemStack previewStack = getPreviewStack();
			if (previewStack.isEmpty()) {
				guiGraphics.textWithWordWrap(font, EMPTY_HINT, x + 6, y + TITLE_HEIGHT + 8, getWidth() - 12, 0xFF_808080);
				return;
			}

			int itemSize = ITEM_SIZE * DecorationTableScreen.PREVIEW_ITEM_SCALE;
			int bottomReserve = previewStacks.size() > 1 ? 10 : 2;
			int itemX = x + (getWidth() - itemSize) / 2;
			int itemY = y + TITLE_HEIGHT + Math.max(0, (getHeight() - TITLE_HEIGHT - bottomReserve - itemSize) / 2);
			Matrix3x2fStack pose = guiGraphics.pose();
			pose.pushMatrix();
			pose.translate(itemX, itemY);
			pose.scale(DecorationTableScreen.PREVIEW_ITEM_SCALE, DecorationTableScreen.PREVIEW_ITEM_SCALE);
			guiGraphics.item(previewStack, 0, 0);
			pose.popMatrix();
		}

		private void renderSelectionDots(GuiGraphicsExtractor guiGraphics) {
			int count = previewStacks.size();
			if (count < 2 || count > 8) {
				return;
			}
			int dotY = y + getHeight() - 7;
			int totalWidth = count * 6 - 2;
			int startX = x + (getWidth() - totalWidth) / 2;
			for (int i = 0; i < count; i++) {
				int dotColor = i == selectedPreview ? 0xFF_E0E0E0 : 0xFF_5A5A5A;
				guiGraphics.fill(startX + i * 6, dotY, startX + i * 6 + 4, dotY + 4, dotColor);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
			if (!isMouseOver(event.x(), event.y()) || previewStacks.size() < 2) {
				return false;
			}
			// 26.3 的鼠标键位常量，禁止裸数字
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				selectedPreview = (selectedPreview + 1) % previewStacks.size();
				return true;
			} else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				selectedPreview = (selectedPreview - 1 + previewStacks.size()) % previewStacks.size();
				return true;
			}
			return false;
		}

		@Override
		public void extractTooltip(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
			if (!isMouseOver(mouseX, mouseY)) {
				return;
			}
			List<Component> tooltip = new ArrayList<>();
			ItemStack previewStack = getPreviewStack();
			if (previewStack.isEmpty()) {
				tooltip.add(EMPTY_HINT);
			} else {
				tooltip.add(previewStack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
				if (previewStacks.size() > 1) {
					tooltip.add(CYCLE_HINT.copy().withStyle(ChatFormatting.GRAY));
				}
			}
			guiGraphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
			int border = 0xFF_404040;
			guiGraphics.fill(x, y, x + getWidth(), y + getHeight(), 0xFF_101010);
			guiGraphics.fill(x, y, x + getWidth(), y + 1, border);
			guiGraphics.fill(x, y + getHeight() - 1, x + getWidth(), y + getHeight(), border);
			guiGraphics.fill(x, y, x + 1, y + getHeight(), border);
			guiGraphics.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), border);
			// 标题栏底 + 分隔线，让「预览」区看起来是一块独立面板
			guiGraphics.fill(x + 1, y + 1, x + getWidth() - 1, y + TITLE_HEIGHT, 0xFF_1C1C1C);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			//noop —— 子控件为空，物品/文字在 extractRenderState 里画
		}
	}
}
