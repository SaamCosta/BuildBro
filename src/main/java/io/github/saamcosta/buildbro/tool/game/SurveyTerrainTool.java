package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Args;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BlockStates;
import io.github.saamcosta.buildbro.world.BuildSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

final class SurveyTerrainTool implements Tool<BuildSession> {
	private static final int MAX_SIDE = 256;
	private static final int MAX_GRID = 48;

	@Override
	public String name() {
		return "survey_terrain";
	}

	@Override
	public String description() {
		return "Height map of a rectangle seen from above (corners included, at most 256 blocks per side). "
				+ "Returns one row per z with the y of the top solid or liquid block of each column ('?' where the area is not loaded), "
				+ "sampled every 'step' blocks for large areas, plus the most common surface blocks. "
				+ "Use it to pick flat ground and to set foundations at the right height.";
	}

	@Override
	public JsonObject inputSchema() {
		return Schema.object()
				.integer("x1", "First corner x", true)
				.integer("z1", "First corner z", true)
				.integer("x2", "Opposite corner x", true)
				.integer("z2", "Opposite corner z", true)
				.integer("step", "Sample every n blocks (default: chosen so the grid is at most 48 by 48)", false)
				.build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		int minX = Math.min(Args.integer(args, "x1"), Args.integer(args, "x2"));
		int maxX = Math.max(Args.integer(args, "x1"), Args.integer(args, "x2"));
		int minZ = Math.min(Args.integer(args, "z1"), Args.integer(args, "z2"));
		int maxZ = Math.max(Args.integer(args, "z1"), Args.integer(args, "z2"));
		int side = Math.max(maxX - minX, maxZ - minZ) + 1;
		if (side > MAX_SIDE) {
			throw new ToolException("The area is " + side + " blocks wide; survey at most " + MAX_SIDE + " per side");
		}
		int step = Math.max(1, Args.integer(args, "step", Math.ceilDiv(side, MAX_GRID)));

		return session.onServer(game -> {
			ServerLevel level = game.level();
			Map<String, Integer> surface = new HashMap<>();
			JsonArray rows = new JsonArray();
			int lowest = Integer.MAX_VALUE;
			int highest = Integer.MIN_VALUE;
			for (int z = minZ; z <= maxZ; z += step) {
				StringBuilder row = new StringBuilder();
				for (int x = minX; x <= maxX; x += step) {
					if (!row.isEmpty()) {
						row.append(' ');
					}
					if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
						row.append('?');
						continue;
					}
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
					row.append(y);
					lowest = Math.min(lowest, y);
					highest = Math.max(highest, y);
					surface.merge(BlockStates.id(level.getBlockState(new BlockPos(x, y, z))), 1, Integer::sum);
				}
				rows.add(row.toString());
			}

			JsonObject out = new JsonObject();
			out.addProperty("min_x", minX);
			out.addProperty("min_z", minZ);
			out.addProperty("step", step);
			out.addProperty("note", "rows go from min_z towards +z, columns from min_x towards +x");
			out.add("heights", rows);
			if (lowest <= highest) {
				out.addProperty("lowest_y", lowest);
				out.addProperty("highest_y", highest);
			}
			out.add("surface_blocks", Counts.top(surface, 10));
			return out;
		});
	}
}
