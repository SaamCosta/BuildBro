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
import io.github.saamcosta.buildbro.world.WorldEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PlaceBlocksTool implements Tool<BuildSession> {
	private static final int MAX_BLOCKS = 4096;

	@Override
	public String name() {
		return "place_blocks";
	}

	@Override
	public String description() {
		return "Places individual blocks, each with its own state: doors, stairs, torches, lanterns, slabs, details. "
				+ "Up to 4096 per call. States use the /setblock syntax, for example "
				+ "oak_stairs[facing=east,half=bottom] or oak_door[facing=north,half=lower]; doors and beds need both halves.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject item = Schema.object()
				.integer("x", "x", true)
				.integer("y", "y", true)
				.integer("z", "z", true)
				.string("block", "Block with optional state", true)
				.build();
		JsonObject list = Schema.type("array", "Blocks to place, in order");
		list.add("items", item);
		list.addProperty("maxItems", MAX_BLOCKS);
		return Schema.object().property("blocks", list, true).build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		JsonArray items = Args.array(args, "blocks");
		if (items.size() > MAX_BLOCKS) {
			throw new ToolException("At most " + MAX_BLOCKS + " blocks per call; got " + items.size());
		}
		Map<String, BlockState> parsed = new HashMap<>();
		List<Placement> placements = new ArrayList<>(items.size());
		for (int i = 0; i < items.size(); i++) {
			if (!items.get(i).isJsonObject()) {
				throw new ToolException("blocks[" + i + "] must be an object with x, y, z and block");
			}
			JsonObject item = items.get(i).getAsJsonObject();
			String text = Args.string(item, "block");
			BlockState state = parsed.get(text);
			if (state == null) {
				state = BlockStates.parse(text);
				parsed.put(text, state);
			}
			placements.add(new Placement(new BlockPos(Args.integer(item, "x"), Args.integer(item, "y"), Args.integer(item, "z")), state));
		}

		return session.onServer(game -> {
			WorldEditor editor = game.editor();
			int changed = 0;
			for (int i = 0; i < placements.size(); i++) {
				Placement placement = placements.get(i);
				try {
					if (editor.set(placement.pos(), placement.state())) {
						changed++;
					}
				} catch (ToolException e) {
					throw new ToolException("Placed " + i + " of " + placements.size() + " blocks, then stopped: " + e.getMessage());
				}
			}
			JsonObject out = new JsonObject();
			out.addProperty("changed", changed);
			out.addProperty("blocks_left", editor.blocksLeft());
			return out;
		});
	}

	private record Placement(BlockPos pos, BlockState state) {
	}
}
