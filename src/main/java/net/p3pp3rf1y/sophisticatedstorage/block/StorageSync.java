package net.p3pp3rf1y.sophisticatedstorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Pushes a storage block entity's synchronised data (colours, wood type, slot counts, double-chest link) to the
 * clients that track it.
 * <p>
 * {@code WorldHelper.notifyBlockUpdate} only marks the block for the next chunk broadcast
 * ({@code ChunkHolder#blockChanged}) and the data packet goes out with that broadcast at the end of the tick; it is
 * skipped entirely for a chunk that is not ticking yet. That is enough for "eventually", but not enough for the two
 * cases that matter here: the first rendered frame after placing a dyed barrel, and the menu that is opened from the
 * client's copy of the block entity. When the copy is one broadcast behind, the barrel paints the wood colour of its
 * block state and a freshly joined double chest still reports the slot count from before the join, so its menu only
 * covers the half that the items were moved out of.
 * <p>
 * Sending the packet to the tracking players right away removes that window; it is idempotent (the client simply
 * loads the fresh tag) and it is what vanilla does for its own block entities.
 */
public final class StorageSync {
	private StorageSync() {
	}

	public static void syncToClients(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (!(level instanceof ServerLevel serverLevel) || blockEntity.isRemoved()) {
			return;
		}

		BlockPos pos = blockEntity.getBlockPos();
		ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(blockEntity);
		for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(pos), false)) {
			player.connection.send(packet);
		}
	}
}
