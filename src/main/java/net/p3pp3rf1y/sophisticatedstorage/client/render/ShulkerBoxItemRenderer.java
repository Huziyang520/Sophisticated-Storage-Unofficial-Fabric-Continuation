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
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;
import net.p3pp3rf1y.sophisticatedstorage.block.ITintableBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Special model renderer behind the {@code sophisticatedstorage:shulker_box} item model type, used by all shulker box
 * items ({@code assets/sophisticatedstorage/items/*shulker_box.json}).
 * <p>
 * Like the chest this reuses the block entity renderer ({@link ShulkerBoxRenderer}) instead of the vanilla
 * {@code minecraft:shulker_box} special model, which can only draw a single flat texture and therefore showed almost
 * nothing but the (missing) tier colour for tiered shulker boxes. The item stack state is turned into a
 * {@link ShulkerBoxRenderer.ShulkerRenderState} so the item is drawn exactly like an upward facing placed box.
 */
public class ShulkerBoxItemRenderer implements SpecialModelRenderer<ShulkerBoxItemRenderer.ShulkerBoxAttributes> {
	private static final Identifier ID = SophisticatedStorage.getRL("shulker_box");
	/** the shulker state submit does not look at the camera, so one empty state is reused */
	private static final CameraRenderState ITEM_CAMERA = new CameraRenderState();
	/** a placed shulker box opens upwards; the item shows the closed box in that orientation */
	private static final Direction ITEM_DIRECTION = Direction.UP;

	private final ShulkerBoxRenderer shulkerBoxRenderer;

	private ShulkerBoxItemRenderer(EntityModelSet modelSet, SpriteGetter sprites) {
		shulkerBoxRenderer = new ShulkerBoxRenderer(modelSet, sprites);
	}

	/** Registers the item model type; called during client initialisation, before any item model is decoded. */
	public static void register() {
		SpecialModelRenderers.ID_MAPPER.put(ID, Unbaked.MAP_CODEC);
	}

	@Nullable
	@Override
	public ShulkerBoxAttributes extractArgument(ItemStack stack) {
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
		return new ShulkerBoxAttributes(block, ShulkerBoxRenderer.tierName(block), mainColor, accentColor,
				StorageBlockItem.showsTier(stack));
	}

	@Override
	public void submit(@Nullable ShulkerBoxAttributes attributes, PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
		if (attributes == null) {
			return;
		}
		shulkerBoxRenderer.submit(state(attributes, lightCoords), poseStack, collector, ITEM_CAMERA);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		consumer.accept(new Vector3f(0.0F, 0.0F, 0.0F));
		consumer.accept(new Vector3f(1.0F, 1.0F, 1.0F));
	}

	private static ShulkerBoxRenderer.ShulkerRenderState state(ShulkerBoxAttributes attributes, int lightCoords) {
		ShulkerBoxRenderer.ShulkerRenderState state = new ShulkerBoxRenderer.ShulkerRenderState();
		state.lightCoords = lightCoords;
		state.direction = ITEM_DIRECTION;
		state.progress = 0.0F;
		state.tierName = attributes.tierName();
		state.mainColor = attributes.mainColor();
		state.accentColor = attributes.accentColor();
		state.showTier = attributes.showTier();
		return state;
	}

	/** Everything the item stack contributes to the look of the shulker box, extracted once per item model update. */
	public record ShulkerBoxAttributes(Block block, String tierName, int mainColor, int accentColor, boolean showTier) {
	}

	public static final class Unbaked implements SpecialModelRenderer.Unbaked<ShulkerBoxAttributes> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		private Unbaked() {
		}

		@Override
		public SpecialModelRenderer<ShulkerBoxAttributes> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new ShulkerBoxItemRenderer(bakingContext.entityModelSet(), bakingContext.sprites());
		}

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<ShulkerBoxAttributes>> type() {
			return MAP_CODEC;
		}
	}
}
