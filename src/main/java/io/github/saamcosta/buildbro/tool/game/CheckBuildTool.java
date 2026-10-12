package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BuildCheck;
import io.github.saamcosta.buildbro.world.BuildSession;
import io.github.saamcosta.buildbro.world.Box;

final class CheckBuildTool implements Tool<BuildSession> {
	private static final long MAX_VOLUME = 64L * 64 * 64;

	@Override
	public String name() {
		return "check_build";
	}

	@Override
	public String description() {
		return "Inspects a finished build for the usual mistakes and lists where they are: doors that cannot be walked "
				+ "through (blocked, missing upper half, no floor), holes in the roof, and covered floor with no light where "
				+ "monsters would spawn. Give a box around the whole build with a margin of 2 blocks, from below the floor to "
				+ "above the roof (at most 64 by 64 by 64). Always call it before you finish, fix what it reports, and call it again.";
	}

	@Override
	public JsonObject inputSchema() {
		return BoxArgs.schema(Schema.object()).build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		Box box = BoxArgs.read(args);
		if (box.sizeX() > 64 || box.sizeY() > 64 || box.sizeZ() > 64 || box.volume() > MAX_VOLUME) {
			throw new ToolException("Check at most 64 by 64 by 64 at a time; split large builds");
		}
		return session.onServer(game -> {
			JsonObject report = BuildCheck.run(game.level(), box);
			report.add("box", GameTools.box(box));
			return report;
		});
	}
}
