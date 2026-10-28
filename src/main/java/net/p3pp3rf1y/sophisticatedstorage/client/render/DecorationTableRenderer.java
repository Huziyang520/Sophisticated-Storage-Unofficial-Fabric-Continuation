package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedstorage.block.DecorationTableBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.DecorationTableBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.init.ModItems;
import org.jetbrains.annotations.Nullable;

/**
 * 装饰台上方显示当前结果物，按 26.3 的「提取状态 / 提交」管线重写。
 */
public class DecorationTableRenderer implements BlockEntityRenderer<DecorationTableBlockEntity, DecorationTableRenderer.DecorationTableRenderState> {
	@Override
	public DecorationTableRenderState createRenderState() {
		return new DecorationTableRenderState();
	}

	@Override
	public void extractRenderState(DecorationTableBlockEntity table, DecorationTableRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(table, state, crumblingOverlay);
		state.facing = table.getBlockState().getValue(DecorationTableBlock.FACING);
		state.result.clear();
		ItemStack result = table.getResult();
		if (result.isEmpty() || result.getItem() == ModItems.PAINTBRUSH.get()) {
			return;
		}
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.result, result, ItemDisplayContext.FIXED, table.getLevel(), null, 0);
	}

	@Override
	public void submit(DecorationTableRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.result.isEmpty()) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate(0.5, 1.125, 0.5);
		poseStack.rotate(state.facing.getOpposite().getRotation());
		poseStack.rotate(Axis.XN.rotationDegrees(90));
		poseStack.translate(0, 0, -0.1);
		poseStack.scale(0.5f, 0.5f, 0.5f);
		state.result.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	public static class DecorationTableRenderState extends BlockEntityRenderState {
		public final ItemStackRenderState result = new ItemStackRenderState();
		public Direction facing = Direction.NORTH;
	}
}
