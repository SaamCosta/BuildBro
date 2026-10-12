package io.github.saamcosta.buildbro.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Can a player stand or walk here? Rough rules good enough to find blocked doors. */
final class Walkability {
	/** Carpets, pressure plates and snow layers are low enough to walk over. */
	private static final double LOW = 0.2;

	private Walkability() {
	}

	/** Nothing in the way at body height. */
	static boolean isOpen(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		var shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= LOW;
	}

	/** Something solid to stand on. */
	static boolean isFloor(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** A block a player walks up without jumping. */
	static boolean isStep(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof StairBlock
				|| state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
	}

	/**
	 * A player can stand at {@code feet}: two open blocks with a floor below, or a step with room above it,
	 * or one block down onto a floor.
	 */
	static boolean canStand(ServerLevel level, BlockPos feet) {
		if (isOpen(level, feet) && isOpen(level, feet.above())) {
			return isFloor(level, feet.below())
					|| isOpen(level, feet.below()) && isFloor(level, feet.below(2));
		}
		return isStep(level, feet) && isOpen(level, feet.above()) && isOpen(level, feet.above(2));
	}

	/** What blocks the way at {@code feet}, for the report. */
	static String obstacle(ServerLevel level, BlockPos feet) {
		for (BlockPos pos : new BlockPos[] {feet, feet.above()}) {
			if (!isOpen(level, pos)) {
				return BlockStates.id(level.getBlockState(pos)) + " at " + pos.toShortString();
			}
		}
		if (!isFloor(level, feet.below()) && !isFloor(level, feet.below(2))) {
			return "no floor below " + feet.toShortString();
		}
		return "no room at " + feet.toShortString();
	}
}
