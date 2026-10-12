package io.github.saamcosta.buildbro.world;

import io.github.saamcosta.buildbro.tool.ToolException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The only place that writes blocks. Checks the limits and saves each position before changing it,
 * so the whole request can be undone. Server thread only.
 */
public final class WorldEditor {
	/**
	 * Normal neighbour and shape updates (stairs and fences connect), but replacing a container
	 * neither spills its items nor drops the block: undo puts both back, so dropping them would duplicate items.
	 */
	static final int FLAGS = Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SUPPRESS_DROPS;

	private final ServerLevel level;
	private final Transaction transaction;
	private final Limits limits;
	private final BlockPos origin;
	private final AtomicInteger changes;

	WorldEditor(ServerLevel level, Transaction transaction, Limits limits, BlockPos origin, AtomicInteger changes) {
		this.level = level;
		this.transaction = transaction;
		this.limits = limits;
		this.origin = origin;
		this.changes = changes;
	}

	public int blocksLeft() {
		return Math.max(0, limits.maxBlocks() - transaction.size());
	}

	/** Fails before anything is written when a box could not fit in what is left of the budget. */
	public void checkFits(Box box) throws ToolException {
		checkFits(box, box.volume());
	}

	/** Same, for work that touches {@code blocks} positions inside {@code box}. */
	public void checkFits(Box box, long blocks) throws ToolException {
		checkReach(box.minX(), box.minZ());
		checkReach(box.maxX(), box.maxZ());
		if (box.minY() < level.getMinY() || box.maxY() > level.getMaxY()) {
			throw new ToolException("Box " + box + " leaves the build height (" + level.getMinY() + " to " + level.getMaxY() + ")");
		}
		if (blocks > blocksLeft()) {
			throw new ToolException("Box " + box + " has " + blocks + " blocks but only " + blocksLeft()
					+ " are left in this request's limit. Split the work or build smaller.");
		}
	}

	/**
	 * Writes a block, then finishes what the game would do if a player had placed it: panes, bars, fences,
	 * walls and stairs take the shape that fits their neighbours, and two matching chests side by side become
	 * a double chest. Returns true when the block actually changed.
	 */
	public boolean set(BlockPos pos, BlockState state) throws ToolException {
		boolean changed = write(pos, state);
		if (changed) {
			BlockState placed = level.getBlockState(pos);
			if (takesNeighbourShape(placed)) {
				BlockState shaped = Block.updateFromNeighbourShapes(placed, level, pos);
				if (shaped != placed && !shaped.isAir()) {
					level.setBlock(pos, shaped, FLAGS);
				}
			}
			if (placed.getBlock() instanceof ChestBlock) {
				pairChest(pos, placed);
			}
		}
		return changed;
	}

	/**
	 * Only blocks whose shape depends on their neighbours alone. Doors and beds are left out on purpose:
	 * the game would remove a lower door half whose upper half is not placed yet.
	 */
	private static boolean takesNeighbourShape(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock;
	}

	/** A chest of type LEFT joins the chest on its clockwise side, as in the game. */
	private void pairChest(BlockPos pos, BlockState chest) throws ToolException {
		if (chest.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			return;
		}
		Direction facing = chest.getValue(ChestBlock.FACING);
		for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
			BlockPos otherPos = pos.relative(side);
			BlockState other = level.getBlockState(otherPos);
			if (other.getBlock() == chest.getBlock() && other.getValue(ChestBlock.FACING) == facing
					&& other.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
				boolean left = side == facing.getClockWise();
				write(pos, chest.setValue(ChestBlock.TYPE, left ? ChestType.LEFT : ChestType.RIGHT));
				write(otherPos, other.setValue(ChestBlock.TYPE, left ? ChestType.RIGHT : ChestType.LEFT));
				return;
			}
		}
	}

	private boolean write(BlockPos pos, BlockState state) throws ToolException {
		if (level.isOutsideBuildHeight(pos)) {
			throw new ToolException("Position " + pos.toShortString() + " is outside the build height");
		}
		checkReach(pos.getX(), pos.getZ());
		if (level.getBlockState(pos) == state && level.getBlockEntity(pos) == null) {
			return false;
		}
		if (!transaction.contains(pos) && transaction.size() >= limits.maxBlocks()) {
			throw new ToolException("Block limit for this request reached (" + limits.maxBlocks() + ")");
		}
		transaction.remember(level, pos);
		boolean changed = level.setBlock(pos, state, FLAGS);
		if (changed) {
			changes.incrementAndGet();
		}
		return changed;
	}

	private void checkReach(int x, int z) throws ToolException {
		int distance = Math.max(Math.abs(x - origin.getX()), Math.abs(z - origin.getZ()));
		if (distance > limits.radius()) {
			throw new ToolException("Position x=" + x + " z=" + z + " is " + distance
					+ " blocks from where the player asked; the limit is " + limits.radius());
		}
	}
}
