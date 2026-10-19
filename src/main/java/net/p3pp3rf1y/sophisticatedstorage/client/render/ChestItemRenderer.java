package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.ITintableBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.ChestBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Special model renderer behind the {@code sophisticatedstorage:chest} item model type, used by all chest items
 * ({@code assets/sophisticatedstorage/items/*chest.json}).
 * <p>
 * A vanilla {@code minecraft:chest} special model only draws one flat texture and cannot stack the wood base, the
 * tintable main / accent layers, the tier overlay and the latch, which is why tiered chests lost almost everything but
 * their corners. This renderer instead reuses the block entity renderer ({@link ChestRenderer}) geometry: it extracts
 * everything the item stack carries (wood type, main / accent colour, tier, packed / shows tier) into a
 * {@link ChestRenderer.ChestRenderState} and submits it through the same code path the placed block uses, so the item
 * and the block always look the same.
 * <p>
 * 26.3 has no Fabric event for special model renderer types; {@link SpecialModelRenderers#ID_MAPPER} is a late bound
 * id mapper that is queried while an item model is decoded, so registering the codec before the resource reload that
 * decodes the item definitions is enough (the field is opened up through the access widener).
 */
public class ChestItemRenderer implements SpecialModelRenderer<ChestItemRenderer.ChestAttributes> {
	private static final Identifier ID = SophisticatedStorage.getRL("chest");
	/**
	 * The chest state submit does not look at the camera (it only uses it for frustum independent drawing), so a single
	 * empty camera state is reused instead of building one per submitted item.
	 */
	private static final CameraRenderState ITEM_CAMERA = new CameraRenderState();
	/** draws the latch towards the viewer, matching how the vanilla chest item is oriented */
	private static final Direction ITEM_FACING = Direction.SOUTH;
	/** a double chest item is shown as both halves of the double model, slightly shrunk so it fits the item slot */
	private static final float DOUBLE_CHEST_SCALE = 0.8F;
	private static final double DOUBLE_CHEST_OFFSET = 0.72D;

	private final ChestRenderer chestRenderer;

	private ChestItemRenderer(EntityModelSet modelSet, SpriteGetter sprites) {
		chestRenderer = new ChestRenderer(modelSet, sprites);
	}

	/** Registers the item model type; called during client initialisation, before any item model is decoded. */
	public static void register() {
		SpecialModelRenderers.ID_MAPPER.put(ID, Unbaked.MAP_CODEC);
	}

	@Nullable
	@Override
	public ChestAttributes extractArgument(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return null;
		}
		int mainColor = -1;
		int accentColor = -1;
		if (stack.getItem() instanceof ITintableBlockItem tintableBlockItem) {
			mainColor = tintableBlockItem.getMainColor(stack).orElse(-1);
			accentColor = tintableBlockItem.getAccentColor(stack).orElse(-1);
		}
		Block block = blockItem.getBlock();
		return new ChestAttributes(block, ChestRenderer.tierName(block),
				WoodStorageBlockItem.getWoodType(stack).orElse(null), mainColor, accentColor,
				StorageBlockItem.showsTier(stack), WoodStorageBlockItem.isPacked(stack), ChestBlockItem.isDoubleChest(stack));
	}

	@Override
	public void submit(@Nullable ChestAttributes attributes, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, boolean hasFoil, int outlineColor) {
		if (attributes == null) {
			return;
		}

		if (attributes.doubleChest()) {
			poseStack.pushPose();
			poseStack.scale(DOUBLE_CHEST_SCALE, DOUBLE_CHEST_SCALE, DOUBLE_CHEST_SCALE);
			poseStack.translate(DOUBLE_CHEST_OFFSET, 0.0D, 0.0D);
			chestRenderer.submit(state(attributes, ChestType.LEFT, lightCoords), poseStack, collector, ITEM_CAMERA);
			poseStack.translate(-1.0D, 0.0D, 0.0D);
			chestRenderer.submit(state(attributes, ChestType.RIGHT, lightCoords), poseStack, collector, ITEM_CAMERA);
			poseStack.popPose();
			return;
		}

		chestRenderer.submit(state(attributes, ChestType.SINGLE, lightCoords), poseStack, collector, ITEM_CAMERA);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		consumer.accept(new Vector3f(0.0F, 0.0F, 0.0F));
		consumer.accept(new Vector3f(1.0F, 1.0F, 1.0F));
	}

	private static ChestRenderer.ChestRenderState state(ChestAttributes attributes, ChestType chestType, int lightCoords) {
		ChestRenderer.ChestRenderState state = new ChestRenderer.ChestRenderState();
		state.lightCoords = lightCoords;
		state.type = chestType;
		state.facing = ITEM_FACING;
		state.open = 0.0F;
		state.woodType = attributes.woodType();
		state.tierName = attributes.tierName();
		state.mainColor = attributes.mainColor();
		state.accentColor = attributes.accentColor();
		state.showTier = attributes.showTier();
		state.packed = attributes.packed();
		return state;
	}

	/** Everything the item stack contributes to the look of the chest, extracted once per item model update. */
	public record ChestAttributes(Block block, String tierName, WoodType woodType, int mainColor, int accentColor,
			boolean showTier, boolean packed, boolean doubleChest) {
	}

	public static final class Unbaked implements SpecialModelRenderer.Unbaked<ChestAttributes> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		private Unbaked() {
		}

		@Override
		public SpecialModelRenderer<ChestAttributes> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new ChestItemRenderer(bakingContext.entityModelSet(), bakingContext.sprites());
		}

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<ChestAttributes>> type() {
			return MAP_CODEC;
		}
	}
}
