package net.p3pp3rf1y.sophisticatedstorage.client.render;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.p3pp3rf1y.sophisticatedstorage.SophisticatedStorage;

import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Item model type for the barrel items ({@code sophisticatedstorage:barrel}).
 * <p>
 * The barrel item reuses the static barrel block model, whose recolourable faces carry tint indexes 1000 (main
 * colour) and 1001 (accent colour) - that is fine for blocks, where the tint list is an addressable sparse list, but
 * an item's {@code tints} array is a plain array that is indexed by the face's tint index and anything out of range
 * renders untinted ({@code ItemFeatureRenderer#getLayerColorSafe} returns -1 for {@code index >= length}). A barrel
 * item definition would therefore need 1002 entries, 1000 of them unused.
 * <p>
 * So this model type takes the two useful tint sources and pads them into a 1002 element list before delegating to
 * the vanilla cuboid wrapper: index 0/1 (a model that remaps its tint indexes to the usual item range) and index
 * 1000/1001 (the current barrel model) both resolve to the main / accent colour, every other index stays untinted.
 */
public final class BarrelItemModel {
	private static final Identifier ID = SophisticatedStorage.getRL("barrel");

	/** tint index of the main colour faces of the barrel model */
	private static final int MAIN_TINT_INDEX = 1000;
	/** tint index of the accent colour faces of the barrel model */
	private static final int ACCENT_TINT_INDEX = 1001;
	private static final int TINT_COUNT = ACCENT_TINT_INDEX + 1;

	private BarrelItemModel() {
	}

	/** Registers the model type; called during client initialisation, before any item model is decoded. */
	public static void register() {
		ItemModels.ID_MAPPER.put(ID, Unbaked.MAP_CODEC);
	}

	public record Unbaked(Identifier model, Optional<Transformation> transformation, List<ItemTintSource> tints)
			implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
				Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
				ItemTintSources.CODEC.listOf().fieldOf("tints").forGetter(Unbaked::tints)
		).apply(instance, Unbaked::new));

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			delegate().resolveDependencies(resolver);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformationMatrix) {
			return delegate().bake(context, transformationMatrix);
		}

		@Override
		public MapCodec<? extends ItemModel.Unbaked> type() {
			return MAP_CODEC;
		}

		private CuboidItemModelWrapper.Unbaked delegate() {
			return new CuboidItemModelWrapper.Unbaked(model, transformation, paddedTints());
		}

		private List<ItemTintSource> paddedTints() {
			List<ItemTintSource> padded = new ArrayList<>(Collections.nCopies(TINT_COUNT, StorageItemTints.NO_TINT));
			setTint(padded, 0, 0);
			setTint(padded, 1, 1);
			setTint(padded, MAIN_TINT_INDEX, 0);
			setTint(padded, ACCENT_TINT_INDEX, 1);
			return padded;
		}

		private void setTint(List<ItemTintSource> padded, int index, int tintIndex) {
			if (tintIndex < tints.size()) {
				padded.set(index, tints.get(tintIndex));
			}
		}
	}
}
