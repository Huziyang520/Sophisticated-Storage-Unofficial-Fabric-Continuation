package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.LimitedBarrelBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.VerticalFacing;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the items a limited barrel holds in the world.
 * <p>
 * The limited barrel is the one storage block whose whole point is seeing its contents without opening it (upstream
 * has a dedicated block entity renderer for exactly that), so its slots are drawn on the front face, four at most -
 * one centred, two stacked, three / four in a 2x2 grid, the same layout upstream uses.
 * <p>
 * The block model itself is only the barrel; the items are submitted as {@link ItemStackRenderState}s extracted once
 * per frame from the block entity's inventory handler, which means they follow the filter/memory slots of the
 * barrel exactly like the slots shown in its screen.
 */
public class LimitedBarrelRenderer implements BlockEntityRenderer<LimitedBarrelBlockEntity, LimitedBarrelRenderer.LimitedBarrelRenderState> {
	private static final int MAX_SLOTS = 4;

	public LimitedBarrelRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
		// nothing to bake: the barrel geometry comes from the block model, the renderer only adds the slot items
	}

	@Override
	public LimitedBarrelRenderState createRenderState() {
		return new LimitedBarrelRenderState();
	}

	@Override
	public void extractRenderState(LimitedBarrelBlockEntity barrel, LimitedBarrelRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(barrel, state, crumblingOverlay);

		BlockState blockState = barrel.getBlockState();
		state.flatTop = blockState.getValueOrElse(BarrelBlock.FLAT_TOP, false);
		state.horizontalFacing = blockState.getValueOrElse(LimitedBarrelBlock.HORIZONTAL_FACING, Direction.NORTH);
		state.verticalFacing = blockState.getValueOrElse(LimitedBarrelBlock.VERTICAL_FACING, VerticalFacing.NO);

		int slots = barrel.isPacked() ? 0 : Math.min(MAX_SLOTS, barrel.getStorageWrapper().getInventoryHandler().getSlotCount());
		state.slotCount = slots;

		ItemModelResolver itemModelResolver = Minecraft.getInstance().getItemModelResolver();
		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			state.items[slot].clear();
			if (slot >= slots) {
				continue;
			}
			ItemStack stack = barrel.getStorageWrapper().getInventoryHandler().getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			itemModelResolver.updateForTopItem(state.items[slot], stack, ItemDisplayContext.FIXED, barrel.getLevel(), null, slot);
		}
	}

	@Override
	public void submit(LimitedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.slotCount <= 0) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D);
		poseStack.rotate(rotationOf(state.horizontalFacing.getOpposite()));
		if (state.verticalFacing != VerticalFacing.NO) {
			poseStack.rotate(rotationOf(state.verticalFacing.getDirection().getOpposite()));
		}
		// front face plane: the barrel body ends 1/16 before the block edge unless it is a flat top one
		poseStack.translate(-0.5D, -0.5D, -(0.5D - (state.flatTop ? 0 : 1 / 16D)));

		float itemScale = state.slotCount == 1 ? 0.5F : 0.25F;
		for (int slot = 0; slot < state.slotCount; slot++) {
			ItemStackRenderState item = state.items[slot];
			if (item.isEmpty()) {
				continue;
			}

			Vector3f frontOffset = getFrontOffset(slot, state.slotCount);
			poseStack.pushPose();
			poseStack.translate(frontOffset.x(), frontOffset.y(), -0.005D);
			poseStack.scale(itemScale, itemScale, itemScale);
			item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

	/** Mirrors {@code DisplayItemRenderer#getDisplayItemIndexFrontOffset} of upstream (x/y in the 0..1 face space). */
	private static Vector3f getFrontOffset(int slot, int slotCount) {
		if (slotCount <= 1) {
			return new Vector3f(0.5F, 0.5F, 0.5F);
		}
		if (slotCount == 2) {
			return new Vector3f(0.5F, slot == 0 ? 0.75F : 0.25F, 0.5F);
		}
		if (slotCount == 3) {
			float x = slot == 0 ? 0.5F : (slot == 1 ? 0.25F : 0.75F);
			return new Vector3f(x, slot == 0 ? 0.75F : 0.25F, 0.5F);
		}
		float x = slot == 0 || slot == 2 ? 0.75F : 0.25F;
		float y = slot == 0 || slot == 1 ? 0.75F : 0.25F;
		return new Vector3f(x, y, 0.5F);
	}

	/** North based rotation, i.e. the same convention the block models and upstream use. */
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
		public int slotCount;
		public boolean flatTop;
		public Direction horizontalFacing = Direction.NORTH;
		public VerticalFacing verticalFacing = VerticalFacing.NO;

		public LimitedBarrelRenderState() {
			for (int slot = 0; slot < MAX_SLOTS; slot++) {
				items[slot] = new ItemStackRenderState();
			}
		}
	}
}
