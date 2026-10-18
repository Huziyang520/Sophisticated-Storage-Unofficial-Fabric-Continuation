package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock;
import net.p3pp3rf1y.sophisticatedstorage.block.ChestBlockEntity;
import net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper;
import net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockBase;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Block entity renderer for the chest, ported from the 1.21.1 upstream renderer to the 26.3
 * "extract state / submit" render pipeline.
 * <p>
 * Geometry comes from the vanilla {@link ChestModel} layers ({@link ModelLayers#CHEST},
 * {@link ModelLayers#DOUBLE_CHEST_LEFT}, {@link ModelLayers#DOUBLE_CHEST_RIGHT}); textures are resolved through
 * {@link Sheets#CHEST_MAPPER} into this mod's 64x64 chest atlas textures under {@code textures/entity/chest/}.
 */
public class ChestRenderer implements BlockEntityRenderer<ChestBlockEntity, ChestRenderer.ChestRenderState> {
	private static final String BOTTOM = "bottom";
	private static final String LID = "lid";
	private static final String LOCK = "lock";

	private static final String WOOD_TIER = "wood_tier";
	private static final String COPPER_TIER = "copper_tier";
	private static final String IRON_TIER = "iron_tier";
	private static final String GOLD_TIER = "gold_tier";
	private static final String DIAMOND_TIER = "diamond_tier";
	private static final String NETHERITE_TIER = "netherite_tier";
	private static final String TINTABLE_MAIN = "tintable_main";
	private static final String TINTABLE_ACCENT = "tintable_accent";
	private static final String PACKED = "packed";

	private final SpriteGetter sprites;
	private final Map<ChestType, ChestModels> models = new EnumMap<>(ChestType.class);

	public ChestRenderer(BlockEntityRendererProvider.Context context) {
		sprites = context.sprites();
		models.put(ChestType.SINGLE, createModels(context, ModelLayers.CHEST));
		models.put(ChestType.LEFT, createModels(context, ModelLayers.DOUBLE_CHEST_LEFT));
		models.put(ChestType.RIGHT, createModels(context, ModelLayers.DOUBLE_CHEST_RIGHT));
	}

	/**
	 * Each chest type needs two independent model instances: one that draws the body / lid (lock hidden) and one
	 * that only draws the latch. {@code submitModel} defers the actual drawing, so part visibility has to be baked
	 * into separate model trees instead of being toggled per submission.
	 */
	private static ChestModels createModels(BlockEntityRendererProvider.Context context, ModelLayerLocation layer) {
		ModelPart bodyRoot = context.bakeLayer(layer);
		bodyRoot.getChild(LOCK).visible = false;

		ModelPart lockRoot = context.bakeLayer(layer);
		lockRoot.getChild(LID).visible = false;
		lockRoot.getChild(BOTTOM).visible = false;

		return new ChestModels(new ChestModel(bodyRoot), new ChestModel(lockRoot));
	}

	@Override
	public ChestRenderState createRenderState() {
		return new ChestRenderState();
	}

	@Override
	public void extractRenderState(ChestBlockEntity chest, ChestRenderState state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(chest, state, crumblingOverlay);

		BlockState blockState = chest.getBlockState();
		state.type = blockState.getValue(ChestBlock.TYPE);
		state.facing = blockState.getValue(ChestBlock.FACING);
		state.open = chest.getOpenNess(partialTick);
		state.woodType = chest.getWoodType().orElse(null);
		state.tierName = tierName(blockState.getBlock());

		StorageWrapper storageWrapper = chest.getMainStorageWrapper();
		state.mainColor = storageWrapper.getMainColor();
		state.accentColor = storageWrapper.getAccentColor();

		state.showTier = chest.shouldShowTier();
		state.packed = chest.isPacked();
	}

	@Override
	public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		ChestModels chestModels = models.get(state.type);
		if (chestModels == null) {
			return;
		}

		poseStack.pushPose();
		poseStack.mulPose(net.minecraft.client.renderer.blockentity.ChestRenderer.modelTransformation(state.facing));

		float lidAngle = 1.0F - state.open;
		lidAngle = 1.0F - lidAngle * lidAngle * lidAngle;

		boolean hasMainColor = state.mainColor != -1;
		boolean hasAccentColor = state.accentColor != -1;

		SpriteId baseSprite = sprite(state.type, baseName(state.woodType));
		if (state.woodType != null || !(hasMainColor && hasAccentColor)) {
			submitModel(chestModels.body(), lidAngle, state, poseStack, collector, baseSprite, -1);
			submitCrumblingOverlay(chestModels.body(), lidAngle, state, poseStack, collector, baseSprite);
		}
		if (hasMainColor) {
			submitModel(chestModels.body(), lidAngle, state, poseStack, collector, sprite(state.type, TINTABLE_MAIN), tint(state.mainColor));
		}
		if (hasAccentColor) {
			submitModel(chestModels.body(), lidAngle, state, poseStack, collector, sprite(state.type, TINTABLE_ACCENT), tint(state.accentColor));
		}
		if (state.showTier) {
			submitModel(chestModels.body(), lidAngle, state, poseStack, collector, sprite(state.type, state.tierName), -1);
		}

		if (state.packed) {
			poseStack.pushPose();
			poseStack.translate(-0.005D, -0.005D, -0.005D);
			poseStack.scale(1.01F, 1.01F, 1.01F);
			submitModel(chestModels.body(), lidAngle, state, poseStack, collector, sprite(state.type, PACKED), -1);
			poseStack.popPose();
		}

		// the latch is drawn with the tier texture, like upstream does
		submitModel(chestModels.lock(), lidAngle, state, poseStack, collector, sprite(state.type, state.tierName), -1);

		poseStack.popPose();
	}

	private void submitModel(ChestModel model, float lidAngle, ChestRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, SpriteId sprite, int color) {
		collector.submitModel(model, lidAngle, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, color, sprite, sprites, 0);
	}

	private void submitCrumblingOverlay(ChestModel model, float lidAngle, ChestRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, SpriteId sprite) {
		if (state.breakProgress == null) {
			return;
		}
		collector.order(0).submitCrumblingOverlay(model, lidAngle, poseStack, sprite.renderType(model.renderType()),
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
	}

	private static int tint(int color) {
		return 0xFF_000000 | color;
	}

	private static String baseName(@Nullable WoodType woodType) {
		WoodType wood = woodType != null && WoodStorageBlockBase.CUSTOM_TEXTURE_WOOD_TYPES.containsKey(woodType) ? woodType : WoodType.ACACIA;
		return wood.name();
	}

	private static String tierName(Block block) {
		if (block == ModBlocks.COPPER_CHEST.get()) {
			return COPPER_TIER;
		} else if (block == ModBlocks.IRON_CHEST.get()) {
			return IRON_TIER;
		} else if (block == ModBlocks.GOLD_CHEST.get()) {
			return GOLD_TIER;
		} else if (block == ModBlocks.DIAMOND_CHEST.get()) {
			return DIAMOND_TIER;
		} else if (block == ModBlocks.NETHERITE_CHEST.get()) {
			return NETHERITE_TIER;
		}
		return WOOD_TIER;
	}

	private static SpriteId sprite(ChestType chestType, String name) {
		String prefix = switch (chestType) {
			case LEFT -> "left_";
			case RIGHT -> "right_";
			default -> "";
		};
		return Sheets.CHEST_MAPPER.apply(SophisticatedStorage.getRL(prefix + name));
	}

	private record ChestModels(ChestModel body, ChestModel lock) {
	}

	public static class ChestRenderState extends BlockEntityRenderState {
		public ChestType type = ChestType.SINGLE;
		public Direction facing = Direction.NORTH;
		public float open;
		@Nullable
		public WoodType woodType;
		public String tierName = WOOD_TIER;
		public int mainColor = -1;
		public int accentColor = -1;
		public boolean showTier;
		public boolean packed;
	}
}
