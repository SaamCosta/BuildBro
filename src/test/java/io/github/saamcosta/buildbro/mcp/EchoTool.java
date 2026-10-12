package io.github.saamcosta.buildbro.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.saamcosta.buildbro.tool.Args;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;

/** A tool that needs no game: answers with its context plus the given word. */
public final class EchoTool implements Tool<String> {
	@Override
	public String name() {
		return "echo";
	}

	@Override
	public String description() {
		return "Repeats a word";
	}

	@Override
	public JsonObject inputSchema() {
		return Schema.object().string("word", "Word to repeat", true).build();
	}

	@Override
	public JsonElement call(JsonObject args, String context) throws ToolException {
		String word = Args.string(args, "word");
		if (word.equals("fail")) {
			throw new ToolException("asked to fail");
		}
		return new JsonPrimitive(context + ":" + word);
	}
}
