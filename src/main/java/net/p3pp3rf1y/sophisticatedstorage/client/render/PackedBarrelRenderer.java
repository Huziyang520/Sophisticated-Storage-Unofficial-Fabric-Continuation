package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.BarrelBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws the "wrapped in cardboard" look of a barrel that was packed with a packing tape.
 * <p>
 * The barrel body is a static block model, so a packed barrel used to look exactly like an unpacked one - the packed
 * flag only existed in the block entity. Mirroring that flag into a block state property would have been the easy way
 * out, but inserting a property into an existing block changes the block state ids of every block registered after it,
 * which silently remaps the blocks of already saved worlds. So the packed look is drawn on top instead: a slightly
 * inflated box with the cardboard textures, submitted by a block entity renderer. No block state, no saved-data change
 * and no effect on a world that was created before this build.
 * <p>
 * Items in a packed barrel are hidden (see {@link LimitedBarrelRenderer}, which skips the contents when packed).
 */
public class PackedBarrelRenderer implements BlockEntityRenderer<BarrelBlockEntity, PackedBarrelRenderer.PackedBarrelRenderState> {
	private static final Identifier SIDE = SophisticatedStorage.getRL("block/barrel_side_packed_base");
	private static final Identifier TOP = SophisticatedStorage.getRL("block/barrel_top_packed_base");
	private static final Identifier BOTTOM = SophisticatedStorage.getRL("block/barrel_bottom_packed_base");
	/** keeps the overlay just outside the barrel body so the two surfaces never fight for the same depth */
	private static final float OVERTURE = 0.001F;
	private static final int FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT;

	public PackedBarrelRenderer(BlockEntityRendererProvider.Context context) {
		// nothing to bake: the overlay is a plain box with three textures
	}

	@Override
	public PackedBarrelRenderState createRenderState() {
		return new PackedBarrelRenderState();
	}

	@Override
	public void extractRenderState(BarrelBlockEntity barrel, PackedBarrelRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(barrel, state, crumblingOverlay);
		BlockState blockState = barrel.getBlockState();
		state.packed = barrel.isPacked();
		state.flatTop = blockState.getValueOrElse(BarrelBlock.FLAT_TOP, false);
		state.lightCoords = frontLightCoords(barrel);
	}

	/**
	 * Light of the air blocks around the barrel. {@code BlockEntityRenderState.extractBase} samples the barrel's own
	 * position, and a barrel is a light occluding full block, so that value is 0 and everything drawn with it comes out
	 * black.
	 */
	private static int frontLightCoords(BarrelBlockEntity barrel) {
		if (barrel.getLevel() == null) {
			return FULL_BRIGHT;
		}
		return LightCoordsUtil.getLightCoords(barrel.getLevel(), barrel.getBlockPos().above());
	}

	@Override
	public void submit(PackedBarrelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.packed) {
			return;
		}
		submitOverlay(poseStack, collector, state.lightCoords, state.flatTop);
	}

	/**
	 * Submits the cardboard box. Shared with {@link LimitedBarrelRenderer} so both barrel kinds look the same when
	 * packed (a block entity can only have one renderer, so the limited barrel's own renderer draws it).
	 */
	public static void submitOverlay(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, boolean flatTop) {
		// the barrel body ends one pixel before the block edge unless it is a flat top one, so the wrapper follows it
		float inset = flatTop ? 0 : 1 / 16F;
		float min = inset - OVERTURE;
		float max = 1 - inset + OVERTURE;

		poseStack.pushPose();

		submitFace(collector, poseStack, RenderTypes.entitySolid(SIDE), min, min, min, max, max, max,
				Direction.NORTH, lightCoords);
		submitFace(collector, poseStack, RenderTypes.entitySolid(SIDE), min, min, min, max, max, max,
				Direction.SOUTH, lightCoords);
		submitFace(collector, poseStack, RenderTypes.entitySolid(SIDE), min, min, min, max, max, max,
				Direction.WEST, lightCoords);
		submitFace(collector, poseStack, RenderTypes.entitySolid(SIDE), min, min, min, max, max, max,
				Direction.EAST, lightCoords);
		submitFace(collector, poseStack, RenderTypes.entitySolid(TOP), min, min, min, max, max, max,
				Direction.UP, lightCoords);
		submitFace(collector, poseStack, RenderTypes.entitySolid(BOTTOM), min, min, min, max, max, max,
				Direction.DOWN, lightCoords);

		poseStack.popPose();
	}

	/**
	 * One face of the box. Vertex order follows the vanilla block model convention so the faces are not culled away.
	 */
	private static void submitFace(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
			float x1, float y1, float z1, float x2, float y2, float z2, Direction face, int lightCoords) {
		collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
			float nx = face.getStepX();
			float ny = face.getStepY();
			float nz = face.getStepZ();
			switch (face) {
				case DOWN -> {
					vertex(pose.pose(), consumer, x1, y1, z2, 0, 0, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y1, z1, 0, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y1, z1, 1, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y1, z2, 1, 0, lightCoords, nx, ny, nz);
				}
				case UP -> {
					vertex(pose.pose(), consumer, x1, y2, z1, 0, 0, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y2, z2, 0, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y2, z2, 1, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y2, z1, 1, 0, lightCoords, nx, ny, nz);
				}
				case NORTH -> {
					vertex(pose.pose(), consumer, x2, y1, z1, 0, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y1, z1, 1, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y2, z1, 1, 0, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y2, z1, 0, 0, lightCoords, nx, ny, nz);
				}
				case SOUTH -> {
					vertex(pose.pose(), consumer, x1, y1, z2, 0, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y1, z2, 1, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y2, z2, 1, 0, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y2, z2, 0, 0, lightCoords, nx, ny, nz);
				}
				case WEST -> {
					vertex(pose.pose(), consumer, x1, y1, z1, 0, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y1, z2, 1, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y2, z2, 1, 0, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x1, y2, z1, 0, 0, lightCoords, nx, ny, nz);
				}
				case EAST -> {
					vertex(pose.pose(), consumer, x2, y1, z2, 0, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y1, z1, 1, 1, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y2, z1, 1, 0, lightCoords, nx, ny, nz);
					vertex(pose.pose(), consumer, x2, y2, z2, 0, 0, lightCoords, nx, ny, nz);
				}
				default -> {
					// every Direction is covered above
				}
			}
		});
	}

	private static void vertex(Matrix4f matrix, VertexConsumer consumer, float x, float y, float z, float u, float v,
			int lightCoords, float nx, float ny, float nz) {
		Vector4f pos = new Vector4f(x, y, z, 1F);
		matrix.transform(pos);
		consumer.addVertex(pos.x(), pos.y(), pos.z(), 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, lightCoords, nx, ny, nz);
	}

	public static class PackedBarrelRenderState extends BlockEntityRenderState {
		public boolean packed;
		public boolean flatTop;
	}
}
