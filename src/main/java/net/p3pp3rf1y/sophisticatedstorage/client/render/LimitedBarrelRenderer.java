package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.renderdata.RenderInfo;
import net.p3pp3rf1y.sophisticatedcore.util.CountAbbreviator;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.IStorageBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.VerticalFacing;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Draws what a limited barrel holds on its front face: one item per slot, the amount under each item and - when the
 * player turned fill levels on - a small fill bar behind each slot.
 * <p>
 * Every position is expressed on the <b>front face</b> of the block: the pose is moved to the block centre, rotated by
 * {@code HORIZONTAL_FACING} (plus {@code VERTICAL_FACING} when the barrel is mounted on a wall or a ceiling) and then
 * moved out to the face plane. The items use the facing direction <b>as is</b>; only the count text is drawn with the
 * opposite direction, because the glyphs have to face the viewer instead of being mirrored. Using the opposite
 * direction for the items - as the previous version did - put the whole item grid on the <i>back</i> face of the
 * block, buried inside the barrel body, which is why nothing was ever visible in world.
 * <p>
 * The items come from the storage wrapper's render info ({@code ItemDisplayRenderInfo}) and not from the inventory
 * handler: a storage block's inventory is never sent to the client, only the render info is (through
 * {@code StorageBlockEntity#createClientData}). {@code LimitedBarrelBlock#setupDefaultSettings} is what makes the
 * block select its slots for the display in the first place, so without that call the render info stays empty.
 * <p>
 * The slot layout is decided by the block's slot count (always four for a limited barrel) instead of by how many
 * slots currently hold something, so emptying a slot does not make the other items jump around. A packed barrel
 * (packed with a packing tape) hides its contents entirely, like upstream.
 */
public class LimitedBarrelRenderer implements BlockEntityRenderer<LimitedBarrelBlockEntity, LimitedBarrelRenderer.LimitedBarrelRenderState> {
	private static final int MAX_SLOTS = 4;
	private static final float MULTIPLE_ITEMS_FONT_SCALE = 1 / 96f;
	private static final float SINGLE_ITEM_FONT_SCALE = 1 / 48f;
	private static final Style COUNT_DISPLAY_STYLE = Style.EMPTY.withBold(true).withColor(ChatFormatting.WHITE);
	private static final Identifier FILL_INDICATORS = SophisticatedStorage.getRL("block/fill_indicators");
	/** a 3D block item is drawn twice as large as a flat item */
	private static final float BLOCK_ITEM_3D_SCALE = 0.5F;
	private static final float FLAT_ITEM_SCALE = 0.25F;
	/** items are placed this far in front of the face plane so they never z-fight with the barrel's front face */
	private static final float LITTLE_OUTSIDE_FACE = 0.002F;
	private static final float GUI_3D_MIN_DEPTH = 0.0625F;
	/**
	 * Logs what the renderer actually sees, once per client session, so one world load with a filled limited barrel in
	 * it is enough to prove the renderer runs and that it has data (look for the {@code [limited-barrel-ber]} line in
	 * the client log). Remove once that has been checked.
	 */
	private static final AtomicBoolean DIAGNOSTIC_LOGGED = new AtomicBoolean(false);

	public LimitedBarrelRenderer(BlockEntityRendererProvider.Context context) {
		// nothing to bake: the barrel geometry comes from the block model, the renderer only adds the contents
	}

	@Override
	public LimitedBarrelRenderState createRenderState() {
		return new LimitedBarrelRenderState();
	}

	@Override
	public void extractRenderState(LimitedBarrelBlockEntity barrel, LimitedBarrelRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(barrel, state, crumblingOverlay);

		BlockState blockState = barrel.getBlockState();
		state.packed = barrel.isPacked();
		state.flatTop = blockState.getValueOrElse(BarrelBlock.FLAT_TOP, false);
		state.horizontalFacing = blockState.getValueOrElse(LimitedBarrelBlock.HORIZONTAL_FACING, Direction.NORTH);
		state.verticalFacing = blockState.getValueOrElse(LimitedBarrelBlock.VERTICAL_FACING, VerticalFacing.NO);
		// the block's own slot count - the client side inventory handler may still be an empty one when this runs
		state.slotCount = blockState.getBlock() instanceof IStorageBlock storageBlock
				? Mth.clamp(storageBlock.getNumberOfInventorySlots(), 1, MAX_SLOTS) : MAX_SLOTS;

		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			state.items[slot].clear();
			state.counts[slot] = 0;
			state.fillLevels[slot] = 0;
			state.scales[slot] = FLAT_ITEM_SCALE;
			state.zOffsets[slot] = -LITTLE_OUTSIDE_FACE;
			state.rotations[slot] = 0;
			// 槽位文字颜色只来自方块实体的 slotColors（{@link LimitedBarrelBlockEntity#getSlotColor}），
			// 与「这个槽里现在有没有物品」无关：物品被取空时颜色仍然保留，不会退回未染色。
			state.colors[slot] = barrel.getSlotColor(slot);
		}
		state.fillCount = 0;
		state.showsCounts = barrel.shouldShowCounts();
		state.showsFillLevels = barrel.shouldShowFillLevels();
		state.lightCoords = frontLightCoords(barrel, state);

		RenderInfo.ItemDisplayRenderInfo displayInfo = barrel.getStorageWrapper().getRenderInfo().getItemDisplayRenderInfo();
		if (!state.packed) {
			extractItems(barrel, state, displayInfo);
			extractCounts(barrel, state, displayInfo);
			extractFillLevels(barrel, state, displayInfo);
		}

		if (DIAGNOSTIC_LOGGED.compareAndSet(false, true)) {
			// 渲染路径里不能留 info 级日志（每帧都可能命中，且字符串拼接昂贵）：降为 debug
			SophisticatedStorage.LOGGER.debug("[limited-barrel-ber] extract pos={} packed={} slots={} displayItems={} counts={} light={}",
					barrel.getBlockPos(), state.packed, state.slotCount, displayInfo.getDisplayItems().size(),
					List.of(state.counts[0], state.counts[1], state.counts[2], state.counts[3]), state.lightCoords);
		}
	}

	private static void extractItems(LimitedBarrelBlockEntity barrel, LimitedBarrelRenderState state,
			RenderInfo.ItemDisplayRenderInfo displayInfo) {
		List<RenderInfo.DisplayItem> displayItems = displayInfo.getDisplayItems();
		ItemModelResolver itemModelResolver = Minecraft.getInstance().getItemModelResolver();
		int highestOccupiedSlot = -1;
		for (int i = 0; i < displayItems.size(); i++) {
			RenderInfo.DisplayItem displayItem = displayItems.get(i);
			// a limited barrel has fixed per slot display items, so the slot index is also the layout position
			int slot = displayItem.getSlotIndex() >= 0 && displayItem.getSlotIndex() < MAX_SLOTS ? displayItem.getSlotIndex() : i;
			if (slot >= MAX_SLOTS) {
				continue;
			}
			ItemStack stack = displayItem.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			itemModelResolver.updateForTopItem(state.items[slot], stack, ItemDisplayContext.FIXED, barrel.getLevel(), null, slot);
			if (state.items[slot].isEmpty()) {
				continue;
			}
			state.rotations[slot] = displayItem.getRotation();
			applyItemGeometry(state, slot, stack);
			highestOccupiedSlot = Math.max(highestOccupiedSlot, slot);
		}
		// keep every slot of the grid even when it is empty, but never crop an occupied one
		state.slotCount = Mth.clamp(Math.max(state.slotCount, highestOccupiedSlot + 1), 1, MAX_SLOTS);
	}

	/**
	 * A 3D block item is drawn at 0.5 instead of 0.25 in the multi slot grid, and it is pushed into the barrel by its
	 * own half depth so its visible face lands on the barrel's face instead of sticking half a block out of it.
	 */
	private static void applyItemGeometry(LimitedBarrelRenderState state, int slot, ItemStack stack) {
		boolean isBlockItem = stack.getItem() instanceof BlockItem;
		boolean isGui3d = isGui3d(state.items[slot]);
		float scale;
		if (state.slotCount <= 1) {
			scale = isBlockItem && isGui3d ? 1F : 2 * FLAT_ITEM_SCALE;
		} else {
			scale = isBlockItem && isGui3d ? BLOCK_ITEM_3D_SCALE : FLAT_ITEM_SCALE;
		}
		state.scales[slot] = scale;
		if (isBlockItem && isGui3d) {
			state.zOffsets[slot] = (float) extentMaxZ(state.items[slot]) * scale - LITTLE_OUTSIDE_FACE;
		}
	}

	private static boolean isGui3d(ItemStackRenderState item) {
		AABB extents = extents(item);
		return extents != null && extents.getZsize() > GUI_3D_MIN_DEPTH;
	}

	private static double extentMaxZ(ItemStackRenderState item) {
		AABB extents = extents(item);
		return extents == null ? 0 : extents.maxZ;
	}

	@Nullable
	private static AABB extents(ItemStackRenderState item) {
		AABB.Builder builder = new AABB.Builder();
		item.visitExtents(builder::include);
		return builder.isDefined() ? builder.build() : null;
	}

	private static void extractCounts(LimitedBarrelBlockEntity barrel, LimitedBarrelRenderState state,
			RenderInfo.ItemDisplayRenderInfo displayInfo) {
		// the block entity's own synchronised list first, the render info as the fallback
		List<Integer> slotCounts = barrel.getSlotCounts();
		if (slotCounts.isEmpty()) {
			slotCounts = displayInfo.getSlotCounts();
		}
		for (int slot = 0; slot < Math.min(MAX_SLOTS, slotCounts.size()); slot++) {
			state.counts[slot] = slotCounts.get(slot);
		}
		// an item on the face with no synced count still has to show a count - the item itself proves the slot is not
		// empty (older save, or a slot that only came through the render info)
		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			if (!state.items[slot].isEmpty() && state.counts[slot] <= 0) {
				state.counts[slot] = 1;
			}
		}
	}

	private static void extractFillLevels(LimitedBarrelBlockEntity barrel, LimitedBarrelRenderState state,
			RenderInfo.ItemDisplayRenderInfo displayInfo) {
		if (!state.showsFillLevels) {
			return;
		}
		List<Float> fillLevels = barrel.getSlotFillLevels();
		if (fillLevels.isEmpty()) {
			fillLevels = displayInfo.getSlotFillRatios();
		}
		state.fillCount = Math.min(MAX_SLOTS, fillLevels.size());
		for (int slot = 0; slot < state.fillCount; slot++) {
			state.fillLevels[slot] = Mth.clamp(fillLevels.get(slot), 0, 1);
		}
	}

	/**
	 * Light for everything the renderer draws on the front face, sampled in the <b>air block in front of</b> the face
	 * (upstream does the same).
	 * <p>
	 * {@link BlockEntityRenderState#extractBase} samples the block entity's own position instead, and a barrel is a
	 * light occluding full block: the sky light inside it is 0 and, unless the block glows, so is the block light. The
	 * packed light therefore ended up as 0, and the light map coordinate (0, 0) is black - every display item, every
	 * block item and the count text were drawn pure black (the black icons / the black square on the barrel front in
	 * the bug report). The air block in front of the face carries the light the player actually sees the barrel in.
	 */
	private static int frontLightCoords(LimitedBarrelBlockEntity barrel, LimitedBarrelRenderState state) {
		if (barrel.getLevel() == null) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
		Direction front = state.verticalFacing != VerticalFacing.NO ? state.verticalFacing.getDirection() : state.horizontalFacing;
		return LightCoordsUtil.getLightCoords(barrel.getLevel(), barrel.getBlockPos().relative(front));
	}

	@Override
	public void submit(LimitedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.packed || state.slotCount <= 0) {
			if (state.packed) {
				// 打包后内容全部隐藏，但打包外观必须看得出来（同一个 BE 只能有一个渲染器，所以由这里代画）
				PackedBarrelRenderer.submitOverlay(poseStack, collector, state.lightCoords, state.flatTop);
			}
			return;
		}
		boolean hasItems = false;
		for (int slot = 0; slot < state.slotCount; slot++) {
			hasItems |= !state.items[slot].isEmpty();
		}
		if (!hasItems && !state.showsCounts && !state.showsFillLevels) {
			return;
		}

		if (state.showsCounts) {
			submitItemCounts(state, poseStack, collector);
		}

		if (hasItems || state.showsFillLevels) {
			poseStack.pushPose();
			poseStack.translate(0.5D, 0.5D, 0.5D);
			poseStack.rotate(frontRotation(state));
			// the barrel body ends 1/16 before the block edge unless it is a flat top one
			poseStack.translate(-0.5D, -0.5D, -(0.5D - (state.flatTop ? 0 : 1 / 16D)));
			if (hasItems) {
				submitItems(state, poseStack, collector);
			}
			if (state.showsFillLevels) {
				submitFillLevels(state, poseStack, collector);
			}
			poseStack.popPose();
		}
	}

	private static void submitItems(LimitedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		for (int slot = 0; slot < state.slotCount; slot++) {
			ItemStackRenderState item = state.items[slot];
			if (item.isEmpty()) {
				continue;
			}

			Vector3f frontOffset = getFrontOffset(slot, state.slotCount);
			poseStack.pushPose();
			poseStack.translate(frontOffset.x(), frontOffset.y(), state.zOffsets[slot]);
			if (state.rotations[slot] != 0) {
				poseStack.rotate(Axis.ZP.rotationDegrees(state.rotations[slot]));
			}
			float scale = state.scales[slot];
			poseStack.scale(scale, scale, scale);
			item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	/**
	 * The count text sits on the same face as the items, but rotated with the opposite direction - that is what makes
	 * the glyphs readable from the front instead of mirrored.
	 */
	private void submitItemCounts(LimitedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		Font font = Minecraft.getInstance().font;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotate(countRotation(state));
		poseStack.translate(0.5F, -0.5F, 0.5F);

		float countDisplayYOffset = state.slotCount == 1 ? -0.25F : -0.11F;
		float scale = state.slotCount == 1 ? SINGLE_ITEM_FONT_SCALE : MULTIPLE_ITEMS_FONT_SCALE;
		for (int slot = 0; slot < state.slotCount; slot++) {
			int count = state.counts[slot];
			if (count <= 0) {
				continue;
			}
			Vector3f frontOffset = getFrontOffset(slot, state.slotCount);
			poseStack.pushPose();
			poseStack.translate(-frontOffset.x(), frontOffset.y() + countDisplayYOffset, 0.001F - (state.flatTop ? 0 : 0.75F / 16F));
			poseStack.scale(scale, -scale, scale);
			MutableComponent countString = Component.literal(CountAbbreviator.abbreviate(count, state.slotCount == 1 ? 6 : 5))
					.withStyle(COUNT_DISPLAY_STYLE);
			poseStack.translate(-font.width(countString) / 2F, 0, 0);
			collector.submitText(poseStack, 0, 0, countString.getVisualOrderText(), false, Font.DisplayMode.NORMAL,
					state.lightCoords, state.colors[slot], 0, 0);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

	/** Small fill bars in front of the slots, laid out the same way the items are (see {@code block/fill_indicators}). */
	private static void submitFillLevels(LimitedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
				.getSprite(FILL_INDICATORS);
		RenderType renderType = RenderTypes.entityCutout(sprite.atlasLocation());

		poseStack.pushPose();
		poseStack.translate(0, 0, -0.001F);
		switch (state.fillCount) {
			case 1 -> submitFillLevel(state, poseStack, collector, renderType, sprite, 0, 1 / 16F, 1 / 16F, true);
			case 2 -> {
				submitFillLevel(state, poseStack, collector, renderType, sprite, 0, 1 / 16F, 9 / 16F, false);
				submitFillLevel(state, poseStack, collector, renderType, sprite, 1, 1 / 16F, 1 / 16F, false);
			}
			case 3 -> {
				submitFillLevel(state, poseStack, collector, renderType, sprite, 0, 1 / 16F, 9 / 16F, false);
				submitFillLevel(state, poseStack, collector, renderType, sprite, 1, 14 / 16F, 1 / 16F, false);
				submitFillLevel(state, poseStack, collector, renderType, sprite, 2, 1 / 16F, 1 / 16F, false);
			}
			case 4 -> {
				submitFillLevel(state, poseStack, collector, renderType, sprite, 0, 14 / 16F, 9 / 16F, false);
				submitFillLevel(state, poseStack, collector, renderType, sprite, 1, 1 / 16F, 9 / 16F, false);
				submitFillLevel(state, poseStack, collector, renderType, sprite, 2, 14 / 16F, 1 / 16F, false);
				submitFillLevel(state, poseStack, collector, renderType, sprite, 3, 1 / 16F, 1 / 16F, false);
			}
			default -> {
			}
		}
		poseStack.popPose();
	}

	private static void submitFillLevel(LimitedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			RenderType renderType, TextureAtlasSprite sprite, int slot, float x, float y, boolean large) {
		float fillLevel = state.fillLevels[slot];
		if (fillLevel <= 0) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate(x + 1 / 16F / 5F, y + 1 / 16F / 5F, 0);
		int barHeight = large ? 14 : 6;
		poseStack.scale(1 / 16F / 5F * 3, fillLevel * 1 / 16F / 5F * (barHeight * 5 - 2), 1);

		float minU = large ? 0 : 3 / 128F;
		float maxV = large ? 68 / 128F : 28 / 128F;
		float minV = (1 - fillLevel) * maxV;
		float maxU = minU + 3 / 128F;
		int light = state.lightCoords;

		collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
			Vector3f normal = pose.transformNormal(0, 0, 1, new Vector3f());
			VertexConsumer spriteConsumer = sprite.wrap(consumer);
			addQuadVertex(pose.pose(), spriteConsumer, 0, 1, 0xFFFFFFFF, maxU, minV, light, normal);
			addQuadVertex(pose.pose(), spriteConsumer, 0, 0, 0xFFFFFFFF, maxU, maxV, light, normal);
			addQuadVertex(pose.pose(), spriteConsumer, 1, 0, 0xFFFFFFFF, minU, maxV, light, normal);
			addQuadVertex(pose.pose(), spriteConsumer, 1, 1, 0xFFFFFFFF, minU, minV, light, normal);
		});
		poseStack.popPose();
	}

	private static void addQuadVertex(Matrix4f matrix, VertexConsumer consumer, float x, float y, int color, float u, float v,
			int light, Vector3f normal) {
		Vector4f pos = new Vector4f(x, y, 0, 1F);
		matrix.transform(pos);
		consumer.addVertex(pos.x(), pos.y(), pos.z(), color, u, v, OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
	}

	/** Rotation that turns the north face of the block model into the barrel's front face. */
	private static Quaternionf frontRotation(LimitedBarrelRenderState state) {
		Quaternionf rotation = rotationOf(state.horizontalFacing);
		if (state.verticalFacing != VerticalFacing.NO) {
			rotation.mul(rotationOf(state.verticalFacing.getDirection()));
		}
		return rotation;
	}

	/** Same as {@link #frontRotation} but mirrored - the font has to be rotated with the opposite direction. */
	private static Quaternionf countRotation(LimitedBarrelRenderState state) {
		Quaternionf rotation = rotationOf(state.horizontalFacing.getOpposite());
		if (state.verticalFacing != VerticalFacing.NO) {
			rotation.mul(rotationOf(state.verticalFacing.getDirection().getOpposite()));
		}
		return rotation;
	}

	/** Mirrors the front offset layout of upstream (x / y in the 0..1 face space, slot 0 is the top left one). */
	private static Vector3f getFrontOffset(int slot, int slotCount) {
		if (slotCount <= 1) {
			return new Vector3f(0.5F, 0.5F, 0.5F);
		}
		if (slotCount == 2) {
			return new Vector3f(0.5F, slot == 0 ? 0.75F : 0.25F, 0.5F);
		}
		if (slotCount == 3) {
			// slot 0 centred on top, slots 1 / 2 in the bottom row
			float x = slot == 0 ? 0.5F : (slot == 1 ? 0.75F : 0.25F);
			return new Vector3f(x, slot == 0 ? 0.75F : 0.25F, 0.5F);
		}
		float x = slot % 2 == 0 ? 0.75F : 0.25F;
		float y = slot < 2 ? 0.75F : 0.25F;
		return new Vector3f(x, y, 0.5F);
	}

	/** North based rotation, i.e. the same convention the block models use. */
	private static Quaternionf rotationOf(Direction direction) {
		return switch (direction) {
			case DOWN -> Axis.XP.rotationDegrees(-90.0F);
			case UP -> Axis.XP.rotationDegrees(90.0F);
			case NORTH -> new Quaternionf();
			case SOUTH -> Axis.YP.rotationDegrees(180.0F);
			case WEST -> Axis.YP.rotationDegrees(90.0F);
			case EAST -> Axis.YP.rotationDegrees(-90.0F);
		};
	}

	public static class LimitedBarrelRenderState extends BlockEntityRenderState {
		public final ItemStackRenderState[] items = new ItemStackRenderState[MAX_SLOTS];
		public final int[] counts = new int[MAX_SLOTS];
		public final int[] colors = new int[MAX_SLOTS];
		public final int[] rotations = new int[MAX_SLOTS];
		public final float[] scales = new float[MAX_SLOTS];
		public final float[] zOffsets = new float[MAX_SLOTS];
		public final float[] fillLevels = new float[MAX_SLOTS];
		public int fillCount;
		public int slotCount;
		public boolean flatTop;
		public boolean packed;
		public boolean showsCounts = true;
		public boolean showsFillLevels;
		public Direction horizontalFacing = Direction.NORTH;
		public VerticalFacing verticalFacing = VerticalFacing.NO;

		public LimitedBarrelRenderState() {
			for (int slot = 0; slot < MAX_SLOTS; slot++) {
				items[slot] = new ItemStackRenderState();
			}
		}
	}
}
