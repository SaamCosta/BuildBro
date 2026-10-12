package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.ToolRegistry;
import io.github.saamcosta.buildbro.world.BuildSession;
import io.github.saamcosta.buildbro.world.Box;
import net.minecraft.core.BlockPos;

/** The tools the model gets in the game. */
public final class GameTools {
	private GameTools() {
	}

	public static ToolRegistry<BuildSession> all() {
		return new ToolRegistry<BuildSession>()
				.register(new PlayerInfoTool())
				.register(new SurveyTerrainTool())
				.register(new FindBlockTool())
				.register(new InspectAreaTool())
				.register(new FillTool())
				.register(new PlaceBlocksTool())
				.register(new RoofTool())
				.register(new SectionTool())
				.register(new CheckBuildTool())
				.register(new UndoTool());
	}

	static JsonObject position(BlockPos pos) {
		JsonObject json = new JsonObject();
		json.addProperty("x", pos.getX());
		json.addProperty("y", pos.getY());
		json.addProperty("z", pos.getZ());
		return json;
	}

	static JsonObject box(Box box) {
		JsonObject json = new JsonObject();
		json.add("from", position(new BlockPos(box.minX(), box.minY(), box.minZ())));
		json.add("to", position(new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
		return json;
	}
}
