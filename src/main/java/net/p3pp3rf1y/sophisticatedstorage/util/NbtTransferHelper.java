package net.p3pp3rf1y.sophisticatedstorage.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * 26.3：BlockEntity 的读写改成 ValueInput / ValueOutput，
 * 这里提供把整块 CompoundTag 一次性喂给 block entity / 从 block entity 导出整块 NBT 的便捷方法。
 */
public final class NbtTransferHelper {
	private NbtTransferHelper() {
	}

	public static void loadAdditional(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries) {
		blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
	}

	public static CompoundTag saveAdditional(BlockEntity blockEntity, HolderLookup.Provider registries) {
		return blockEntity.saveCustomOnly(registries);
	}
}
