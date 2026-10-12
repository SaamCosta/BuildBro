package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BlockStates;
import io.github.saamcosta.buildbro.world.BuildSession;
import io.github.saamcosta.buildbro.world.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

final class InspectAreaTool implements Tool<BuildSession> {
	private static final long MAX_VOLUME = 32 * 32 * 32;

	@Override
	public String name() {
		return "inspect_area";
	}

	@Override
	public String description() {
		return "Counts the blocks inside a box (corners included, at most 32768 blocks): which blocks are there and how many, "
				+ "how many are air and how many hold data such as chests. "
				+ "Use it to check what is already built or what an existing building is made of.";
	}

	@Override
	public JsonObject inputSchema() {
		return BoxArgs.schema(Schema.object()).build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		Box box = BoxArgs.read(args);
		if (box.volume() > MAX_VOLUME) {
			throw new ToolException("The box has " + box.volume() + " blocks; inspect at most " + MAX_VOLUME + " at a time");
		}
		return session.onServer(game -> {
			ServerLevel level = game.level();
			Map<String, Integer> counts = new HashMap<>();
			int air = 0;
			int withData = 0;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					air++;
					continue;
				}
				if (state.hasBlockEntity()) {
					withData++;
				}
				counts.merge(BlockStates.id(state), 1, Integer::sum);
			}
			JsonObject out = GameTools.box(box);
			out.addProperty("volume", box.volume());
			out.addProperty("air", air);
			out.addProperty("with_data", withData);
			out.add("blocks", Counts.top(counts, 40));
			return out;
		});
	}
}
