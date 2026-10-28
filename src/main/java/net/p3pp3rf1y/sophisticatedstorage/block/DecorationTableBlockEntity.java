package net.p3pp3rf1y.sophisticatedstorage.block;

import net.p3pp3rf1y.sophisticatedstorage.util.Lazy;
import net.p3pp3rf1y.sophisticatedcore.platform.item.ItemStackHandler;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedcore.util.WorldHelper;
import net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedstorage.item.BarrelBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.item.PaintbrushItem;
import net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem;
import net.p3pp3rf1y.sophisticatedstorage.util.DecorationHelper;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DecorationTableBlockEntity extends BlockEntity {
	public static final int TOP_INNER_TRIM_SLOT = 0;
	public static final int TOP_TRIM_SLOT = 1;
	public static final int SIDE_TRIM_SLOT = 2;
	public static final int BOTTOM_TRIM_SLOT = 3;
	public static final int TOP_CORE_SLOT = 4;
	public static final int SIDE_CORE_SLOT = 5;
	public static final int BOTTOM_CORE_SLOT = 6;
	public static final int RED_DYE_SLOT = 0;
	public static final int GREEN_DYE_SLOT = 1;
	public static final int BLUE_DYE_SLOT = 2;
	// Need to make this lazy as don't have a priority system for mod loading, therefore SophisticatedStorageInMotion can load before SophisticatedStorage is loaded
	public static final Lazy<Set<Item>> STORAGES_WIHOUT_TOP_INNER_TRIM = Lazy.of(() -> Set.of(ModBlocks.BARREL_ITEM.get(), ModBlocks.COPPER_BARREL_ITEM.get(), ModBlocks.IRON_BARREL_ITEM.get(), ModBlocks.GOLD_BARREL_ITEM.get(), ModBlocks.DIAMOND_BARREL_ITEM.get(), ModBlocks.NETHERITE_BARREL_ITEM.get(),
			ModBlocks.LIMITED_BARREL_1_ITEM.get(), ModBlocks.LIMITED_COPPER_BARREL_1_ITEM.get(), ModBlocks.LIMITED_IRON_BARREL_1_ITEM.get(), ModBlocks.LIMITED_GOLD_BARREL_1_ITEM.get(), ModBlocks.LIMITED_DIAMOND_BARREL_1_ITEM.get(), ModBlocks.LIMITED_NETHERITE_BARREL_1_ITEM.get()));

	private static final Map<Predicate<ItemStack>, IItemDecorator> ITEM_DECORATORS = new LinkedHashMap<>();

	public static void registerItemDecorator(Predicate<ItemStack> itemMatcher, IItemDecorator itemDecorator) {
		ITEM_DECORATORS.put(itemMatcher, itemDecorator);
	}

	private final Map<Identifier, Integer> remainingParts = new HashMap<>();

	private final ItemStackHandler decorativeBlocks = new ItemStackHandler(7) {
		@Override
		public void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			updateResultAndSetChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemVariant resource, int count) {
			return resource.getItem() instanceof BlockItem blockItem && !(resource.getItem() instanceof StorageBlockItem) && Block.isShapeFullBlock(blockItem.getBlock().defaultBlockState().getShape(level, BlockPos.ZERO));
		}
	};

	private final ItemStackHandler dyes = new ItemStackHandler(3) {
		@Override
		public void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			updateResultAndSetChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemVariant resource, int count) {
			return switch (slot) {
				case RED_DYE_SLOT -> resource.toStack().is(ConventionalItemTags.RED_DYES);
				case GREEN_DYE_SLOT -> resource.toStack().is(ConventionalItemTags.GREEN_DYES);
				case BLUE_DYE_SLOT -> resource.toStack().is(ConventionalItemTags.BLUE_DYES);
				default -> false;
			};
		}
	};

	private ItemStack result = ItemStack.EMPTY;

	private final Map<Integer, Boolean> slotMaterialInheritance = new HashMap<>();
	private int accentColor = -1;
	private int mainColor = -1;

	private final Set<Identifier> missingDyes = new HashSet<>();

	public void updateResultAndSetChanged() {
		updateResult();
		setChanged();
		// 结果只存在方块实体里，客户端要点/看它就必须收到一次方块更新包：
		// 之前这里只有 setChanged()（只标脏存档），客户端那份 result 永远是空的，
		// 玩家看到结果槽空着、点也没反应 —— “装饰台只能开 UI”。
		if (level != null && !level.isClientSide()) {
			WorldHelper.notifyBlockUpdate(this);
		}
	}

	private final ItemStackHandler storageBlock = new ItemStackHandler(1) {
		@Override
		public void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			updateResultAndSetChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemVariant resource, int count) {
			return ITEM_DECORATORS.keySet().stream().anyMatch(predicate -> predicate.test(resource.toStack(count)));
		}
	};

	private void updateResult() {
		missingDyes.clear();
		result = ItemStack.EMPTY;

		ItemStack input = storageBlock.getStackInSlot(0);
		if (input.isEmpty()) {
			return;
		}

		getItemDecorator(input).ifPresent(itemDecorator -> {
			TintDecorationResult decorationResult = decorateItem(itemDecorator, input);
			result = decorationResult.result();
			missingDyes.addAll(calculateMissingDyes(decorationResult.requiredDyeParts()));
		});
	}

	private static Optional<IItemDecorator> getItemDecorator(ItemStack input) {
		return ITEM_DECORATORS.entrySet().stream().filter(e -> e.getKey().test(input)).findFirst().map(Map.Entry::getValue);
	}

	private TintDecorationResult decorateItem(IItemDecorator itemDecorator, ItemStack input) {
		if (itemDecorator.supportsMaterials(input)) {
			Map<BarrelMaterial, Identifier> materialsToApply = getMaterialsToApply(itemDecorator.supportsTopInnerTrim(input));
			if (!materialsToApply.isEmpty()) {
				return new TintDecorationResult(itemDecorator.decorateWithMaterials(input, materialsToApply), Collections.emptyMap());
			}
		}
		if (itemDecorator.supportsTints(input)) {
			return itemDecorator.decorateWithTints(input, mainColor, accentColor);
		}
		return TintDecorationResult.EMPTY;
	}

	public boolean hasMaterials() {
		return !InventoryHelper.isEmpty(decorativeBlocks);
	}

	public List<ItemStack> getDecoratedPreviewStacks() {
		ItemStack input = storageBlock.getStackInSlot(0);
		return getItemDecorator(input).map(itemDecorator -> {
			List<ItemStack> previewStacks = new ArrayList<>();
			itemDecorator.getPreviewStackInputs(input, hasMaterials()).forEach(stack -> {
				getItemDecorator(stack).ifPresent(inputItemDecorator -> {
					TintDecorationResult decorationResult = decorateItem(inputItemDecorator, stack);
					if (!decorationResult.result().isEmpty()) {
						previewStacks.add(decorationResult.result());
					}
				});
			});
			return previewStacks;
		}).orElse(Collections.emptyList());
	}

	public static boolean allMaterialsMatch(Map<BarrelMaterial, Identifier> newMaterials, Map<BarrelMaterial, Identifier> currentMaterials) {
		if (newMaterials.size() != currentMaterials.size()) {
			return false;
		}

		for (Map.Entry<BarrelMaterial, Identifier> entry : newMaterials.entrySet()) {
			if (!entry.getValue().equals(currentMaterials.get(entry.getKey()))) {
				return false;
			}
		}

		return true;
	}

	private Set<Identifier> calculateMissingDyes(Map<TagKey<Item>, Integer> requiredDyeParts) {
		Set<Identifier> missingDyes = new HashSet<>();
		if (!dyes.getStackInSlot(RED_DYE_SLOT).isEmpty() && !dyes.getStackInSlot(GREEN_DYE_SLOT).isEmpty() && !dyes.getStackInSlot(BLUE_DYE_SLOT).isEmpty()) {
			return missingDyes;
		}

		Map<Identifier, Integer> partsNeeded =
				requiredDyeParts.entrySet().stream().map(entry -> Map.entry(entry.getKey().location(), entry.getValue())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		for (Map.Entry<Identifier, Integer> entry : partsNeeded.entrySet()) {
			if (entry.getKey().equals(ConventionalItemTags.RED_DYES.location()) && dyes.getStackInSlot(RED_DYE_SLOT).isEmpty()) {
				missingDyes.add(entry.getKey());
			} else if (entry.getKey().equals(ConventionalItemTags.GREEN_DYES.location()) && dyes.getStackInSlot(GREEN_DYE_SLOT).isEmpty()) {
				missingDyes.add(entry.getKey());
			} else if (entry.getKey().equals(ConventionalItemTags.BLUE_DYES.location()) && dyes.getStackInSlot(BLUE_DYE_SLOT).isEmpty()) {
				missingDyes.add(entry.getKey());
			}
		}
		return missingDyes;
	}

	private static Map<TagKey<Item>, Integer> calculateRequiredDyes(int mainColorToSet, int accentColorToSet, int currentMainColor, int currentAccentColor) {
		return DecorationHelper.getDyePartsNeeded(mainColorToSet, accentColorToSet, currentMainColor, currentAccentColor);
	}

	public Set<Identifier> getMissingDyes() {
		return missingDyes;
	}

	private void setMaterialsFromDecorativeBlocks(Map<BarrelMaterial, Identifier> materials, boolean supportsInnerTrim) {
		Identifier topInnerTrimMaterialLocation = setMaterialFromBlock(TOP_INNER_TRIM_SLOT, null, materials, BarrelMaterial.TOP_INNER_TRIM, supportsInnerTrim);
		Identifier topTrimMaterialLocation = setMaterialFromBlock(TOP_TRIM_SLOT, topInnerTrimMaterialLocation, materials, BarrelMaterial.TOP_TRIM, true);
		Identifier sideTrimMaterialLocation = setMaterialFromBlock(SIDE_TRIM_SLOT, topTrimMaterialLocation, materials, BarrelMaterial.SIDE_TRIM, true);
		setMaterialFromBlock(BOTTOM_TRIM_SLOT, sideTrimMaterialLocation, materials, BarrelMaterial.BOTTOM_TRIM, true);
		Identifier topMaterialLocation = setMaterialFromBlock(TOP_CORE_SLOT, topTrimMaterialLocation, materials, BarrelMaterial.TOP, true);
		Identifier sideMaterialLocation = setMaterialFromBlock(SIDE_CORE_SLOT, topMaterialLocation, materials, BarrelMaterial.SIDE, true);
		setMaterialFromBlock(BOTTOM_CORE_SLOT, sideMaterialLocation, materials, BarrelMaterial.BOTTOM, true);
	}

	@Nullable
	private Identifier setMaterialFromBlock(int slotIndex, @Nullable Identifier defaultMaterialLocation, Map<BarrelMaterial, Identifier> materials, BarrelMaterial material, boolean addToMaterials) {
		ItemStack decorativeBlock = decorativeBlocks.getStackInSlot(slotIndex);
		Identifier materialLocation = DecorationHelper.getMaterialLocation(decorativeBlock).orElse(isSlotMaterialInherited(slotIndex) ? defaultMaterialLocation : null);
		if (materialLocation != null) {
			if (addToMaterials) {
				materials.put(material, materialLocation);
			}
			return materialLocation;
		}
		return null;
	}

	public DecorationTableBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlocks.DECORATION_TABLE_BLOCK_ENTITY_TYPE.get(), pos, blockState);
	}

	public ItemStackHandler getDecorativeBlocks() {
		return decorativeBlocks;
	}

	public ItemStackHandler getDyes() {
		return dyes;
	}

	public ItemStackHandler getStorageBlock() {
		return storageBlock;
	}

	public ItemStack getResult() {
		return result;
	}

	public ItemStack extractResult(int count) {
		ItemStack result = getResult();
		if (result.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack extracted = result.copy();
		extracted.setCount(count);
		if (count >= result.getCount()) {
			this.result = ItemStack.EMPTY;
		} else {
			result.shrink(count);
		}
		setChanged();
		return extracted;
	}

	public boolean isSlotMaterialInherited(int slot) {
		return slotMaterialInheritance.getOrDefault(slot, true);
	}

	public ItemStack getInheritedItem(int childSlot) {
		while (isSlotMaterialInherited(childSlot)) {
			int parentSlot = getSlotInheritedFrom(childSlot);
			if (parentSlot == -1) {
				return ItemStack.EMPTY;
			} else if (!decorativeBlocks.getStackInSlot(parentSlot).isEmpty()) {
				return decorativeBlocks.getStackInSlot(parentSlot);
			}
			childSlot = parentSlot;
		}
		return ItemStack.EMPTY;
	}

	public int getSlotInheritedFrom(int slot) {
		return switch (slot) {
			case TOP_TRIM_SLOT -> TOP_INNER_TRIM_SLOT;
			case SIDE_TRIM_SLOT -> TOP_TRIM_SLOT;
			case BOTTOM_TRIM_SLOT -> SIDE_TRIM_SLOT;
			case TOP_CORE_SLOT -> TOP_TRIM_SLOT;
			case SIDE_CORE_SLOT -> TOP_CORE_SLOT;
			case BOTTOM_CORE_SLOT -> SIDE_CORE_SLOT;
			default -> -1;
		};
	}

	public void setSlotMaterialInheritance(int slot, boolean value) {
		if (value) {
			slotMaterialInheritance.remove(slot);
		} else {
			slotMaterialInheritance.put(slot, false);
		}
		updateResultAndSetChanged();
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private HolderLookup.Provider registries() {
		return getLevel() == null ? net.minecraft.core.RegistryAccess.EMPTY : getLevel().registryAccess();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveData(tag, registries);
		return tag;
	}

	@Override
	protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		CompoundTag tag = input.read("sophisticatedstorage_data", CompoundTag.CODEC).orElseGet(CompoundTag::new);
		super.loadAdditional(input);
		decorativeBlocks.deserializeNBT(registries(), tag.getCompoundOrEmpty("decorativeBlocks"));
		dyes.deserializeNBT(registries(), tag.getCompoundOrEmpty("dyes"));
		storageBlock.deserializeNBT(registries(), tag.getCompoundOrEmpty("storageBlock"));
		result = ItemStack.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.getCompoundOrEmpty("result")).result().orElse(ItemStack.EMPTY);
		slotMaterialInheritance.clear();
		ListTag inheritance = tag.getListOrEmpty("slotMaterialInheritance");
		for (int i = 0; i < inheritance.size(); i++) {
			CompoundTag slotTag = inheritance.getCompoundOrEmpty(i);
			slotMaterialInheritance.put(slotTag.getIntOr("slot", 0), slotTag.getBooleanOr("value", false));
		}
		remainingParts.clear();
		ListTag remainingPartsTag = tag.getListOrEmpty("remainingParts");
		for (int i = 0; i < remainingPartsTag.size(); i++) {
			CompoundTag partTag = remainingPartsTag.getCompoundOrEmpty(i);
			Identifier key = Identifier.tryParse(partTag.getStringOr("key", ""));
			if (key == null) {
				continue;
			}
			remainingParts.put(key, partTag.getIntOr("value", 0));
		}

		mainColor = tag.getIntOr("mainColor", 0);
		accentColor = tag.getIntOr("accentColor", 0);
	}

	@Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		CompoundTag tag = new CompoundTag();
		super.saveAdditional(output);
		saveData(tag, registries());
			output.store("sophisticatedstorage_data", CompoundTag.CODEC, tag);
	}

	private void saveData(CompoundTag tag, HolderLookup.Provider registries) {
		tag.put("decorativeBlocks", decorativeBlocks.serializeNBT(registries));
		tag.put("dyes", dyes.serializeNBT(registries));
		tag.put("storageBlock", storageBlock.serializeNBT(registries));
		if (!result.isEmpty()) {
			ItemStack.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, result).result().ifPresent(saved -> tag.put("result", saved));
		}
		ListTag inheritance = new ListTag();
		slotMaterialInheritance.forEach((slot, value) -> {
			CompoundTag slotTag = new CompoundTag();
			slotTag.putInt("slot", slot);
			slotTag.putBoolean("value", value);
			inheritance.add(slotTag);
		});
		tag.put("slotMaterialInheritance", inheritance);
		ListTag remainingPartsTag = new ListTag();
		remainingParts.forEach((key, value) -> {
			CompoundTag partTag = new CompoundTag();
			partTag.putString("key", key.toString());
			partTag.putInt("value", value);
			remainingPartsTag.add(partTag);
		});
		tag.put("remainingParts", remainingPartsTag);

		tag.putInt("mainColor", mainColor);
		tag.putInt("accentColor", accentColor);
	}

	public void consumeIngredientsOnCraft() {
		ItemStack input = storageBlock.getStackInSlot(0);

		getItemDecorator(input).ifPresent(itemDecorator -> {
			if (!itemDecorator.consumesIngredientsOnCraft()) {
				return;
			}

			boolean emptyDecorativeBlocks = InventoryHelper.isEmpty(decorativeBlocks);
			if ((emptyDecorativeBlocks || !itemDecorator.supportsMaterials(input)) && itemDecorator.supportsTints(input)) {
				DecorationHelper.consumeDyes(mainColor, accentColor, this.remainingParts, List.of(dyes), StorageBlockItem.getMainColorFromComponentHolder(input).orElse(-1), StorageBlockItem.getAccentColorFromComponentHolder(input).orElse(-1), false);
			} else if (!emptyDecorativeBlocks && itemDecorator.supportsMaterials(input)) {
				Map<BarrelMaterial, Identifier> originalMaterials = BarrelBlockItem.getUncompactedMaterials(input);
				/// See comment at {@link STORAGES_WIHOUT_TOP_INNER_TRIM}
				DecorationHelper.consumeMaterials(this.remainingParts, List.of(decorativeBlocks), originalMaterials, getMaterialsToApply(!STORAGES_WIHOUT_TOP_INNER_TRIM.get().contains(input.getItem())), false);
			}

			setChanged();
			WorldHelper.notifyBlockUpdate(this);
		});

	}

	public Map<Identifier, Integer> getPartsNeeded() {
		Map<Identifier, Integer> partsNeeded = new HashMap<>();
		ItemStack storageStack = storageBlock.getStackInSlot(0);
		if (InventoryHelper.isEmpty(decorativeBlocks) || !(storageStack.getItem() instanceof BarrelBlockItem)) {
			DecorationHelper.getDyePartsNeeded(mainColor, accentColor, StorageBlockItem.getMainColorFromComponentHolder(storageStack).orElse(-1), StorageBlockItem.getAccentColorFromComponentHolder(storageStack).orElse(-1))
					.forEach((tag, parts) -> partsNeeded.put(tag.location(), parts));
		} else {
			/// See comment at {@link STORAGES_WIHOUT_TOP_INNER_TRIM}
			partsNeeded.putAll(DecorationHelper.getMaterialPartsNeeded(BarrelBlockItem.getUncompactedMaterials(storageStack), getMaterialsToApply(!STORAGES_WIHOUT_TOP_INNER_TRIM.get().contains(storageStack.getItem()))));
		}

		return partsNeeded;
	}

	private Map<BarrelMaterial, Identifier> getMaterialsToApply(boolean supportsInnerTrim) {
		Map<BarrelMaterial, Identifier> materialsToApply = new EnumMap<>(BarrelMaterial.class);
		setMaterialsFromDecorativeBlocks(materialsToApply, supportsInnerTrim);
		return materialsToApply;
	}

	public int getMainColor() {
		return mainColor;
	}

	public void setMainColor(int mainColor) {
		this.mainColor = mainColor;
		updateResultAndSetChanged();
	}

	public int getAccentColor() {
		return accentColor;
	}

	public void setAccentColor(int accentColor) {
		this.accentColor = accentColor;
		updateResultAndSetChanged();
	}

	public Map<Identifier, Integer> getPartsStored() {
		return remainingParts;
	}

	public void dropContents() {
		if (level == null || level.isClientSide()) {
			return;
		}
		InventoryHelper.dropItems(decorativeBlocks, level, worldPosition);
		InventoryHelper.dropItems(dyes, level, worldPosition);
		InventoryHelper.dropItems(storageBlock, level, worldPosition);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		dropContents();
	}

	public record TintDecorationResult(ItemStack result, Map<TagKey<Item>, Integer> requiredDyeParts) {
		public static final TintDecorationResult EMPTY = new TintDecorationResult(ItemStack.EMPTY, Collections.emptyMap());
	}

	public interface IItemDecorator {
		default boolean consumesIngredientsOnCraft() {
			return true;
		}

		boolean supportsMaterials(ItemStack input);

		boolean supportsTints(ItemStack input);

		boolean supportsTopInnerTrim(ItemStack input);

		ItemStack decorateWithMaterials(ItemStack input, Map<BarrelMaterial, Identifier> materialsToApply);

		TintDecorationResult decorateWithTints(ItemStack input, int mainColorToSet, int accentColorToSet);

		default List<ItemStack> getPreviewStackInputs(ItemStack input, boolean materialsInCraftingSlots) {
			return List.of(input);
		}
	}

	private static boolean isTintedStorage(ItemStack storage) {
		return StorageBlockItem.getMainColorFromComponentHolder(storage).isPresent() || StorageBlockItem.getAccentColorFromComponentHolder(storage).isPresent();
	}

	private static boolean colorsTransparentOrSameAs(ItemStack storage, int mainColorToSet, int accentColorToSet) {
		return (mainColorToSet == -1 || mainColorToSet == StorageBlockItem.getMainColorFromComponentHolder(storage).orElse(-1)) && (accentColorToSet == -1 || accentColorToSet == StorageBlockItem.getAccentColorFromComponentHolder(storage).orElse(-1));
	}

	public static final IItemDecorator STORAGE_DECORATOR = new IItemDecorator() {
		@Override
		public boolean supportsMaterials(ItemStack input) {
			return input.getItem() instanceof BarrelBlockItem && !isTintedStorage(input);
		}

		@Override
		public boolean supportsTints(ItemStack input) {
			return !(input.getItem() instanceof BarrelBlockItem) || BarrelBlockItem.getMaterials(input).isEmpty();
		}

		@Override
		public boolean supportsTopInnerTrim(ItemStack input) {
			/// See comment at {@link STORAGES_WIHOUT_TOP_INNER_TRIM}
			return !STORAGES_WIHOUT_TOP_INNER_TRIM.get().contains(input.getItem());
		}

		@Override
		public ItemStack decorateWithMaterials(ItemStack input, Map<BarrelMaterial, Identifier> materialsToApply) {
			if (materialsToApply.isEmpty()) {
				return ItemStack.EMPTY;
			}

			Map<BarrelMaterial, Identifier> materials = new EnumMap<>(BarrelMaterial.class);
			materials.putAll(BarrelBlockItem.getMaterials(input));
			BarrelBlockItem.uncompactMaterials(materials);

			materials.putAll(materialsToApply);

			BarrelBlockItem.compactMaterials(materials);

			if (allMaterialsMatch(materials, BarrelBlockItem.getMaterials(input))) {
				return ItemStack.EMPTY;
			}

			ItemStack result = input.copyWithCount(1);

			BarrelBlockItem.removeCoveredTints(result, materials);
			BarrelBlockItem.setMaterials(result, materials);

			return result;
		}

		@Override
		public TintDecorationResult decorateWithTints(ItemStack input, int mainColorToSet, int accentColorToSet) {
			if (colorsTransparentOrSameAs(input, mainColorToSet, accentColorToSet)) {
				return TintDecorationResult.EMPTY;
			}

			ItemStack result = input.copyWithCount(1);

			if (result.getItem() instanceof BlockItem blockItem && blockItem instanceof ITintableBlockItem tintableBlockItem) {
				if (mainColorToSet != -1) {
					tintableBlockItem.setMainColor(result, mainColorToSet);
				}
				if (accentColorToSet != -1) {
					tintableBlockItem.setAccentColor(result, accentColorToSet);
				}
			}

			return new TintDecorationResult(result, calculateRequiredDyes(mainColorToSet, accentColorToSet, StorageBlockItem.getMainColorFromComponentHolder(input).orElse(-1), StorageBlockItem.getAccentColorFromComponentHolder(input).orElse(-1)));

		}
	};

	static {
		ITEM_DECORATORS.put(stack -> stack.getItem() instanceof StorageBlockItem, STORAGE_DECORATOR);
		ITEM_DECORATORS.put(stack -> stack.getItem() instanceof PaintbrushItem, new IItemDecorator() {
			@Override
			public boolean consumesIngredientsOnCraft() {
				return false;
			}

			@Override
			public boolean supportsMaterials(ItemStack input) {
				return true;
			}

			@Override
			public boolean supportsTints(ItemStack input) {
				return true;
			}

			@Override
			public boolean supportsTopInnerTrim(ItemStack input) {
				return true;
			}

			@Override
			public ItemStack decorateWithMaterials(ItemStack input, Map<BarrelMaterial, Identifier> materialsToApply) {
				BarrelBlockItem.compactMaterials(materialsToApply);

				if (allMaterialsMatch(materialsToApply, BarrelBlockItem.getMaterials(input))) {
					return ItemStack.EMPTY;
				}

				ItemStack result = input.copyWithCount(1);
				PaintbrushItem.setBarrelMaterials(result, materialsToApply);
				return result;
			}

			@Override
			public TintDecorationResult decorateWithTints(ItemStack input, int mainColorToSet, int accentColorToSet) {
				if ((mainColorToSet == -1 && accentColorToSet == -1) || (mainColorToSet == PaintbrushItem.getMainColor(input) && accentColorToSet == PaintbrushItem.getAccentColor(input))) {
					return TintDecorationResult.EMPTY;
				}

				ItemStack result = input.copyWithCount(1);
				if (mainColorToSet != -1) {
					PaintbrushItem.setMainColor(result, mainColorToSet);
				}
				if (accentColorToSet != -1) {
					PaintbrushItem.setAccentColor(result, accentColorToSet);
				}
				return new TintDecorationResult(result, Collections.emptyMap());

			}

			@Override
			public List<ItemStack> getPreviewStackInputs(ItemStack input, boolean materialsInCraftingSlots) {
				if (materialsInCraftingSlots) {
					return List.of(new ItemStack(ModBlocks.LIMITED_BARREL_3_ITEM.get()));
				}

				return List.of(new ItemStack(ModBlocks.LIMITED_BARREL_3_ITEM.get()), new ItemStack(ModBlocks.CHEST_ITEM.get()), new ItemStack(ModBlocks.SHULKER_BOX_ITEM.get()));
			}
		});
		ITEM_DECORATORS.put(stack -> stack.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags.DYEABLE), new IItemDecorator() {
			@Override
			public boolean supportsMaterials(ItemStack input) {
				return false;
			}

			@Override
			public boolean supportsTints(ItemStack input) {
				return true;
			}

			@Override
			public boolean supportsTopInnerTrim(ItemStack input) {
				return false;
			}

			@Override
			public ItemStack decorateWithMaterials(ItemStack input, Map<BarrelMaterial, Identifier> materialsToApply) {
				return ItemStack.EMPTY;
			}

			@Override
			public TintDecorationResult decorateWithTints(ItemStack input, int mainColorToSet, int accentColorToSet) {
				int currentColor = DyedItemColor.getOrDefault(input, -1);
				if (mainColorToSet == -1 || (currentColor == mainColorToSet)) {
					return TintDecorationResult.EMPTY;
				}

				ItemStack result = input.copyWithCount(1);
				result.set(DataComponents.DYED_COLOR, new DyedItemColor(ARGB.color(0, ARGB.red(mainColorToSet), ARGB.green(mainColorToSet), ARGB.blue(mainColorToSet))));

				return new TintDecorationResult(result, DecorationHelper.getDyePartsNeeded(mainColorToSet, -1, currentColor, -1, 24, 0));
			}
		});
	}
}
