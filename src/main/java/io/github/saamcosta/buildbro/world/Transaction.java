package io.github.saamcosta.buildbro.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one player request changed, with what each position held before it was first touched.
 * Server thread only.
 */
public final class Transaction {
	private final ResourceKey<Level> dimension;
	private final Map<BlockPos, Snapshot> originals = new LinkedHashMap<>();

	public Transaction(ResourceKey<Level> dimension) {
		this.dimension = dimension;
	}

	public int size() {
		return originals.size();
	}

	public boolean isEmpty() {
		return originals.isEmpty();
	}

	public boolean contains(BlockPos pos) {
		return originals.containsKey(pos);
	}

	/** Saves the block and its block entity data, unless this position was already saved. */
	void remember(ServerLevel level, BlockPos pos) {
		BlockPos key = pos.immutable();
		if (originals.containsKey(key)) {
			return;
		}
		BlockEntity blockEntity = level.getBlockEntity(key);
		CompoundTag data = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
		originals.put(key, new Snapshot(level.getBlockState(key), data));
	}

	/** Puts every position back as it was, newest change first. Returns how many blocks were restored. */
	public int revert(MinecraftServer server) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return 0;
		}
		List<Map.Entry<BlockPos, Snapshot>> entries = new ArrayList<>(originals.entrySet());
		for (int i = entries.size() - 1; i >= 0; i--) {
			BlockPos pos = entries.get(i).getKey();
			Snapshot snapshot = entries.get(i).getValue();
			level.setBlock(pos, snapshot.state(), WorldEditor.FLAGS);
			if (snapshot.data() != null) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity != null) {
					blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), snapshot.data()));
					blockEntity.setChanged();
					level.sendBlockUpdated(pos, snapshot.state(), snapshot.state(), WorldEditor.FLAGS);
				}
			}
		}
		int restored = originals.size();
		originals.clear();
		return restored;
	}

	private record Snapshot(BlockState state, CompoundTag data) {
	}
}
