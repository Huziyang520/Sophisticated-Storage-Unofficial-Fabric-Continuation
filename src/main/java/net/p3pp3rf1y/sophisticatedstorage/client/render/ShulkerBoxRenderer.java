package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.ShulkerBoxBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.ShulkerBoxBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;

/**
 * Block entity renderer for the shulker box, ported from the 1.21.1 upstream renderer to the 26.3
 * "extract state / submit" render pipeline.
 * <p>
 * Geometry is the vanilla shulker box layer ({@link ModelLayers#SHULKER_BOX}, 64x64) driven by the open progress;
 * textures are resolved through {@link Sheets#SHULKER_MAPPER} into this mod's textures under
 * {@code textures/entity/shulker/}.
 */
public class ShulkerBoxRenderer implements BlockEntityRenderer<ShulkerBoxBlockEntity, ShulkerBoxRenderer.ShulkerRenderState> {
	private static final String BASE_TIER = "base_tier";
	private static final String COPPER_TIER = "copper_tier";
	private static final String IRON_TIER = "iron_tier";
	private static final String GOLD_TIER = "gold_tier";
	private static final String DIAMOND_TIER = "diamond_tier";
	private static final String NETHERITE_TIER = "netherite_tier";
	private static final String TINTABLE_MAIN = "tintable_main";
	private static final String TINTABLE_ACCENT = "tintable_accent";
	private static final String NO_TINT = "no_tint";

	private final SpriteGetter sprites;
	/**
	 * 真正负责画的那一个：直接用 vanilla 的渲染器实例，连 pose 变换都不自己算。
	 * 之前手写 submit / 手写模型导致的“方块整体偏半格”就是这么来的——模型空间、0.9995 缩放、
	 * translate(0.5,0.5,0.5) 都在 modelTransform 里，任何一处手写都会积累误差。
	 */
	private final net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer vanilla;

	public ShulkerBoxRenderer(BlockEntityRendererProvider.Context context) {
		this(context.entityModelSet(), context.sprites());
	}

	/**
	 * Used by {@link ShulkerBoxItemRenderer}: the item side has no block entity renderer context, only the entity
	 * model set and the sprite getter handed to a special model renderer when it is baked.
	 */
	public ShulkerBoxRenderer(EntityModelSet modelSet, SpriteGetter sprites) {
		this.sprites = sprites;
		vanilla = new net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer(modelSet, sprites);
	}

	@Override
	public ShulkerRenderState createRenderState() {
		return new ShulkerRenderState();
	}

	@Override
	public void extractRenderState(ShulkerBoxBlockEntity shulkerBox, ShulkerRenderState state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(shulkerBox, state, crumblingOverlay);

		BlockState blockState = shulkerBox.getBlockState();
		state.direction = blockState.getValueOrElse(ShulkerBoxBlock.FACING, Direction.UP);
		state.progress = shulkerBox.getProgress(partialTick);
		state.tierName = tierName(blockState.getBlock());

		StorageWrapper storageWrapper = shulkerBox.getStorageWrapper();
		state.mainColor = storageWrapper.getMainColor();
		state.accentColor = storageWrapper.getAccentColor();

		state.showTier = shulkerBox.shouldShowTier();
	}

	@Override
	public void submit(ShulkerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// 世界内的方块：vanilla 私有 submit 做的就是 push + modelTransform + 画 + pop。
		// 这个变换里有 translate(0.5,0.5,0.5) 和 scale(1,-1,-1)，少了它盒子就会上下颠倒并偏半格。
		poseStack.pushPose();
		poseStack.mulPose(net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer.modelTransform(state.direction));
		submitLayers(state, poseStack, collector);
		poseStack.popPose();
	}

	/**
	 * 物品形态：vanilla 的 special renderer 调的是不带方向的公开 submit，姿态由物品定义的
	 * transformation 提供（translation 0.5 / 1.4995 / 0.5），这里不能再套 modelTransform，否则会二次变换。
	 */
	public void submitItem(ShulkerRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		submitLayers(state, poseStack, collector);
	}

	private void submitLayers(ShulkerRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.mainColor == -1 || state.accentColor == -1) {
			submitLayer(state, poseStack, collector, sprite(NO_TINT), -1);
		}
		if (state.mainColor != -1) {
			submitLayer(state, poseStack, collector, sprite(TINTABLE_MAIN), tint(state.mainColor));
		}
		if (state.accentColor != -1) {
			submitLayer(state, poseStack, collector, sprite(TINTABLE_ACCENT), tint(state.accentColor));
		}
		if (state.showTier && !BASE_TIER.equals(state.tierName)) {
			submitLayer(state, poseStack, collector, sprite(state.tierName), -1);
		}
	}

	private void submitLayer(ShulkerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, SpriteId sprite, int color) {
		vanilla.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.progress, state.breakProgress, sprite, color);
	}

	private static int tint(int color) {
		return 0xFF_000000 | color;
	}

	/** shared with {@link ShulkerBoxItemRenderer}, which has to resolve the same tier sprite for an item stack */
	static String tierName(Block block) {
		if (block == ModBlocks.COPPER_SHULKER_BOX.get()) {
			return COPPER_TIER;
		} else if (block == ModBlocks.IRON_SHULKER_BOX.get()) {
			return IRON_TIER;
		} else if (block == ModBlocks.GOLD_SHULKER_BOX.get()) {
			return GOLD_TIER;
		} else if (block == ModBlocks.DIAMOND_SHULKER_BOX.get()) {
			return DIAMOND_TIER;
		} else if (block == ModBlocks.NETHERITE_SHULKER_BOX.get()) {
			return NETHERITE_TIER;
		}
		return BASE_TIER;
	}

	private static SpriteId sprite(String name) {
		return Sheets.SHULKER_MAPPER.apply(SophisticatedStorage.getRL(name));
	}

	/**
	 * Same shape as the vanilla 26.3 shulker box model (which is package private), but using the no-cull cutout
	 * render type like upstream does so the inside of the opening lid stays visible.
	 */
	private static class StorageShulkerBoxModel extends Model<Float> {
		private final ModelPart lid;

		StorageShulkerBoxModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			lid = root.getChild("lid");
		}

		@Override
		public void setupAnim(Float progress) {
			super.setupAnim(progress);
			lid.setPos(0.0F, 24.0F - progress * 0.5F * 16.0F, 0.0F);
			lid.yRot = 270.0F * progress * ((float) Math.PI / 180F);
		}
	}

	public static class ShulkerRenderState extends BlockEntityRenderState {
		public Direction direction = Direction.UP;
		public float progress;
		public String tierName = BASE_TIER;
		public int mainColor = -1;
		public int accentColor = -1;
		public boolean showTier;
	}
}
