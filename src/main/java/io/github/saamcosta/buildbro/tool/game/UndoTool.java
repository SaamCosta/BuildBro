package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Args;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BuildSession;

final class UndoTool implements Tool<BuildSession> {
	private static final int MAX_STEPS = 10;

	@Override
	public String name() {
		return "undo";
	}

	@Override
	public String description() {
		return "Reverts whole builds, newest first, putting back blocks and container contents. "
				+ "What you built so far in this request counts as the newest build. "
				+ "Use it when the player asks to undo, or to start over after a mistake.";
	}

	@Override
	public JsonObject inputSchema() {
		return Schema.object()
				.integer("steps", "How many builds to revert (default 1, at most 10)", false)
				.build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		int steps = Args.integer(args, "steps", 1);
		if (steps < 1 || steps > MAX_STEPS) {
			throw new ToolException("steps must be between 1 and " + MAX_STEPS);
		}
		int restored = session.onServer(game -> session.undo(steps));
		JsonObject out = new JsonObject();
		out.addProperty("blocks_restored", restored);
		return out;
	}
}
