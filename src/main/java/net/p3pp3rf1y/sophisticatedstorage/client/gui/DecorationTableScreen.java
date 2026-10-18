package net.p3pp3rf1y.sophisticatedstorage.client.gui;

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
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonBase;
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
 * 没有 ItemRenderer/bufferSource/PoseStack，因此原来的 3D 旋转预览与自定义物品渲染器预览无法保留，
 * 这里退化为平面物品预览（见 {@link BlockPreview}）。容器槽位、染料槽覆盖色、继承箭头、颜色选择器、
 * 部件提示与结果槽 tooltip 全部保留。
 */
public class DecorationTableScreen extends AbstractContainerScreen<DecorationTableMenu> {
	public static final Identifier GUI_BACKGROUND = SophisticatedStorage.getRL("textures/gui/decoration_table.png");
	public static final Identifier GUI_DECORATION_TABLE_ELEMENTS = SophisticatedStorage.getRL("textures/gui/decoration_table_elements.png");
	public static final Dimension SQUARE_64 = new Dimension(64, 64);
	public static final Dimension SQUARE_8 = new Dimension(8, 8);
	private static final Dimension BACKGROUND_TEXTURE_SIZE = new Dimension(256, 256);
	private static final int GUI_WIDTH = 250;
	private static final int GUI_HEIGHT = 226;

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
		int lastDyeSlotIndex = getMenu().getDyeSlotRange().firstSlot() + getMenu().getDyeSlotRange().numberOfSlots() - 1;
		Slot lastDyeSlot = getMenu().getSlot(lastDyeSlotIndex);
		Slot resultSlot = menu.getResultSlot();
		blockPreview = new BlockPreview(new Position(leftPos + lastDyeSlot.x + 16 + 1 + 8 + 1, topPos + lastDyeSlot.y), new Dimension(80, resultSlot.y - lastDyeSlot.y + 16 + 4));
		updatePreviewStacks();

		addRenderableWidget(blockPreview);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.TOP_TRIM_SLOT);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.SIDE_TRIM_SLOT);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.BOTTOM_TRIM_SLOT);

		addInheritanceArrow(DecorationTableBlockEntity.TOP_CORE_SLOT, -11, 4, HORIZONTAL_INHERITANCE_ARROW);

		addVerticalInheritanceArrow(DecorationTableBlockEntity.SIDE_CORE_SLOT);
		addVerticalInheritanceArrow(DecorationTableBlockEntity.BOTTOM_CORE_SLOT);

		addPartHint(DecorationTableBlockEntity.TOP_INNER_TRIM_SLOT, TOP_INNER_TRIM_HIGHLIGHT, "top_inner_trim");
		addPartHint(DecorationTableBlockEntity.TOP_TRIM_SLOT, TOP_TRIM_HIGHLIGHT, "top_trim");
		addPartHint(DecorationTableBlockEntity.SIDE_TRIM_SLOT, SIDE_TRIM_HIGHLIGHT, "side_trim");
		addPartHint(DecorationTableBlockEntity.BOTTOM_TRIM_SLOT, BOTTOM_TRIM_HIGHLIGHT, "bottom_trim");
		addPartHint(DecorationTableBlockEntity.TOP_CORE_SLOT, TOP_CORE_HIGHLIGHT, "top");
		addPartHint(DecorationTableBlockEntity.SIDE_CORE_SLOT, SIDE_CORE_HIGHLIGHT, "side");
		addPartHint(DecorationTableBlockEntity.BOTTOM_CORE_SLOT, BOTTOM_CORE_HIGHLIGHT, "bottom");

		Slot slot = menu.getSlot(DecorationTableBlockEntity.TOP_INNER_TRIM_SLOT);
		addRenderableWidget(new PartStorageInfo(new Position(leftPos + slot.x + 57, topPos + slot.y), menu::getPartsStored));

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
		renderDyeSlotsOverlays(guiGraphics);
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

	private void renderDyeSlotsOverlays(GuiGraphicsExtractor guiGraphics) {
		Matrix3x2fStack pose = guiGraphics.pose();
		pose.pushMatrix();
		pose.translate(leftPos, topPos);
		Slot redSlot = getMenu().getSlot(getMenu().getDyeSlotRange().firstSlot());
		renderSlotOverlay(guiGraphics, redSlot, 0x33_FF0000);
		Slot greenSlot = getMenu().getSlot(getMenu().getDyeSlotRange().firstSlot() + 1);
		renderSlotOverlay(guiGraphics, greenSlot, 0x33_00FF00);
		Slot blueSlot = getMenu().getSlot(getMenu().getDyeSlotRange().firstSlot() + 2);
		renderSlotOverlay(guiGraphics, blueSlot, 0x33_0000FF);
		pose.popMatrix();
	}

	private void renderSlotOverlay(GuiGraphicsExtractor guiGraphics, Slot slot, int slotColor) {
		guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, slotColor);
	}

	private void addPartHint(int slotIndex, TextureBlitData texture, String barrelPart) {
		Slot slot = menu.getSlot(slotIndex);
		addRenderableWidget(new PartIcon(new Position(leftPos + slot.x + 18, topPos + slot.y), texture, Component.translatable(StorageTranslationHelper.INSTANCE.translGui("barrel_part." + barrelPart))));
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

		return super.mouseClicked(event, isDoubleClick);
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

	private static class StackButton extends ButtonBase {
		private static final TextureBlitData BUTTON_HOVER = new TextureBlitData(GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(63, 42), Dimension.SQUARE_18);
		private final Supplier<ItemStack> stackSupplier;

		protected StackButton(Position position, IntConsumer onClick, Supplier<ItemStack> stackSupplier) {
			super(position, Dimension.SQUARE_18, onClick);
			this.stackSupplier = stackSupplier;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
			ItemStack stack = stackSupplier.get();
			if (stack.isEmpty()) {
				return;
			}

			GuiHelper.renderItemInGUI(guiGraphics, minecraft, stack, x + 1, y + 1);
			if (isMouseOver(mouseX, mouseY)) {
				GuiHelper.blit(guiGraphics, x, y, BUTTON_HOVER);
			}
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			//noop
		}
	}

	/**
	 * 26.3 的 GUI 提取阶段没有 PoseStack / BufferSource，无法再做 3D 模型旋转预览，
	 * 这里改为在深色框内以物品图标展示当前选中的预览结果，多结果时底部提供切换按钮。
	 */
	private static class BlockPreview extends CompositeWidgetBase<WidgetBase> {
		private final List<ItemStack> previewStacks = new ArrayList<>();
		private int selectedPreview = 0;
		private final List<StackButton> previewStackButtons = new ArrayList<>();

		protected BlockPreview(Position position, Dimension dimension) {
			super(position, dimension);
		}

		public void setPreviewStacks(List<ItemStack> previewStacks) {
			this.previewStacks.clear();
			this.previewStacks.addAll(previewStacks);
			this.selectedPreview = 0;
			updatePreviewStackButtons();
		}

		private void updatePreviewStackButtons() {
			previewStackButtons.forEach(this.children::remove);
			previewStackButtons.clear();

			if (previewStacks.size() < 2) {
				return;
			}

			int startX = this.x + (this.getWidth() - previewStacks.size() * (18 + 2)) / 2;
			for (int i = 0; i < previewStacks.size(); i++) {
				ItemStack stack = previewStacks.get(i);
				int finalI = i;
				previewStackButtons.add(new StackButton(new Position(startX + i * 20, y + getHeight() - 19), button -> selectedPreview = finalI, () -> stack));
			}
			previewStackButtons.forEach(this::addChild);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
			// 组合控件子控件需要显式提取渲染状态（见 DecorationTableScreen#renderColorPicker）
			for (WidgetBase child : children) {
				child.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
			}
			renderPreviewStack(guiGraphics);
		}

		private void renderPreviewStack(GuiGraphicsExtractor guiGraphics) {
			if (previewStacks.isEmpty()) {
				return;
			}

			ItemStack previewStack = previewStacks.get(Math.min(selectedPreview, previewStacks.size() - 1));
			if (previewStack.isEmpty()) {
				return;
			}

			int usableHeight = getHeight() - (previewStackButtons.isEmpty() ? 0 : 20);
			int itemX = x + (getWidth() - 16) / 2;
			int itemY = y + (usableHeight - 16) / 2;
			GuiHelper.renderItemInGUI(guiGraphics, minecraft, previewStack, itemX, itemY);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
			int border = 0xFF_404040;
			guiGraphics.fill(x, y, x + getWidth(), y + getHeight(), 0xFF_101010);
			guiGraphics.fill(x, y, x + getWidth(), y + 1, border);
			guiGraphics.fill(x, y + getHeight() - 1, x + getWidth(), y + getHeight(), border);
			guiGraphics.fill(x, y, x + 1, y + getHeight(), border);
			guiGraphics.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), border);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
			//noop
		}
	}
}
