package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Args;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.Box;

/** The two-corner arguments shared by the box tools. */
final class BoxArgs {
	private BoxArgs() {
	}

	static Schema schema(Schema schema) {
		return schema
				.integer("x1", "First corner x", true)
				.integer("y1", "First corner y", true)
				.integer("z1", "First corner z", true)
				.integer("x2", "Opposite corner x", true)
				.integer("y2", "Opposite corner y", true)
				.integer("z2", "Opposite corner z", true);
	}

	static Box read(JsonObject args) throws ToolException {
		return Box.of(Args.integer(args, "x1"), Args.integer(args, "y1"), Args.integer(args, "z1"),
				Args.integer(args, "x2"), Args.integer(args, "y2"), Args.integer(args, "z2"));
	}
}
