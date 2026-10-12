package io.github.saamcosta.buildbro.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Looks for the mistakes builds keep making: doors nobody can walk through, holes in roofs,
 * and closed spaces dark enough for monsters. Server thread only.
 */
public final class BuildCheck {
	private static final int SAMPLES = 12;

	private BuildCheck() {
	}

	public static JsonObject run(ServerLevel level, Box box) {
		JsonObject report = new JsonObject();
		report.add("doors", doors(level, box));
		report.add("roof_holes", roofHoles(level, box));
		report.add("dark_spots", darkSpots(level, box));
		return report;
	}

	/** Each door needs room to stand on both sides of it. */
	private static JsonObject doors(ServerLevel level, Box box) {
		int checked = 0;
		JsonArray problems = new JsonArray();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
				continue;
			}
			checked++;
			if (!(level.getBlockState(pos.above()).getBlock() instanceof DoorBlock)) {
				problems.add(problem(pos, "the upper half of the door is missing"));
				continue;
			}
			Direction facing = state.getValue(DoorBlock.FACING);
			for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
				BlockPos feet = pos.relative(side);
				if (!Walkability.canStand(level, feet)) {
					problems.add(problem(pos, "blocked on the " + side.getSerializedName() + " side: " + Walkability.obstacle(level, feet)));
				}
			}
		}
		JsonObject out = new JsonObject();
		out.addProperty("checked", checked);
		out.add("problems", limit(problems));
		return out;
	}

	/**
	 * A column whose top block sits two or more below all four neighbours is a hole you can see the sky through
	 * from inside. Chimney openings show up too; judge those yourself.
	 */
	private static JsonObject roofHoles(ServerLevel level, Box box) {
		int sizeX = box.sizeX();
		int sizeZ = box.sizeZ();
		int[][] top = new int[sizeX][sizeZ];
		for (int x = 0; x < sizeX; x++) {
			for (int z = 0; z < sizeZ; z++) {
				top[x][z] = Integer.MIN_VALUE;
				for (int y = box.maxY(); y >= box.minY(); y--) {
					if (!level.getBlockState(new BlockPos(box.minX() + x, y, box.minZ() + z)).isAir()) {
						top[x][z] = y;
						break;
					}
				}
			}
		}
		JsonArray holes = new JsonArray();
		int count = 0;
		for (int x = 1; x < sizeX - 1; x++) {
			for (int z = 1; z < sizeZ - 1; z++) {
				int lowestNeighbour = Math.min(Math.min(top[x - 1][z], top[x + 1][z]), Math.min(top[x][z - 1], top[x][z + 1]));
				if (lowestNeighbour == Integer.MIN_VALUE || top[x][z] > lowestNeighbour - 2) {
					continue;
				}
				count++;
				if (holes.size() < SAMPLES) {
					JsonObject hole = new JsonObject();
					hole.addProperty("x", box.minX() + x);
					hole.addProperty("z", box.minZ() + z);
					hole.addProperty("roof_y_around", lowestNeighbour);
					hole.addProperty("top_y_here", top[x][z] == Integer.MIN_VALUE ? "nothing" : Integer.toString(top[x][z]));
					holes.add(hole);
				}
			}
		}
		JsonObject out = new JsonObject();
		out.addProperty("count", count);
		out.add("samples", holes);
		return out;
	}

	/** Covered floor with no block light at all: monsters spawn there in the dark. */
	private static JsonObject darkSpots(ServerLevel level, Box box) {
		var blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
		var skyLight = level.getLightEngine().getLayerListener(LightLayer.SKY);
		JsonArray samples = new JsonArray();
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
				continue;
			}
			BlockPos below = pos.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				continue;
			}
			if (skyLight.getLightValue(pos) >= 15 || blockLight.getLightValue(pos) > 0) {
				continue;
			}
			count++;
			if (samples.size() < SAMPLES) {
				samples.add(pos.getX() + " " + pos.getY() + " " + pos.getZ());
			}
		}
		JsonObject out = new JsonObject();
		out.addProperty("count", count);
		out.add("samples", samples);
		out.addProperty("note", "Light spreads a moment after blocks change; if you just placed lights, check again.");
		return out;
	}

	private static JsonObject problem(BlockPos door, String text) {
		JsonObject problem = new JsonObject();
		problem.addProperty("door", door.toShortString());
		problem.addProperty("problem", text);
		return problem;
	}

	private static JsonArray limit(JsonArray all) {
		if (all.size() <= SAMPLES * 2) {
			return all;
		}
		JsonArray some = new JsonArray();
		for (int i = 0; i < SAMPLES * 2; i++) {
			some.add(all.get(i));
		}
		some.add("... and " + (all.size() - SAMPLES * 2) + " more");
		return some;
	}
}
